package com.lapel.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lapel.app.MainActivity
import com.lapel.app.R
import com.lapel.app.data.local.dao.OrderSummaryRow
import com.lapel.app.ui.common.formatted
import com.lapel.domain.model.ReminderType
import com.lapel.domain.reminder.Reminder

object ReminderNotifications {
    const val CHANNEL_REMINDERS = "reminders"
    const val EXTRA_ORDER_ID = "com.lapel.app.ORDER_ID"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_reminders_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Returns false when notifications are not allowed, so the reminder is retried next time. */
    fun post(context: Context, reminder: Reminder, order: OrderSummaryRow): Boolean {
        if (!canPost(context)) return false
        val who = listOfNotNull(order.customerName, order.title.takeIf { it != order.customerName }).joinToString(" · ")
        val (title, text) = when (reminder.type) {
            ReminderType.DEPOSIT_DUE -> R.string.notify_deposit_title to context.getString(R.string.notify_money_text, who, order.orderNumber, reminder.amount.formatted())
            ReminderType.BALANCE_DUE -> R.string.notify_balance_title to context.getString(R.string.notify_money_text, who, order.orderNumber, reminder.amount.formatted())
            ReminderType.BALANCE_OVERDUE -> R.string.notify_overdue_title to context.getString(R.string.notify_money_text, who, order.orderNumber, reminder.amount.formatted())
            ReminderType.STALE_DRAFT -> R.string.notify_draft_title to context.getString(R.string.notify_order_text, who, order.orderNumber)
            ReminderType.ADDRESS_MISSING -> R.string.notify_address_missing_title to context.getString(R.string.notify_order_text, who, order.orderNumber)
            ReminderType.ADDRESS_NOT_SENT -> R.string.notify_address_not_sent_title to context.getString(R.string.notify_order_text, who, order.orderNumber)
            else -> return true
        }
        val id = (order.orderId * 16 + reminder.type.ordinal).toInt()
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_ORDER_ID, order.orderId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        @Suppress("MissingPermission") // checked in canPost()
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
    }
}
