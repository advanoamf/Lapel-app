package com.lapel.server

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

data class Request(
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

data class Response(val status: Int, val body: String, val contentType: String = "application/json; charset=utf-8")

/** HTTP routes, independent of Lambda so they can be tested directly. */
class Api(private val auth: AuthService, private val sync: SyncService) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun handle(req: Request): Response = try {
        route(req)
    } catch (e: IllegalArgumentException) {
        error(400, e.message ?: "bad request")
    } catch (e: kotlinx.serialization.SerializationException) {
        error(400, "invalid JSON")
    }

    private fun route(req: Request): Response = when {
        req.method == "GET" && (req.path == "/" || req.path == "/index.html") -> page()
        req.method == "GET" && req.path == "/api/health" -> Response(200, """{"ok":true}""")
        req.method == "POST" && req.path == "/api/login" -> {
            val body = parse(req.body, LoginRequest.serializer())
            auth.login(body.password)?.let { ok(it, LoginResponse.serializer()) } ?: run {
                Thread.sleep(LOGIN_FAILURE_DELAY_MS) // slows down password guessing
                error(401, "wrong password")
            }
        }
        req.path.startsWith("/api/") && !auth.isValid(bearer(req)) -> error(401, "login required")
        req.method == "GET" && req.path == "/api/sync" -> {
            val since = req.query["since"]?.toLongOrNull() ?: 0
            ok(sync.pull(since), PullResponse.serializer())
        }
        req.method == "POST" && req.path == "/api/sync" -> {
            val body = parse(req.body, PushRequest.serializer())
            ok(sync.push(body.records), PushResponse.serializer())
        }
        else -> error(404, "not found")
    }

    private fun bearer(req: Request): String? =
        req.headers.entries.firstOrNull { it.key.equals("authorization", ignoreCase = true) }
            ?.value?.removePrefix("Bearer ")?.trim()

    private fun <T> parse(body: String?, serializer: KSerializer<T>): T =
        json.decodeFromString(serializer, body ?: throw IllegalArgumentException("missing body"))

    private fun <T> ok(value: T, serializer: KSerializer<T>) = Response(200, json.encodeToString(serializer, value))

    private fun error(status: Int, message: String) =
        Response(status, json.encodeToString(ErrorResponse.serializer(), ErrorResponse(message)))

    private fun page() = Response(
        200,
        Api::class.java.getResourceAsStream("/web/index.html")!!.bufferedReader().readText(),
        "text/html; charset=utf-8",
    )

    private companion object {
        const val LOGIN_FAILURE_DELAY_MS = 1_000L
    }
}
