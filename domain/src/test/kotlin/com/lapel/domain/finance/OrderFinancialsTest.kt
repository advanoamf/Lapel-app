package com.lapel.domain.finance

import com.lapel.domain.model.CostType
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.Money
import com.lapel.domain.model.PaymentStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OrderFinancialsTest {

    private fun ils(shekels: Long) = Money.shekels(shekels)

    /** The worked example from docs/architecture/05-profit-calculation.md. */
    private val example = FinancialInput(
        lines = listOf(OrderLine(quantityOrdered = 200, quantitySold = 200, unitPrice = ils(15))),
        costs = listOf(
            CostLine(CostType.ALIBABA_PAYMENT, ils(1_350)),
            CostLine(CostType.OTHER, ils(220)),
            CostLine(CostType.BANK_FEE, ils(30)),
        ),
        payments = listOf(ils(1_500)),
        status = FulfillmentStatus.SHIPPED,
    )

    @Test fun `worked example while shipped`() {
        val f = OrderFinancials.calculate(example)
        assertEquals(ils(3_000), f.sellingTotal)
        assertEquals(ils(1_600), f.costTotal)
        assertEquals(ils(1_400), f.netProfit)
        assertEquals(46.67, f.marginPercent!!, 0.01)
        assertEquals(ils(1_500), f.depositDue)
        assertEquals(Money.ZERO, f.depositOutstanding)
        assertEquals(ils(1_500), f.outstanding)
        assertEquals(Money.ZERO, f.dueNow)
        assertEquals(ils(-100), f.cashPosition)
        assertEquals(Money(675), f.costPerPin) // 1350 / 200 = ₪6.75
        assertEquals(PaymentStatus.DEPOSIT_PAID, f.paymentStatus)
    }

    @Test fun `balance becomes due on delivery`() {
        val f = OrderFinancials.calculate(example.copy(status = FulfillmentStatus.DELIVERED))
        assertEquals(ils(1_500), f.dueNow)
    }

    @Test fun `deposit is due once ordered from Alibaba`() {
        val f = OrderFinancials.calculate(example.copy(payments = emptyList(), status = FulfillmentStatus.ORDERED_FROM_ALIBABA))
        assertEquals(ils(1_500), f.dueNow)
        assertEquals(PaymentStatus.UNPAID, f.paymentStatus)
    }

    @Test fun `nothing is due for a draft`() {
        val f = OrderFinancials.calculate(example.copy(payments = emptyList(), status = FulfillmentStatus.DRAFT))
        assertEquals(Money.ZERO, f.dueNow)
        assertEquals(ils(3_000), f.outstanding)
    }

    @Test fun `profit counts sold pins, cost per pin counts ordered pins`() {
        val f = OrderFinancials.calculate(
            example.copy(lines = listOf(OrderLine(quantityOrdered = 73, quantitySold = 70, unitPrice = ils(9)))),
        )
        assertEquals(ils(630), f.sellingTotal)
        assertEquals(Money(1_849), f.costPerPin) // 1350 / 73 = 18.493 → ₪18.49
    }

    @Test fun `discount reduces selling total`() {
        val f = OrderFinancials.calculate(example.copy(discount = ils(90)))
        assertEquals(ils(2_910), f.sellingTotal)
        assertEquals(ils(1_310), f.netProfit)
    }

    @Test fun `fully paid and overpaid`() {
        assertEquals(PaymentStatus.FULLY_PAID, OrderFinancials.paymentStatus(ils(100), ils(100)))
        val over = OrderFinancials.calculate(example.copy(payments = listOf(ils(3_100))))
        assertEquals(PaymentStatus.OVERPAID, over.paymentStatus)
        assertEquals(Money.ZERO, over.outstanding)
    }

    @Test fun `cancelled order keeps deposit minus costs and owes nothing`() {
        val f = OrderFinancials.calculate(example.copy(status = FulfillmentStatus.CANCELLED))
        assertEquals(ils(-100), f.netProfit)
        assertEquals(Money.ZERO, f.outstanding)
        assertEquals(Money.ZERO, f.dueNow)
    }

    @Test fun `empty order has no margin or cost per pin`() {
        val f = OrderFinancials.calculate(FinancialInput(emptyList(), emptyList(), emptyList(), status = FulfillmentStatus.DRAFT))
        assertNull(f.marginPercent)
        assertNull(f.costPerPin)
    }
}
