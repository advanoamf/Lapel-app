package com.lapel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lapel.app.data.sync.SyncSettings
import com.lapel.app.ui.navigation.LapelApp
import com.lapel.app.ui.theme.LapelTheme
import com.lapel.app.work.SyncScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var syncSettings: SyncSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LapelTheme {
                LapelApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (syncSettings.isConfigured) SyncScheduler.syncNow(this) // pick up changes made on the computer
    }
}
