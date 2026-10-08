package com.lapel.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lapel.app.data.local.LapelDatabase
import com.lapel.app.data.local.entity.ReminderLogEntity
import com.lapel.domain.reminder.ReminderLogEntry
import com.lapel.domain.reminder.ReminderPolicy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Clock

/** Daily check: sends payment and shipping-address reminders that are due today. */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: LapelDatabase,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = clock.instant()
        val rows = db.orderDao().observeSummaries().first()
        val byId = rows.associateBy { it.orderId }
        val log = db.reminderLogDao().getAll().map { ReminderLogEntry(it.orderId, it.type, it.sentAt, it.snoozedUntil) }

        val due = ReminderPolicy().remindersDue(rows.map { it.reminderSnapshot() }, log, now)
        due.forEach { reminder ->
            val order = byId[reminder.orderId] ?: return@forEach
            if (ReminderNotifications.post(applicationContext, reminder, order)) {
                db.reminderLogDao().insert(ReminderLogEntity(orderId = reminder.orderId, type = reminder.type, sentAt = now, snoozedUntil = null))
            }
        }
        return Result.success()
    }
}
