package com.lapel.app.data.sync

import android.content.Context
import com.lapel.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SyncStatus(
    val serverUrl: String,
    val loggedIn: Boolean,
    val lastSyncAt: Long?,
    val lastError: String?,
)

/** Server address, login token and last result, kept in app-private preferences. */
@Singleton
class SyncSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(read())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    val serverUrl: String get() = prefs.getString(KEY_SERVER, null) ?: BuildConfig.DEFAULT_SERVER_URL
    val token: String?
        get() = prefs.getString(KEY_TOKEN, null)?.takeIf { prefs.getLong(KEY_EXPIRES, 0) > System.currentTimeMillis() }
    val isConfigured: Boolean get() = token != null

    fun saveLogin(server: String, token: String, expiresAt: Long) = update {
        putString(KEY_SERVER, server.trim()).putString(KEY_TOKEN, token).putLong(KEY_EXPIRES, expiresAt).remove(KEY_ERROR)
    }

    fun logout() = update { remove(KEY_TOKEN).remove(KEY_EXPIRES) }
    fun recordSuccess(at: Long) = update { putLong(KEY_LAST, at).remove(KEY_ERROR) }
    fun recordError(message: String) = update { putString(KEY_ERROR, message) }

    private fun update(block: android.content.SharedPreferences.Editor.() -> android.content.SharedPreferences.Editor) {
        prefs.edit().block().apply()
        _status.value = read()
    }

    private fun read() = SyncStatus(
        serverUrl = serverUrl,
        loggedIn = token != null,
        lastSyncAt = prefs.getLong(KEY_LAST, 0).takeIf { it > 0 },
        lastError = prefs.getString(KEY_ERROR, null),
    )

    private companion object {
        const val KEY_SERVER = "server"
        const val KEY_TOKEN = "token"
        const val KEY_EXPIRES = "expires"
        const val KEY_LAST = "last"
        const val KEY_ERROR = "error"
    }
}
