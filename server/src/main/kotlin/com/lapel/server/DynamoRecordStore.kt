package com.lapel.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest

/**
 * Table layout (see server/template.yaml):
 * pk = record type, sk = record id; GSI "bySync" with pk "gsi1pk" = "ALL" and sort key "syncedAt",
 * so a device can fetch only what changed since its last sync.
 */
class DynamoRecordStore(private val table: String) : RecordStore {

    private val client: DynamoDbClient by lazy {
        DynamoDbClient.builder()
            .httpClient(UrlConnectionHttpClient.create())
            .credentialsProvider(DefaultCredentialsProvider.builder().build())
            .build()
    }

    override fun get(type: String, id: String): SyncRecord? {
        val item = client.getItem(
            GetItemRequest.builder().tableName(table).consistentRead(true)
                .key(mapOf("pk" to s(type), "sk" to s(id))).build(),
        ).item()
        return if (item.isNullOrEmpty()) null else item.toRecord()
    }

    override fun put(record: SyncRecord) {
        client.putItem(
            PutItemRequest.builder().tableName(table).item(
                mapOf(
                    "pk" to s(record.type),
                    "sk" to s(record.id),
                    "updatedAt" to n(record.updatedAt),
                    "deleted" to AttributeValue.fromBool(record.deleted),
                    "data" to s(Json.encodeToString(JsonObject.serializer(), record.data)),
                    "syncedAt" to n(record.syncedAt),
                    "gsi1pk" to s(ALL),
                ),
            ).build(),
        )
    }

    override fun changedSince(syncedAfter: Long): List<SyncRecord> {
        val out = mutableListOf<SyncRecord>()
        var start: Map<String, AttributeValue>? = null
        do {
            val page = client.query(
                QueryRequest.builder().tableName(table).indexName(INDEX)
                    .keyConditionExpression("gsi1pk = :all AND syncedAt >= :since")
                    .expressionAttributeValues(mapOf(":all" to s(ALL), ":since" to n(syncedAfter)))
                    .exclusiveStartKey(start)
                    .build(),
            )
            page.items().mapTo(out) { it.toRecord() }
            start = page.lastEvaluatedKey().takeIf { it.isNotEmpty() }
        } while (start != null)
        return out
    }

    override fun putMetaIfAbsent(key: String, value: String): String {
        try {
            client.putItem(
                PutItemRequest.builder().tableName(table)
                    .item(mapOf("pk" to s(META), "sk" to s(key), "value" to s(value)))
                    .conditionExpression("attribute_not_exists(pk)")
                    .build(),
            )
            return value
        } catch (_: ConditionalCheckFailedException) {
            val item = client.getItem(
                GetItemRequest.builder().tableName(table).consistentRead(true)
                    .key(mapOf("pk" to s(META), "sk" to s(key))).build(),
            ).item()
            return item.getValue("value").s()
        }
    }

    private fun Map<String, AttributeValue>.toRecord() = SyncRecord(
        type = getValue("pk").s(),
        id = getValue("sk").s(),
        updatedAt = getValue("updatedAt").n().toLong(),
        deleted = this["deleted"]?.bool() ?: false,
        data = Json.parseToJsonElement(this["data"]?.s() ?: "{}") as JsonObject,
        syncedAt = getValue("syncedAt").n().toLong(),
    )

    private fun s(v: String) = AttributeValue.fromS(v)
    private fun n(v: Long) = AttributeValue.fromN(v.toString())

    private companion object {
        const val ALL = "ALL"
        const val META = "META"
        const val INDEX = "bySync"
    }
}
