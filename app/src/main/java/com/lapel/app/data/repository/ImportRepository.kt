package com.lapel.app.data.repository

import androidx.room.withTransaction
import com.lapel.app.data.local.LapelDatabase
import com.lapel.app.data.local.entity.CustomerEntity
import com.lapel.app.data.local.entity.DesignEntity
import com.lapel.app.data.local.entity.OrderCostEntity
import com.lapel.app.data.local.entity.OrderEntity
import com.lapel.app.data.local.entity.OrderItemEntity
import com.lapel.app.data.local.entity.PaymentEntity
import com.lapel.app.data.local.entity.StatusChangeEntity
import com.lapel.app.data.local.entity.StockBatchCostEntity
import com.lapel.app.data.local.entity.StockBatchEntity
import com.lapel.app.data.local.entity.StockSaleEntity
import com.lapel.domain.importer.ImportedPayment
import com.lapel.domain.importer.SpreadsheetImport
import com.lapel.domain.model.ChangeSource
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.PaymentMethod
import com.lapel.domain.model.PinType
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class ImportCounts(val customers: Int, val orders: Int, val stockBatches: Int, val stockSales: Int, val payments: Int)

/** Writes a parsed spreadsheet into the database in one transaction (all or nothing). */
@Singleton
class ImportRepository @Inject constructor(
    private val db: LapelDatabase,
    private val clock: Clock,
) {
    suspend fun hasOrders(): Boolean = db.orderDao().count() > 0

    suspend fun save(data: SpreadsheetImport): ImportCounts = db.withTransaction {
        val now = clock.instant()
        val today = LocalDate.now(clock)
        val orderDao = db.orderDao()
        val stockDao = db.stockDao()
        val paymentDao = db.paymentDao()
        var paymentCount = 0

        val customerIds = data.customers.associate { c ->
            c.key to db.customerDao().insert(
                CustomerEntity(
                    name = c.name,
                    phone = c.phone,
                    email = null,
                    organization = c.organization,
                    paymentTermsDays = c.paymentTermsDays,
                    notes = null,
                    customerSince = c.firstSeen ?: today,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

        fun payment(p: ImportedPayment, fallbackDate: LocalDate, orderId: Long?, saleId: Long?) = PaymentEntity(
            orderId = orderId,
            stockSaleId = saleId,
            amountAgorot = p.amount.agorot,
            method = PaymentMethod.OTHER,
            milestone = p.milestone,
            receivedOn = p.date ?: fallbackDate,
            reference = IMPORT_NOTE,
            createdAt = now,
        )

        data.orders.forEach { o ->
            val delivered = o.status == FulfillmentStatus.DELIVERED || o.status == FulfillmentStatus.COMPLETED
            val deliveredAt = if (delivered) o.orderDate.atStartOfDay(clock.zone).toInstant() else null
            val orderId = orderDao.insertOrder(
                OrderEntity(
                    customerId = customerIds.getValue(o.customerKey),
                    orderNumber = o.orderNumber,
                    title = o.title,
                    orderDate = o.orderDate,
                    dueDate = null,
                    fulfillmentStatus = o.status,
                    discountAgorot = o.discount.agorot,
                    deliveryMethod = o.deliveryMethod,
                    alibabaOrderNumber = null,
                    alibabaOrderedOn = o.orderDate,
                    supplierName = o.supplier,
                    qualityOk = o.qualityOk,
                    deliveredAt = deliveredAt,
                    completedAt = if (o.status == FulfillmentStatus.COMPLETED) deliveredAt else null,
                    notes = o.notes,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            orderDao.insertItems(
                o.items.map {
                    OrderItemEntity(
                        orderId = orderId, designId = null, designName = it.designName, pinType = PinType.OTHER,
                        sizeMm = null, plating = null, quantityOrdered = it.quantityOrdered,
                        quantitySold = it.quantitySold, unitPriceAgorot = it.unitPrice.agorot, artworkUri = null,
                    )
                },
            )
            orderDao.insertCosts(o.costs.map { OrderCostEntity(orderId = orderId, type = it.type, amountAgorot = it.amount.agorot, note = it.note) })
            o.payments.forEach { paymentDao.insert(payment(it, o.orderDate, orderId, null)); paymentCount++ }
            orderDao.insertStatusChange(StatusChangeEntity(orderId = orderId, fromStatus = null, toStatus = o.status, source = ChangeSource.SYSTEM, at = now))
        }

        var saleCount = 0
        data.stockBatches.forEach { b ->
            val designId = stockDao.insertDesign(DesignEntity(name = b.designName, artworkUri = null, supplierName = b.supplier, moldPaidOnOrderId = null, notes = null))
            val batchId = stockDao.insertBatch(
                StockBatchEntity(designId = designId, quantityReceived = b.quantityReceived, purchasedOn = b.purchasedOn, notes = "$IMPORT_NOTE (${b.orderNumber})"),
            )
            stockDao.insertBatchCosts(b.costs.map { StockBatchCostEntity(batchId = batchId, type = it.type, amountAgorot = it.amount.agorot, note = it.note) })
            b.sales.forEach { s ->
                val saleId = stockDao.insertSale(
                    StockSaleEntity(
                        batchId = batchId,
                        customerId = customerIds.getValue(s.customerKey),
                        soldOn = s.date ?: b.purchasedOn,
                        quantity = s.quantity,
                        unitPriceAgorot = s.unitPrice.agorot,
                        shippingChargedAgorot = s.shippingCharged.agorot,
                        shippingCostAgorot = 0,
                        discountAgorot = s.discount.agorot,
                        sent = s.sent,
                        arrived = s.arrived,
                        postTrackingNumber = s.trackingNumber,
                        notes = s.notes,
                    ),
                )
                saleCount++
                s.payments.forEach { paymentDao.insert(payment(it, s.date ?: b.purchasedOn, null, saleId)); paymentCount++ }
            }
        }

        ImportCounts(customerIds.size, data.orders.size, data.stockBatches.size, saleCount, paymentCount)
    }

    companion object {
        const val IMPORT_NOTE = "יובא מהגיליון"
    }
}
