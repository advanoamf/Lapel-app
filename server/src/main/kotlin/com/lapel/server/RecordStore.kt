package com.lapel.server

/** Where records live. DynamoDB in production, in memory in tests. */
interface RecordStore {
    fun get(type: String, id: String): SyncRecord?
    fun put(record: SyncRecord)

    /** Records written to the server at or after [syncedAfter] (server time), oldest first. */
    fun changedSince(syncedAfter: Long): List<SyncRecord>

    /** Stores [value] under [key] only if nothing is there yet; returns the value that is stored. */
    fun putMetaIfAbsent(key: String, value: String): String
}

class InMemoryRecordStore : RecordStore {
    private val records = linkedMapOf<Pair<String, String>, SyncRecord>()
    private val meta = mutableMapOf<String, String>()

    @Synchronized override fun get(type: String, id: String) = records[type to id]

    @Synchronized override fun put(record: SyncRecord) {
        records[record.type to record.id] = record
    }

    @Synchronized override fun changedSince(syncedAfter: Long) =
        records.values.filter { it.syncedAt >= syncedAfter }.sortedBy { it.syncedAt }

    @Synchronized override fun putMetaIfAbsent(key: String, value: String) = meta.getOrPut(key) { value }

    val size: Int get() = records.size
}
