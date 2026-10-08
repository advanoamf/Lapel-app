package com.lapel.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.lapel.app.ui.navigation.LapelApp
import com.lapel.app.ui.theme.LapelTheme
import com.lapel.app.work.ReminderNotifications
import com.lapel.app.data.sync.SyncSettings
import com.lapel.app.work.SyncScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var syncSettings: SyncSettings

    /** Order to open, when started from a reminder notification. */
    private val openOrderId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readOrderId(intent)
        setContent {
            LapelTheme {
                LapelApp(openOrderId = openOrderId.value, onOrderOpened = { openOrderId.value = null })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (syncSettings.isConfigured) SyncScheduler.syncNow(this) // pick up changes made on the computer
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readOrderId(intent)
    }

    private fun readOrderId(intent: Intent?) {
        intent?.getLongExtra(ReminderNotifications.EXTRA_ORDER_ID, 0L)?.takeIf { it > 0 }?.let { openOrderId.value = it }
    }
}
