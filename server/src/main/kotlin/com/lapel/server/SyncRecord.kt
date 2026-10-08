package com.lapel.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * One app record as the server stores it. The server does not interpret [data]; devices own the
 * schema. [updatedAt] (device clock) decides conflicts, [syncedAt] (server clock) drives pulls.
 */
@Serializable
data class SyncRecord(
    val type: String,
    val id: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val data: JsonObject = JsonObject(emptyMap()),
    val syncedAt: Long = 0,
)

object RecordTypes {
    val ALL = setOf(
        "CUSTOMER", "DESIGN", "ORDER", "ORDER_ITEM", "ORDER_COST", "STATUS_CHANGE", "PAYMENT",
        "SHIPMENT", "TRACKING_EVENT", "REMINDER_LOG", "STOCK_BATCH", "STOCK_BATCH_COST", "STOCK_SALE",
    )
}

@Serializable data class PullResponse(val cursor: Long, val records: List<SyncRecord>)
@Serializable data class PushRequest(val records: List<SyncRecord>)
@Serializable data class PushResponse(val accepted: Int, val stale: List<String>, val rejected: List<String>, val cursor: Long)
@Serializable data class LoginRequest(val password: String)
@Serializable data class LoginResponse(val token: String, val expiresAt: Long)
@Serializable data class ErrorResponse(val error: String)
