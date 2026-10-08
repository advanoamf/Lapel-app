package com.lapel.app.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class RemoteRecord(
    val type: String,
    val id: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val data: JsonObject = JsonObject(emptyMap()),
    val syncedAt: Long = 0,
)

@Serializable data class PullResult(val cursor: Long, val records: List<RemoteRecord>)
@Serializable data class PushResult(val accepted: Int, val stale: List<String> = emptyList(), val rejected: List<String> = emptyList(), val cursor: Long = 0)
@Serializable data class LoginResult(val token: String, val expiresAt: Long)
@Serializable private data class LoginBody(val password: String)
@Serializable private data class PushBody(val records: List<RemoteRecord>)

class NotLoggedInException : Exception("login required")
class WrongPasswordException : Exception("wrong password")

/** The Lapel server's HTTP API (see server/). */
interface SyncApi {
    suspend fun login(server: String, password: String): LoginResult
    suspend fun pull(server: String, token: String, since: Long): PullResult
    suspend fun push(server: String, token: String, records: List<RemoteRecord>): PushResult
}

@Singleton
class HttpSyncApi @Inject constructor() : SyncApi {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun login(server: String, password: String): LoginResult = try {
        json.decodeFromString(LoginResult.serializer(), call(server, "POST", "api/login", null, json.encodeToString(LoginBody.serializer(), LoginBody(password))))
    } catch (_: NotLoggedInException) {
        throw WrongPasswordException()
    }

    override suspend fun pull(server: String, token: String, since: Long): PullResult =
        json.decodeFromString(PullResult.serializer(), call(server, "GET", "api/sync?since=$since", token, null))

    override suspend fun push(server: String, token: String, records: List<RemoteRecord>): PushResult =
        json.decodeFromString(PushResult.serializer(), call(server, "POST", "api/sync", token, json.encodeToString(PushBody.serializer(), PushBody(records))))

    private suspend fun call(server: String, method: String, path: String, token: String?, body: String?): String =
        withContext(Dispatchers.IO) {
            val url = URL(server.trimEnd('/') + "/" + path)
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = conn.responseCode
                if (code == 401) throw NotLoggedInException()
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IOException("Server error $code: ${text.take(200)}")
                text
            } finally {
                conn.disconnect()
            }
        }
}
