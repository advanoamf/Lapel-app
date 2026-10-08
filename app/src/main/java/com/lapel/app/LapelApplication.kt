package com.lapel.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lapel.app.work.ReminderNotifications
import com.lapel.app.work.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import java.time.Clock
import javax.inject.Inject

@HiltAndroidApp
class LapelApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var clock: Clock

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannels(this)
        ReminderScheduler.schedule(this, clock)
    }
}
