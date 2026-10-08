package com.lapel.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lapel.app.data.sync.SyncApi
import com.lapel.app.data.sync.SyncEngine
import com.lapel.app.data.sync.SyncSettings
import com.lapel.app.data.sync.SyncStatus
import com.lapel.app.data.sync.WrongPasswordException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncUi(
    val busy: Boolean = false,
    val pending: Int = 0,
    val message: SyncMessage? = null,
)

sealed interface SyncMessage {
    data object WrongPassword : SyncMessage
    data class Done(val pushed: Int, val pulled: Int) : SyncMessage
    data class Failed(val reason: String) : SyncMessage
}

@HiltViewModel
class SyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SyncApi,
    private val engine: SyncEngine,
    private val settings: SyncSettings,
) : ViewModel() {

    val status: StateFlow<SyncStatus> = settings.status
    val ui = MutableStateFlow(SyncUi())

    init { refreshPending() }

    fun connect(server: String, password: String) {
        ui.value = ui.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                val login = api.login(server.trim(), password)
                settings.saveLogin(server, login.token, login.expiresAt)
                val report = engine.sync()
                ui.value = SyncUi(message = SyncMessage.Done(report.pushed, report.pulled))
            } catch (_: WrongPasswordException) {
                ui.value = SyncUi(message = SyncMessage.WrongPassword)
            } catch (e: Exception) {
                ui.value = SyncUi(message = SyncMessage.Failed(e.message ?: e.javaClass.simpleName))
            }
            refreshPending()
        }
    }

    fun syncNow() {
        ui.value = ui.value.copy(busy = true, message = null)
        viewModelScope.launch {
            ui.value = try {
                val report = engine.sync()
                SyncUi(message = SyncMessage.Done(report.pushed, report.pulled))
            } catch (e: Exception) {
                SyncUi(message = SyncMessage.Failed(e.message ?: e.javaClass.simpleName))
            }
            refreshPending()
        }
    }

    fun logout() {
        settings.logout()
        ui.value = SyncUi()
    }

    private fun refreshPending() {
        viewModelScope.launch { ui.value = ui.value.copy(pending = runCatching { engine.pendingChanges() }.getOrDefault(0)) }
    }
}
