package com.lapel.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.room.InvalidationTracker
import androidx.work.Configuration
import com.lapel.app.data.local.LapelDatabase
import com.lapel.app.data.sync.SyncSettings
import com.lapel.app.data.sync.SyncTables
import com.lapel.app.work.ReminderNotifications
import com.lapel.app.work.ReminderScheduler
import com.lapel.app.work.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import java.time.Clock
import javax.inject.Inject

@HiltAndroidApp
class LapelApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var clock: Clock
    @Inject lateinit var db: LapelDatabase
    @Inject lateinit var syncSettings: SyncSettings

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannels(this)
        ReminderScheduler.schedule(this, clock)
        SyncScheduler.schedulePeriodic(this)

        // Any change to synced tables → send it to the server shortly after.
        db.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer(SyncTables.ALL.map { it.name }.toTypedArray()) {
                override fun onInvalidated(tables: Set<String>) {
                    if (syncSettings.isConfigured) SyncScheduler.syncSoon(this@LapelApplication)
                }
            },
        )
    }
}
