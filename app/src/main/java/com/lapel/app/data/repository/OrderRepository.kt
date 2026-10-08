package com.lapel.app.data.repository

import androidx.room.withTransaction
import com.lapel.app.data.local.LapelDatabase
import com.lapel.app.data.local.dao.OrderSummaryRow
import com.lapel.app.data.local.entity.OrderCostEntity
import com.lapel.app.data.local.entity.OrderEntity
import com.lapel.app.data.local.entity.OrderItemEntity
import com.lapel.app.data.local.entity.PaymentEntity
import com.lapel.app.data.local.entity.StatusChangeEntity
import com.lapel.domain.model.ChangeSource
import com.lapel.domain.model.CostType
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.Money
import com.lapel.domain.status.OrderStatusReconciler
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepository @Inject constructor(
    private val db: LapelDatabase,
    private val clock: Clock,
) {
    private val orders = db.orderDao()
    private val payments = db.paymentDao()
    private val shipments = db.shipmentDao()

    fun observeSummaries(): Flow<List<OrderSummaryRow>> = orders.observeSummaries()

    fun observeSummariesForCustomer(customerId: Long) = orders.observeSummariesForCustomer(customerId)

    fun observeOrder(orderId: Long) = orders.observeOrder(orderId)
    fun observeItems(orderId: Long) = orders.observeItems(orderId)
    fun observeCosts(orderId: Long) = orders.observeCosts(orderId)
    fun observePayments(orderId: Long) = payments.observeForOrder(orderId)
    fun observeShipments(orderId: Long) = shipments.observeForOrder(orderId)
    fun observeStatusChanges(orderId: Long) = orders.observeStatusChanges(orderId)

    /** Suggests the next plain order number ("154" after "153"). */
    suspend fun nextOrderNumber(): String = ((orders.maxNumericOrderNumber() ?: 0L) + 1).toString().padStart(3, '0')

    /**
     * Saves a new order with its designs and costs. A ₪8 bank fee is added automatically
     * unless the caller already included a bank fee line.
     */
    suspend fun createOrder(
        order: OrderEntity,
        items: List<OrderItemEntity>,
        costs: List<OrderCostEntity>,
        defaultBankFee: Money = DEFAULT_BANK_FEE,
    ): Long = db.withTransaction {
        val now = clock.instant()
        val id = orders.insertOrder(order.copy(id = 0, createdAt = now, updatedAt = now))
        orders.insertItems(items.map { it.copy(id = 0, orderId = id) })

        val withFee = if (costs.none { it.type == CostType.BANK_FEE } && defaultBankFee.isPositive) {
            costs + OrderCostEntity(orderId = id, type = CostType.BANK_FEE, amountAgorot = defaultBankFee.agorot, note = null)
        } else {
            costs
        }
        orders.insertCosts(withFee.map { it.copy(id = 0, orderId = id) })
        orders.insertStatusChange(StatusChangeEntity(orderId = id, fromStatus = null, toStatus = order.fulfillmentStatus, source = ChangeSource.MANUAL, at = now))
        id
    }

    /** Saves edits to an order's details and replaces its designs. Costs and payments are edited separately. */
    suspend fun updateOrder(order: OrderEntity, items: List<OrderItemEntity>, customs: Long? = null) = db.withTransaction {
        val existing = orders.getOrder(order.id) ?: return@withTransaction
        orders.updateOrder(
            order.copy(
                fulfillmentStatus = existing.fulfillmentStatus,
                deliveredAt = existing.deliveredAt,
                completedAt = existing.completedAt,
                createdAt = existing.createdAt,
                updatedAt = clock.instant(),
            ),
        )
        orders.deleteItemsForOrder(order.id)
        orders.insertItems(items.map { it.copy(id = 0, orderId = order.id) })
        if (customs != null) setCustoms(order.id, customs)
        reconcile(order.id)
    }

    suspend fun addCost(cost: OrderCostEntity) = db.withTransaction {
        orders.insertCosts(listOf(cost.copy(id = 0)))
    }

    suspend fun deleteCost(cost: OrderCostEntity) = orders.deleteCost(cost)

    /** Replaces the order's customs lines with one line of [agorot] (none when 0). */
    private suspend fun setCustoms(orderId: Long, agorot: Long) {
        orders.deleteCostsOfType(orderId, CostType.CUSTOMS)
        if (agorot > 0) {
            orders.insertCosts(listOf(OrderCostEntity(orderId = orderId, type = CostType.CUSTOMS, amountAgorot = agorot, note = null)))
        }
    }

    /** Manual status change from the order screen (e.g. "Ordered from Alibaba", "Delivered" for pickup). */
    suspend fun setStatus(orderId: Long, status: FulfillmentStatus) = db.withTransaction {
        val order = orders.getOrder(orderId) ?: return@withTransaction
        if (order.fulfillmentStatus == status) return@withTransaction
        val now = clock.instant()
        orders.updateOrder(order.withStatus(status, now))
        orders.insertStatusChange(StatusChangeEntity(orderId = orderId, fromStatus = order.fulfillmentStatus, toStatus = status, source = ChangeSource.MANUAL, at = now))
        reconcile(orderId)
    }

    /** Records a payment; a delivered order that is now fully paid completes automatically. */
    suspend fun recordPayment(payment: PaymentEntity): Long = db.withTransaction {
        val id = payments.insert(payment.copy(id = 0, createdAt = clock.instant()))
        payment.orderId?.let { reconcile(it) }
        id
    }

    suspend fun deletePayment(payment: PaymentEntity) = payments.delete(payment)

    /** Applies [OrderStatusReconciler] from the order's shipments and payments. Call inside a transaction. */
    suspend fun reconcile(orderId: Long) {
        val order = orders.getOrder(orderId) ?: return
        val summary = orders.getSummary(orderId) ?: return
        val transitions = OrderStatusReconciler.reconcile(
            current = order.fulfillmentStatus,
            shipments = shipments.getForOrder(orderId).map { it.status },
            fullyPaid = summary.financials().isFullyPaid,
        )
        if (transitions.isEmpty()) return
        val now = clock.instant()
        var updated = order
        transitions.forEach { t ->
            updated = updated.withStatus(t.to, now)
            orders.insertStatusChange(StatusChangeEntity(orderId = orderId, fromStatus = t.from, toStatus = t.to, source = t.source, at = now))
        }
        orders.updateOrder(updated)
    }

    /** Marks whether the shipping address was passed to the manufacturer. */
    suspend fun setAddressSent(orderId: Long, sent: Boolean) {
        val order = orders.getOrder(orderId) ?: return
        orders.updateOrder(order.copy(addressSentToSupplier = sent, updatedAt = clock.instant()))
    }

    private fun OrderEntity.withStatus(status: FulfillmentStatus, now: Instant): OrderEntity {
        // Moving back (a manual correction) clears dates that no longer apply.
        val delivered = status == FulfillmentStatus.DELIVERED || status == FulfillmentStatus.COMPLETED
        return copy(
            fulfillmentStatus = status,
            deliveredAt = when {
                !delivered -> null
                deliveredAt == null -> now
                else -> deliveredAt
            },
            completedAt = if (status == FulfillmentStatus.COMPLETED) completedAt ?: now else null,
            alibabaOrderedOn = if (status != FulfillmentStatus.DRAFT && status != FulfillmentStatus.CANCELLED && alibabaOrderedOn == null) {
                now.atZone(clock.zone).toLocalDate()
            } else {
                alibabaOrderedOn
            },
            updatedAt = now,
        )
    }

    companion object {
        val DEFAULT_BANK_FEE = Money.shekels(8)
    }
}
