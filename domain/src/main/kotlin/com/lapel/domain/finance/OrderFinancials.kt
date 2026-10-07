package com.lapel.domain.finance

import com.lapel.domain.model.CostType
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.Money
import com.lapel.domain.model.PaymentStatus
import com.lapel.domain.model.sum

data class OrderLine(
    val quantityOrdered: Int,
    val quantitySold: Int,
    val unitPrice: Money,
)

data class CostLine(val type: CostType, val amount: Money)

data class FinancialInput(
    val lines: List<OrderLine>,
    val costs: List<CostLine>,
    val payments: List<Money>,
    val discount: Money = Money.ZERO,
    val depositPercent: Int = 50,
    val status: FulfillmentStatus,
)

/** Pre-summed order amounts; [sellingTotal] already has the discount taken off. */
data class OrderTotals(
    val sellingTotal: Money,
    val costTotal: Money,
    val paidTotal: Money,
    val alibabaPayment: Money,
    val pinsOrdered: Int,
    val depositPercent: Int,
    val status: FulfillmentStatus,
)

/**
 * Every money figure shown for an order. The single place where profit, debt and
 * payment status are calculated, so screens, dashboard and export always agree.
 */
data class OrderFinancials(
    val sellingTotal: Money,
    val costTotal: Money,
    val paidTotal: Money,
    val netProfit: Money,
    /** Net profit as % of selling price; null when nothing is sold. */
    val marginPercent: Double?,
    val depositDue: Money,
    val depositOutstanding: Money,
    /** What the customer still owes in total. */
    val outstanding: Money,
    /** What the customer should already have paid by the 50/50 rule. */
    val dueNow: Money,
    /** Received minus spent; negative means the owner is out of pocket. */
    val cashPosition: Money,
    /** Alibaba payment ÷ pins ordered (spares included). */
    val costPerPin: Money?,
    val paymentStatus: PaymentStatus,
) {
    val isFullyPaid: Boolean
        get() = paymentStatus == PaymentStatus.FULLY_PAID || paymentStatus == PaymentStatus.OVERPAID

    companion object {
        fun calculate(input: FinancialInput): OrderFinancials = from(
            OrderTotals(
                sellingTotal = input.lines.map { it.unitPrice * it.quantitySold }.sum() - input.discount,
                costTotal = input.costs.map { it.amount }.sum(),
                paidTotal = input.payments.sum(),
                alibabaPayment = input.costs.filter { it.type == CostType.ALIBABA_PAYMENT }.map { it.amount }.sum(),
                pinsOrdered = input.lines.sumOf { it.quantityOrdered },
                depositPercent = input.depositPercent,
                status = input.status,
            ),
        )

        /** Same calculation from totals already summed (e.g. by a database query). */
        fun from(totals: OrderTotals): OrderFinancials {
            val selling = totals.sellingTotal
            val costs = totals.costTotal
            val paid = totals.paidTotal
            val cancelled = totals.status == FulfillmentStatus.CANCELLED

            val depositDue = selling.percent(totals.depositPercent)
            val depositOutstanding = if (cancelled) Money.ZERO else (depositDue - paid).coerceAtLeastZero()
            val outstanding = if (cancelled) Money.ZERO else (selling - paid).coerceAtLeastZero()

            // A cancelled order earns only what was kept minus what was already spent.
            val netProfit = if (cancelled) paid - costs else selling - costs
            val marginBase = if (cancelled) paid else selling
            val margin = if (marginBase.isPositive) netProfit.agorot * 100.0 / marginBase.agorot else null

            val dueNow = when (totals.status) {
                FulfillmentStatus.DRAFT, FulfillmentStatus.CANCELLED -> Money.ZERO
                FulfillmentStatus.ORDERED_FROM_ALIBABA, FulfillmentStatus.SHIPPED -> depositOutstanding
                FulfillmentStatus.DELIVERED, FulfillmentStatus.COMPLETED -> outstanding
            }

            return OrderFinancials(
                sellingTotal = selling,
                costTotal = costs,
                paidTotal = paid,
                netProfit = netProfit,
                marginPercent = margin,
                depositDue = depositDue,
                depositOutstanding = depositOutstanding,
                outstanding = outstanding,
                dueNow = dueNow,
                cashPosition = paid - costs,
                costPerPin = totals.alibabaPayment.dividedBy(totals.pinsOrdered),
                paymentStatus = paymentStatus(selling, paid),
            )
        }

        fun paymentStatus(selling: Money, paid: Money): PaymentStatus = when {
            paid > selling -> PaymentStatus.OVERPAID
            paid == selling -> PaymentStatus.FULLY_PAID
            paid.isPositive -> PaymentStatus.DEPOSIT_PAID
            else -> PaymentStatus.UNPAID
        }
    }
}
