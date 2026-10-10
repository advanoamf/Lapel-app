package com.lapel.domain.finance

import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PeriodSummaryTest {

    private fun order(day: Int, status: FulfillmentStatus, selling: Long, costs: Long, paid: Long) = PeriodOrder(
        orderDate = LocalDate.of(2026, 3, day),
        status = status,
        financials = OrderFinancials.from(
            OrderTotals(
                sellingTotal = Money.shekels(selling),
                costTotal = Money.shekels(costs),
                paidTotal = Money.shekels(paid),
                alibabaPayment = Money.ZERO,
                pinsOrdered = 0,
                depositPercent = 50,
                status = status,
            ),
        ),
    )

    @Test fun `sums placed orders in the range and leaves out drafts`() {
        val orders = listOf(
            order(1, FulfillmentStatus.COMPLETED, selling = 3_000, costs = 1_358, paid = 3_000),
            order(15, FulfillmentStatus.ORDERED_FROM_ALIBABA, selling = 1_000, costs = 600, paid = 500),
            order(20, FulfillmentStatus.CANCELLED, selling = 800, costs = 300, paid = 400),
            order(21, FulfillmentStatus.DRAFT, selling = 5_000, costs = 0, paid = 0),
            order(28, FulfillmentStatus.DELIVERED, selling = 2_000, costs = 900, paid = 1_000), // after the range
        )
        val s = PeriodSummary.of(orders, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 27))

        assertEquals(3, s.orderCount)
        assertEquals(Money.shekels(3_000 + 1_000 + 400), s.income) // cancelled: only what was kept
        assertEquals(Money.shekels(1_358 + 600 + 300), s.expenses)
        assertEquals(s.income - s.expenses, s.profit)
        assertEquals(Money.shekels(3_000 + 500 + 400), s.received)
        assertEquals(Money.shekels(500), s.toCollect)
    }

    @Test fun `empty range`() {
        val s = PeriodSummary.of(emptyList(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))
        assertEquals(0, s.orderCount)
        assertEquals(Money.ZERO, s.profit)
        assertEquals(null, s.marginPercent)
    }
}
