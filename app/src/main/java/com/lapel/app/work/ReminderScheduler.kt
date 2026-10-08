package com.lapel.app.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val DAILY = "reminders-daily"
    private const val NOW = "reminders-now"
    private val TIME_OF_DAY: LocalTime = LocalTime.of(10, 0)

    /** Daily at about 10:00 Israel time. Safe to call on every app start. */
    fun schedule(context: Context, clock: Clock) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilNext(clock).toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Runs the check right away (Settings → "check reminders now"). */
    fun runNow(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderWorker>().build())
    }

    internal fun delayUntilNext(clock: Clock): Duration {
        val now = java.time.ZonedDateTime.now(clock)
        var next = now.with(TIME_OF_DAY)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
