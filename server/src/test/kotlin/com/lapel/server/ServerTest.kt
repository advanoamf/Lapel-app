package com.lapel.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class TestClock(var now: Instant = Instant.parse("2026-10-08T10:00:00Z")) : Clock() {
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?) = this
    override fun instant() = now
    fun advance(ms: Long) { now = now.plusMillis(ms) }
}

private fun order(id: String, updatedAt: Long, number: String = "170", deleted: Boolean = false) =
    SyncRecord("ORDER", id, updatedAt, deleted, buildJsonObject { put("orderNumber", number) })

class SyncServiceTest {
    private val clock = TestClock()
    private val store = InMemoryRecordStore()
    private val sync = SyncService(store, clock)

    @Test fun `pushed records come back on pull`() {
        val result = sync.push(listOf(order("a1", 100), order("a2", 100, "171")))
        assertEquals(2, result.accepted)
        val pulled = sync.pull(0)
        assertEquals(setOf("a1", "a2"), pulled.records.map { it.id }.toSet())
        assertEquals(clock.millis(), pulled.cursor)
    }

    @Test fun `newer edit wins, older edit is reported stale`() {
        sync.push(listOf(order("a1", 200, "170")))
        val older = sync.push(listOf(order("a1", 150, "OLD")))
        assertEquals(listOf("ORDER/a1"), older.stale)
        val newer = sync.push(listOf(order("a1", 300, "NEW")))
        assertEquals(1, newer.accepted)
        assertEquals(JsonPrimitive("NEW"), store.get("ORDER", "a1")!!.data["orderNumber"])
    }

    @Test fun `pull returns only what changed since the cursor (with a small overlap)`() {
        sync.push(listOf(order("a1", 100)))
        clock.advance(10_000)
        val first = sync.pull(0)
        clock.advance(60_000)
        sync.push(listOf(order("a2", 200)))
        val second = sync.pull(first.cursor)
        assertEquals(listOf("a2"), second.records.map { it.id })
    }

    @Test fun `deletes travel as tombstones`() {
        sync.push(listOf(order("a1", 100)))
        sync.push(listOf(order("a1", 200, deleted = true)))
        assertTrue(sync.pull(0).records.single().deleted)
    }

    @Test fun `invalid records are rejected without stopping the batch`() {
        val result = sync.push(
            listOf(
                SyncRecord("HACKER", "x", 1),
                SyncRecord("ORDER", "bad id with spaces", 1),
                SyncRecord("ORDER", "ok-1", 0),
                order("ok-2", 5),
            ),
        )
        assertEquals(1, result.accepted)
        assertEquals(3, result.rejected.size)
    }
}

class AuthServiceTest {
    private val clock = TestClock()
    private val store = InMemoryRecordStore()

    @Test fun `right password gets a token that expires`() {
        val auth = AuthService("correct horse battery", store, clock, Duration.ofDays(30))
        val login = assertNotNull(auth.login("correct horse battery"))
        assertTrue(auth.isValid(login.token))
        clock.advance(Duration.ofDays(31).toMillis())
        assertFalse(auth.isValid(login.token))
    }

    @Test fun `wrong password, tampered token and short password are refused`() {
        val auth = AuthService("correct horse battery", store, clock)
        assertNull(auth.login("guess"))
        val token = auth.login("correct horse battery")!!.token
        assertFalse(auth.isValid(token.dropLast(2) + "xx"))
        assertFalse(auth.isValid("nonsense"))
        assertNull(AuthService("short", store, clock).login("short"))
    }

    @Test fun `signing key is shared across instances through the store`() {
        val a = AuthService("correct horse battery", store, clock)
        val token = a.login("correct horse battery")!!.token
        val b = AuthService("correct horse battery", store, clock)
        assertTrue(b.isValid(token))
    }
}

class ApiTest {
    private val clock = TestClock()
    private val store = InMemoryRecordStore()
    private val api = Api(AuthService("correct horse battery", store, clock), SyncService(store, clock))
    private val json = Json { ignoreUnknownKeys = true }

    private fun token(): String {
        val r = api.handle(Request("POST", "/api/login", body = """{"password":"correct horse battery"}"""))
        assertEquals(200, r.status)
        return json.decodeFromString(LoginResponse.serializer(), r.body).token
    }

    @Test fun `web page and health are public`() {
        assertEquals(200, api.handle(Request("GET", "/")).status)
        assertTrue(api.handle(Request("GET", "/")).body.contains("ניהול סיכות"))
        assertEquals(200, api.handle(Request("GET", "/api/health")).status)
    }

    @Test fun `sync needs a token`() {
        assertEquals(401, api.handle(Request("GET", "/api/sync")).status)
        assertEquals(401, api.handle(Request("POST", "/api/login", body = """{"password":"nope"}""")).status)
    }

    @Test fun `push then pull over HTTP`() {
        val auth = mapOf("authorization" to "Bearer ${token()}")
        val push = api.handle(
            Request(
                "POST", "/api/sync", headers = auth,
                body = """{"records":[{"type":"CUSTOMER","id":"c1","updatedAt":5,"data":{"name":"דנה"}}]}""",
            ),
        )
        assertEquals(200, push.status)
        val pull = api.handle(Request("GET", "/api/sync", query = mapOf("since" to "0"), headers = auth))
        val records = json.decodeFromString(PullResponse.serializer(), pull.body).records
        assertEquals(JsonPrimitive("דנה"), records.single().data["name"])
    }

    @Test fun `bad JSON is a 400, unknown route a 404`() {
        val auth = mapOf("Authorization" to "Bearer ${token()}")
        assertEquals(400, api.handle(Request("POST", "/api/sync", headers = auth, body = "{not json")).status)
        assertEquals(404, api.handle(Request("GET", "/api/nothing", headers = auth)).status)
    }
}
