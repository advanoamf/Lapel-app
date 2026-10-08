package com.lapel.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.Clock

/**
 * Last write wins per record, by the device's [SyncRecord.updatedAt]. Pulls overlap by a few
 * seconds so a record written in the same instant as a previous pull is never missed; devices
 * apply records idempotently, so seeing one twice is harmless.
 */
class SyncService(private val store: RecordStore, private val clock: Clock) {

    fun pull(since: Long): PullResponse {
        val now = clock.millis()
        val from = if (since <= 0) 0 else since - OVERLAP_MS
        return PullResponse(cursor = now, records = store.changedSince(from))
    }

    fun push(records: List<SyncRecord>): PushResponse {
        require(records.size <= MAX_BATCH) { "Too many records in one request (max $MAX_BATCH)" }
        var accepted = 0
        val stale = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        for (r in records) {
            val problem = validate(r)
            if (problem != null) { rejected += "${r.type}/${r.id}: $problem"; continue }
            val existing = store.get(r.type, r.id)
            if (existing != null && existing.updatedAt >= r.updatedAt) { stale += "${r.type}/${r.id}"; continue }
            store.put(r.copy(syncedAt = clock.millis()))
            accepted++
        }
        return PushResponse(accepted, stale, rejected, clock.millis())
    }

    private fun validate(r: SyncRecord): String? = when {
        r.type !in RecordTypes.ALL -> "unknown type"
        !ID.matches(r.id) -> "bad id"
        r.updatedAt <= 0 -> "missing updatedAt"
        Json.encodeToString(JsonObject.serializer(), r.data).length > MAX_DATA_CHARS -> "record too large"
        else -> null
    }

    companion object {
        const val OVERLAP_MS = 5_000L
        const val MAX_BATCH = 500
        private const val MAX_DATA_CHARS = 100_000
        private val ID = Regex("^[A-Za-z0-9-]{1,64}$")
    }
}
