package com.lapel.app.data.local.dao

import com.lapel.domain.finance.OrderFinancials
import com.lapel.domain.finance.OrderTotals
import com.lapel.domain.model.DeliveryMethod
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.Money
import com.lapel.domain.reminder.OrderReminderSnapshot
import java.time.Instant
import java.time.LocalDate

/** One row per order with money already summed in SQL. Read by lists and the dashboard. */
data class OrderSummaryRow(
    val orderId: Long,
    val orderNumber: String,
    val title: String,
    val orderDate: LocalDate,
    val fulfillmentStatus: FulfillmentStatus,
    val deliveryMethod: DeliveryMethod,
    val depositPercent: Int,
    val alibabaOrderedOn: LocalDate?,
    val deliveredAt: Instant?,
    val createdAt: Instant,
    val hasAddress: Boolean,
    val addressSentToSupplier: Boolean,
    val customerId: Long,
    val customerName: String,
    val customerOrganization: String?,
    val customerPhone: String?,
    val paymentTermsDays: Int,
    val sellingTotal: Long,
    val costTotal: Long,
    val paidTotal: Long,
    val alibabaPayment: Long,
    val pinsOrdered: Int,
    val activeShipments: Int,
) {
    fun financials(): OrderFinancials = OrderFinancials.from(
        OrderTotals(
            sellingTotal = Money(sellingTotal),
            costTotal = Money(costTotal),
            paidTotal = Money(paidTotal),
            alibabaPayment = Money(alibabaPayment),
            pinsOrdered = pinsOrdered,
            depositPercent = depositPercent,
            status = fulfillmentStatus,
        ),
    )

    fun reminderSnapshot(f: OrderFinancials = financials()) = OrderReminderSnapshot(
        orderId = orderId,
        status = fulfillmentStatus,
        depositOutstanding = f.depositOutstanding,
        outstanding = f.outstanding,
        createdAt = createdAt,
        orderedFromAlibabaOn = alibabaOrderedOn,
        deliveredAt = deliveredAt,
        paymentTermsDays = paymentTermsDays,
        orderDate = orderDate,
        deliveryMethod = deliveryMethod,
        hasAddress = hasAddress,
        addressSentToSupplier = addressSentToSupplier,
    )
}
