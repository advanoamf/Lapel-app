package com.lapel.domain.reminder

import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.Money
import com.lapel.domain.model.ReminderType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderPolicyTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val policy = ReminderPolicy()

    private fun at(day: Int, hour: Int = 10, second: Int = 0): Instant =
        LocalDateTime.of(2026, 3, day, hour, 0, second).atZone(zone).toInstant()

    private val delivered = OrderReminderSnapshot(
        orderId = 1,
        status = FulfillmentStatus.DELIVERED,
        depositOutstanding = Money.ZERO,
        outstanding = Money.shekels(600),
        createdAt = at(1),
        orderedFromAlibabaOn = LocalDate.of(2026, 3, 1),
        deliveredAt = at(10, hour = 14),
        paymentTermsDays = 0,
    )

    private fun due(order: OrderReminderSnapshot, log: List<ReminderLogEntry>, now: Instant) =
        policy.remindersDue(listOf(order), log, now)

    @Test fun `balance reminder on delivery day`() {
        val r = due(delivered, emptyList(), at(10, hour = 15)).single()
        assertEquals(ReminderType.BALANCE_DUE, r.type)
        assertEquals(Money.shekels(600), r.amount)
    }

    @Test fun `balance reminder repeats after three calendar days, even a few seconds early`() {
        val patient = ReminderPolicy(ReminderSettings(overdueGraceDays = 7))
        val log = listOf(ReminderLogEntry(1, ReminderType.BALANCE_DUE, at(10, second = 5)))
        assertTrue(patient.remindersDue(listOf(delivered), log, at(12)).isEmpty())
        assertEquals(ReminderType.BALANCE_DUE, patient.remindersDue(listOf(delivered), log, at(13, second = 0)).single().type)
    }

    @Test fun `overdue after grace period, then daily`() {
        val log = listOf(ReminderLogEntry(1, ReminderType.BALANCE_DUE, at(10)))
        assertEquals(ReminderType.BALANCE_OVERDUE, due(delivered, log, at(13)).single().type)
        val sentToday = log + ReminderLogEntry(1, ReminderType.BALANCE_OVERDUE, at(13))
        assertTrue(due(delivered, sentToday, at(13, hour = 18)).isEmpty())
        assertEquals(ReminderType.BALANCE_OVERDUE, due(delivered, sentToday, at(14)).single().type)
        assertTrue(policy.isOverdue(delivered, at(13)))
        assertFalse(policy.isOverdue(delivered, at(12)))
    }

    @Test fun `net plus 60 customer is reminded once, then only when overdue`() {
        val terms = delivered.copy(paymentTermsDays = 60)
        val log = listOf(ReminderLogEntry(1, ReminderType.BALANCE_DUE, at(10)))
        assertTrue(due(terms, log, at(20)).isEmpty())
        assertFalse(policy.isOverdue(terms, at(20)))
        val dueDay = LocalDateTime.of(2026, 5, 9, 10, 0).atZone(zone).toInstant() // 10 March + 60 days
        assertEquals(ReminderType.BALANCE_OVERDUE, due(terms, log, dueDay).single().type)
    }

    @Test fun `snoozed reminder waits`() {
        val log = listOf(ReminderLogEntry(1, ReminderType.BALANCE_DUE, at(10), snoozedUntil = at(14)))
        assertTrue(due(delivered, log, at(13)).isEmpty()) // would be overdue, but snoozed
        assertEquals(ReminderType.BALANCE_OVERDUE, due(delivered, log, at(14)).single().type)
    }

    @Test fun `deposit missing after ordering from Alibaba`() {
        val ordered = delivered.copy(
            status = FulfillmentStatus.ORDERED_FROM_ALIBABA,
            depositOutstanding = Money.shekels(300),
            deliveredAt = null,
        )
        val r = due(ordered, emptyList(), at(2)).single()
        assertEquals(ReminderType.DEPOSIT_DUE, r.type)
        assertEquals(Money.shekels(300), r.amount)
        assertTrue(policy.isOverdue(ordered, at(4)))
    }

    @Test fun `paid orders get nothing`() {
        assertTrue(due(delivered.copy(outstanding = Money.ZERO), emptyList(), at(20)).isEmpty())
        assertTrue(due(delivered.copy(status = FulfillmentStatus.COMPLETED), emptyList(), at(20)).isEmpty())
    }

    @Test fun `old draft is mentioned once`() {
        val draft = delivered.copy(status = FulfillmentStatus.DRAFT, deliveredAt = null)
        assertTrue(due(draft, emptyList(), at(7)).isEmpty())
        assertEquals(ReminderType.STALE_DRAFT, due(draft, emptyList(), at(8)).single().type)
        val log = listOf(ReminderLogEntry(1, ReminderType.STALE_DRAFT, at(8)))
        assertTrue(due(draft, log, at(20)).isEmpty())
    }
}
