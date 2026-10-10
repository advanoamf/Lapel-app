package com.lapel.domain.finance

import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.Money
import com.lapel.domain.model.sum
import java.time.LocalDate

/** One order as the period summary sees it. */
data class PeriodOrder(
    val orderDate: LocalDate,
    val status: FulfillmentStatus,
    val financials: OrderFinancials,
)

/** Income, expenses and profit of the orders placed in a date range. */
data class PeriodSummary(
    val orderCount: Int,
    /** Selling price of the orders; for a cancelled order only what the customer paid and we kept. */
    val income: Money,
    val expenses: Money,
    val profit: Money,
    val received: Money,
    val toCollect: Money,
) {
    val marginPercent: Double?
        get() = if (income.isPositive) profit.agorot * 100.0 / income.agorot else null

    companion object {
        /**
         * Orders dated [from]..[to] (both inclusive). Drafts are left out: they were not placed
         * yet and have no real money in them.
         */
        fun of(orders: List<PeriodOrder>, from: LocalDate, to: LocalDate): PeriodSummary {
            val inRange = orders.filter {
                it.status != FulfillmentStatus.DRAFT && !it.orderDate.isBefore(from) && !it.orderDate.isAfter(to)
            }
            val f = inRange.map { it.financials }
            return PeriodSummary(
                orderCount = inRange.size,
                income = inRange.map { if (it.status == FulfillmentStatus.CANCELLED) it.financials.paidTotal else it.financials.sellingTotal }.sum(),
                expenses = f.map { it.costTotal }.sum(),
                profit = f.map { it.netProfit }.sum(),
                received = f.map { it.paidTotal }.sum(),
                toCollect = f.map { it.outstanding }.sum(),
            )
        }
    }
}
