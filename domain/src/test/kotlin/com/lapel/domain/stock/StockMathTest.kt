package com.lapel.domain.stock

import com.lapel.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class StockMathTest {
    @Test fun `remaining stock, debt and profit`() {
        val sales = listOf(
            // 5 pins at ₪10, mailed: ₪20 charged, ₪18 cost; paid 34 + 16
            StockSaleLine(5, Money.shekels(10), Money.shekels(20), Money.shekels(18), listOf(Money.shekels(34), Money.shekels(16))),
            // 60 pins, pickup, half paid
            StockSaleLine(60, Money.shekels(10), Money.ZERO, Money.ZERO, listOf(Money.shekels(300))),
        )
        val s = StockMath.summarize(quantityReceived = 150, writtenOff = 3, batchCosts = listOf(Money.shekels(400)), sales = sales)
        assertEquals(82, s.remaining)
        assertEquals(65, s.sold)
        assertEquals(Money.shekels(670), s.revenue)
        assertEquals(Money.shekels(320), s.outstanding)
        assertEquals(Money.shekels(252), s.profitSoFar)
    }
}
