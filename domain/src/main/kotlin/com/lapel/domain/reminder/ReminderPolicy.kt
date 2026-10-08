package com.lapel.domain.reminder

import com.lapel.domain.model.DeliveryMethod
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.Money
import com.lapel.domain.model.ReminderType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ReminderSettings(
    /** Repeat deposit / balance reminders every N days. */
    val repeatDays: Int = 3,
    /** A balance becomes overdue this many days after delivery (or after the customer's terms, if longer). */
    val overdueGraceDays: Int = 3,
    val staleDraftDays: Int = 7,
    /** Ask for the shipping address this many days after the order date (a week and a half). */
    val addressAfterDays: Int = 10,
    val zone: ZoneId = ZoneId.of("Asia/Jerusalem"),
)

/** What the reminder logic needs to know about one order. */
data class OrderReminderSnapshot(
    val orderId: Long,
    val status: FulfillmentStatus,
    val depositOutstanding: Money,
    val outstanding: Money,
    val createdAt: Instant,
    val orderedFromAlibabaOn: LocalDate?,
    val deliveredAt: Instant?,
    /** 0 = pay on delivery; 60 = "שוטף+60". */
    val paymentTermsDays: Int,
    val orderDate: LocalDate? = null,
    val deliveryMethod: DeliveryMethod = DeliveryMethod.FEDEX,
    val hasAddress: Boolean = true,
    val addressSentToSupplier: Boolean = true,
)

enum class AddressStatus { NOT_NEEDED, MISSING, NOT_SENT, DONE }

data class ReminderLogEntry(
    val orderId: Long,
    val type: ReminderType,
    val sentAt: Instant,
    val snoozedUntil: Instant? = null,
)

data class Reminder(val orderId: Long, val type: ReminderType, val amount: Money)

/**
 * Decides which payment reminders to send today. Runs once a day; "every N days" is measured in
 * calendar days in Israel time so a reminder sent at 10:00:05 is due again 3 days later at 10:00.
 */
class ReminderPolicy(private val settings: ReminderSettings = ReminderSettings()) {

    fun remindersDue(
        orders: List<OrderReminderSnapshot>,
        log: List<ReminderLogEntry>,
        now: Instant,
    ): List<Reminder> {
        val byOrder = log.groupBy { it.orderId }
        return orders.flatMap { order ->
            val orderLog = byOrder[order.orderId].orEmpty()
            listOfNotNull(reminderFor(order, orderLog, now), addressReminder(order, orderLog, now))
        }
    }

    /**
     * Shipping address progress for an order the supplier ships by FedEx. Only orders already
     * placed with Alibaba and not yet shipped need it.
     */
    fun addressStatus(order: OrderReminderSnapshot): AddressStatus = when {
        order.deliveryMethod != DeliveryMethod.FEDEX -> AddressStatus.NOT_NEEDED
        order.status != FulfillmentStatus.ORDERED_FROM_ALIBABA -> AddressStatus.NOT_NEEDED
        !order.hasAddress -> AddressStatus.MISSING
        !order.addressSentToSupplier -> AddressStatus.NOT_SENT
        else -> AddressStatus.DONE
    }

    private fun addressReminder(order: OrderReminderSnapshot, log: List<ReminderLogEntry>, now: Instant): Reminder? {
        if (log.any { it.snoozedUntil?.isAfter(now) == true }) return null
        val type = when (addressStatus(order)) {
            AddressStatus.MISSING -> ReminderType.ADDRESS_MISSING
            AddressStatus.NOT_SENT -> ReminderType.ADDRESS_NOT_SENT
            else -> return null
        }
        val today = today(now)
        val orderDate = order.orderDate ?: return null
        if (daysBetween(orderDate, today) < settings.addressAfterDays) return null
        val last = log.filter { it.type == type }.maxByOrNull { it.sentAt }
        if (last != null && daysBetween(today(last.sentAt), today) < settings.repeatDays) return null
        return Reminder(order.orderId, type, Money.ZERO)
    }

    /** True when money that should already have been paid is late (dashboard "Overdue" card). */
    fun isOverdue(order: OrderReminderSnapshot, now: Instant): Boolean {
        val today = today(now)
        return when (order.status) {
            FulfillmentStatus.ORDERED_FROM_ALIBABA, FulfillmentStatus.SHIPPED ->
                order.depositOutstanding.isPositive &&
                    order.orderedFromAlibabaOn != null &&
                    daysBetween(order.orderedFromAlibabaOn, today) >= settings.overdueGraceDays
            FulfillmentStatus.DELIVERED, FulfillmentStatus.COMPLETED ->
                order.outstanding.isPositive && balanceOverdueFrom(order)?.let { !today.isBefore(it) } == true
            else -> false
        }
    }

    private fun reminderFor(
        order: OrderReminderSnapshot,
        log: List<ReminderLogEntry>,
        now: Instant,
    ): Reminder? {
        // Snoozing any reminder of an order silences all of its reminders until then.
        if (log.any { it.snoozedUntil?.isAfter(now) == true }) return null

        val today = today(now)
        fun lastSent(type: ReminderType) = log.filter { it.type == type }.maxByOrNull { it.sentAt }
        fun sentWithin(type: ReminderType, days: Int) =
            lastSent(type)?.let { daysBetween(today(it.sentAt), today) < days } == true

        return when (order.status) {
            FulfillmentStatus.DRAFT -> {
                val type = ReminderType.STALE_DRAFT
                val old = daysBetween(today(order.createdAt), today) >= settings.staleDraftDays
                if (old && lastSent(type) == null) Reminder(order.orderId, type, Money.ZERO) else null
            }

            FulfillmentStatus.ORDERED_FROM_ALIBABA, FulfillmentStatus.SHIPPED -> {
                val type = ReminderType.DEPOSIT_DUE
                if (order.depositOutstanding.isPositive && !sentWithin(type, settings.repeatDays)) {
                    Reminder(order.orderId, type, order.depositOutstanding)
                } else {
                    null
                }
            }

            FulfillmentStatus.DELIVERED -> balanceReminder(order, today, ::lastSent, ::sentWithin)

            FulfillmentStatus.COMPLETED, FulfillmentStatus.CANCELLED -> null
        }
    }

    private fun balanceReminder(
        order: OrderReminderSnapshot,
        today: LocalDate,
        lastSent: (ReminderType) -> ReminderLogEntry?,
        sentWithin: (ReminderType, Int) -> Boolean,
    ): Reminder? {
        if (!order.outstanding.isPositive) return null
        val overdueFrom = balanceOverdueFrom(order) ?: return null

        if (!today.isBefore(overdueFrom)) {
            val type = ReminderType.BALANCE_OVERDUE
            return if (!sentWithin(type, 1)) Reminder(order.orderId, type, order.outstanding) else null
        }

        val type = ReminderType.BALANCE_DUE
        val neverSent = lastSent(type) == null
        // With payment terms (e.g. net+60) remind once on delivery, then wait for the due date.
        val repeat = order.paymentTermsDays == 0 && !sentWithin(type, settings.repeatDays)
        return if (neverSent || repeat) Reminder(order.orderId, type, order.outstanding) else null
    }

    private fun balanceOverdueFrom(order: OrderReminderSnapshot): LocalDate? {
        val delivered = order.deliveredAt ?: return null
        val days = maxOf(order.paymentTermsDays, settings.overdueGraceDays)
        return today(delivered).plusDays(days.toLong())
    }

    private fun today(instant: Instant): LocalDate = instant.atZone(settings.zone).toLocalDate()

    private fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)
}
