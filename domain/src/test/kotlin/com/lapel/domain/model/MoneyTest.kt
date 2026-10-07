package com.lapel.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {
    @Test fun `percent rounds half up to a whole agora`() {
        assertEquals(Money(50), Money(99).percent(50)) // 49.5 → 50
        assertEquals(Money(150_000), Money.shekels(3_000).percent(50))
    }

    @Test fun `divide by zero is null`() {
        assertNull(Money(100).dividedBy(0))
        assertEquals(Money(800), Money.shekels(1_600).dividedBy(200))
    }

    @Test fun `formats as shekels`() {
        assertEquals("₪1234.05", Money(123_405).toString())
        assertEquals("-₪0.50", Money(-50).toString())
    }
}
