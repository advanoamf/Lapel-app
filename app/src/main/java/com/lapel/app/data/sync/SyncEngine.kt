package com.lapel.app.data.sync

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lapel.app.data.local.LapelDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

data class SyncReport(val pushed: Int, val pulled: Int)

/**
 * Two-way sync between the phone's database and the server:
 * 1. push rows marked dirty by the triggers, plus deletions (tombstones);
 * 2. pull everything that changed on the server since the last pull.
 * Conflicts: the newer edit wins (by `syncUpdatedAt`). Rows reference each other by uid on the
 * wire and by local id in the database.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val db: LapelDatabase,
    private val api: SyncApi,
    private val settings: SyncSettings,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val sql: SupportSQLiteDatabase get() = db.openHelper.writableDatabase

    suspend fun sync(): SyncReport = mutex.withLock {
        val token = settings.token ?: throw NotLoggedInException()
        val server = settings.serverUrl
        try {
            val pushed = push(server, token)
            val pulled = pull(server, token)
            settings.recordSuccess(clock.millis())
            SyncReport(pushed, pulled)
        } catch (e: NotLoggedInException) {
            settings.logout()
            settings.recordError("login")
            throw e
        } catch (e: Exception) {
            settings.recordError(e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    /** Local changes waiting to be sent. */
    suspend fun pendingChanges(): Int = db.withTransaction {
        SyncTables.ALL.sumOf { t -> count("SELECT COUNT(*) FROM ${t.name} WHERE dirty = 1") } +
            count("SELECT COUNT(*) FROM sync_tombstones")
    }

    // ---- push ---------------------------------------------------------------------------------

    private suspend fun push(server: String, token: String): Int {
        val records = db.withTransaction { collectChanges() }
        if (records.isEmpty()) return 0
        val done = mutableSetOf<String>()
        records.chunked(PUSH_BATCH).forEach { batch ->
            val result = api.push(server, token, batch)
            val rejected = result.rejected.map { it.substringBefore(':') }.toSet()
            batch.filter { "${it.type}/${it.id}" !in rejected }.mapTo(done) { "${it.type}/${it.id}" }
        }
        // Clear only what was sent and not changed again in the meantime.
        db.withTransaction {
            applying {
                records.filter { "${it.type}/${it.id}" in done }.forEach { r ->
                    if (r.deleted) {
                        sql.execSQL("DELETE FROM sync_tombstones WHERE type = ? AND uid = ? AND deletedAt <= ?", arrayOf(r.type, r.id, r.updatedAt))
                    } else {
                        val t = SyncTables.byType.getValue(r.type)
                        sql.execSQL("UPDATE ${t.name} SET dirty = 0 WHERE uid = ? AND syncUpdatedAt = ?", arrayOf(r.id, r.updatedAt))
                    }
                }
            }
        }
        return done.size
    }

    private fun collectChanges(): List<RemoteRecord> {
        val out = mutableListOf<RemoteRecord>()
        val uidCache = mutableMapOf<String, Map<Long, String>>()
        fun uids(table: String) = uidCache.getOrPut(table) {
            buildMap { sql.query("SELECT id, uid FROM $table WHERE uid IS NOT NULL").use { c -> while (c.moveToNext()) put(c.getLong(0), c.getString(1)) } }
        }
        SyncTables.ALL.forEach { t ->
            sql.query("SELECT * FROM ${t.name} WHERE dirty = 1").use { c ->
                while (c.moveToNext()) {
                    val uid = c.getString(c.getColumnIndexOrThrow("uid")) ?: continue
                    val data = buildMap<String, JsonElement> {
                        for (i in 0 until c.columnCount) {
                            val name = c.getColumnName(i)
                            if (name in SyncTables.LOCAL_COLUMNS) continue
                            val target = t.refs[name]
                            put(
                                name,
                                if (target != null) {
                                    if (c.isNull(i)) JsonNull else uids(target)[c.getLong(i)]?.let { JsonPrimitive(it) } ?: JsonNull
                                } else {
                                    c.jsonAt(i)
                                },
                            )
                        }
                    }
                    val updatedAt = c.getLong(c.getColumnIndexOrThrow("syncUpdatedAt")).coerceAtLeast(1)
                    out += RemoteRecord(t.type, uid, updatedAt, deleted = false, data = JsonObject(data))
                }
            }
        }
        sql.query("SELECT type, uid, deletedAt FROM sync_tombstones").use { c ->
            while (c.moveToNext()) out += RemoteRecord(c.getString(0), c.getString(1), c.getLong(2).coerceAtLeast(1), deleted = true)
        }
        return out
    }

    // ---- pull ---------------------------------------------------------------------------------

    private suspend fun pull(server: String, token: String): Int {
        val since = db.withTransaction { sql.query("SELECT cursor FROM sync_state WHERE id = 1").use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } }
        val result = api.pull(server, token, since)
        var applied = 0
        db.withTransaction {
            applying {
                var waitingForParent = false
                val byType = result.records.groupBy { it.type }
                SyncTables.ALL.forEach { t ->
                    byType[t.type].orEmpty().filter { !it.deleted }.sortedBy { it.updatedAt }.forEach { r ->
                        when (upsert(t, r)) {
                            Outcome.APPLIED -> applied++
                            // A parent that never arrives (e.g. deleted long ago) must not block the cursor forever.
                            Outcome.MISSING_PARENT -> if (result.cursor - r.updatedAt < PARENT_WAIT_MS) waitingForParent = true
                            Outcome.SKIPPED -> Unit
                        }
                    }
                }
                SyncTables.ALL.asReversed().forEach { t ->
                    byType[t.type].orEmpty().filter { it.deleted }.forEach { r -> if (delete(t, r)) applied++ }
                }
                // If something waited for a parent we have not received, fetch the same range again next time.
                if (!waitingForParent) sql.execSQL("UPDATE sync_state SET cursor = ? WHERE id = 1", arrayOf(result.cursor))
            }
        }
        return applied
    }

    private enum class Outcome { APPLIED, SKIPPED, MISSING_PARENT }

    private data class LocalRow(val id: Long, val dirty: Boolean, val updatedAt: Long)

    private fun local(t: SyncTable, uid: String): LocalRow? =
        sql.query("SELECT id, dirty, syncUpdatedAt FROM ${t.name} WHERE uid = ?", arrayOf(uid)).use { c ->
            if (c.moveToFirst()) LocalRow(c.getLong(0), c.getInt(1) == 1, c.getLong(2)) else null
        }

    private fun upsert(t: SyncTable, r: RemoteRecord): Outcome {
        val existing = local(t, r.id)
        if (existing != null && existing.updatedAt >= r.updatedAt) return Outcome.SKIPPED // same or newer here
        val deletedHere = count("SELECT COUNT(*) FROM sync_tombstones WHERE type = ? AND uid = ? AND deletedAt >= ?", arrayOf(r.type, r.id, r.updatedAt)) > 0
        if (deletedHere) return Outcome.SKIPPED

        val columns = columns(t.name)
        val values = ContentValues()
        for ((key, value) in r.data) {
            if (key !in columns || key in SyncTables.LOCAL_COLUMNS) continue
            val target = t.refs[key]
            if (target != null) {
                val refUid = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (refUid == null) { values.putNull(key); continue }
                val localId = sql.query("SELECT id FROM $target WHERE uid = ?", arrayOf(refUid)).use { c -> if (c.moveToFirst()) c.getLong(0) else null }
                when {
                    localId != null -> values.put(key, localId)
                    key in t.softRefs -> values.putNull(key)
                    else -> return Outcome.MISSING_PARENT
                }
            } else {
                values.putJson(key, value)
            }
        }
        values.put("uid", r.id)
        values.put("syncUpdatedAt", r.updatedAt)
        values.put("dirty", 0)
        return try {
            if (existing != null) {
                sql.update(t.name, SQLiteDatabase.CONFLICT_ABORT, values, "id = ?", arrayOf(existing.id))
            } else {
                sql.insert(t.name, SQLiteDatabase.CONFLICT_ABORT, values)
            }
            Outcome.APPLIED
        } catch (_: SQLiteConstraintException) {
            Outcome.SKIPPED // e.g. a duplicate tracking number or data from an older app version
        }
    }

    private fun delete(t: SyncTable, r: RemoteRecord): Boolean {
        val existing = local(t, r.id) ?: return false
        if (existing.dirty && existing.updatedAt > r.updatedAt) return false // edited here after the delete
        return try {
            sql.delete(t.name, "id = ?", arrayOf(existing.id)) > 0
        } catch (_: SQLiteConstraintException) {
            false // still referenced, e.g. a customer with orders
        }
    }

    // ---- helpers ------------------------------------------------------------------------------

    private val columnCache = mutableMapOf<String, Set<String>>()

    private fun columns(table: String) = columnCache.getOrPut(table) {
        buildSet { sql.query("PRAGMA table_info($table)").use { c -> while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) } }
    }

    /** Writes made while this runs are not tracked as local changes. */
    private inline fun <T> applying(block: () -> T): T {
        sql.execSQL("UPDATE sync_state SET applying = 1 WHERE id = 1")
        try {
            return block()
        } finally {
            sql.execSQL("UPDATE sync_state SET applying = 0 WHERE id = 1")
        }
    }

    private fun count(query: String, args: Array<Any?> = emptyArray()): Int =
        sql.query(query, args).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    private fun Cursor.jsonAt(i: Int): JsonElement = when (getType(i)) {
        Cursor.FIELD_TYPE_NULL -> JsonNull
        Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(getLong(i))
        Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(getDouble(i))
        else -> JsonPrimitive(getString(i))
    }

    private fun ContentValues.putJson(key: String, value: JsonElement) {
        val p = value as? JsonPrimitive
        when {
            p == null || p is JsonNull -> putNull(key)
            p.isString -> put(key, p.content)
            p.booleanOrNull != null -> put(key, if (p.booleanOrNull == true) 1 else 0)
            p.longOrNull != null -> put(key, p.longOrNull)
            else -> put(key, p.content.toDouble())
        }
    }

    private companion object {
        const val PUSH_BATCH = 200
        const val PARENT_WAIT_MS = 60 * 60 * 1000L
    }
}
