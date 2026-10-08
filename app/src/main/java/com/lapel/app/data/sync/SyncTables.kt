package com.lapel.app.data.sync

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** A local table that is synced with the server. */
data class SyncTable(
    val name: String,
    /** Record type on the server. */
    val type: String,
    /** Foreign-key columns → referenced table; sent as the referenced row's uid. */
    val refs: Map<String, String> = emptyMap(),
    /** References that may point at a row that does not exist (yet); resolved to null instead of waiting. */
    val softRefs: Set<String> = emptySet(),
)

/**
 * Which tables sync, in dependency order (parents first). Change tracking is done by SQLite
 * triggers, so app code never has to remember to mark anything as changed.
 */
object SyncTables {
    val ALL = listOf(
        SyncTable("customers", "CUSTOMER"),
        SyncTable("designs", "DESIGN", mapOf("moldPaidOnOrderId" to "orders"), softRefs = setOf("moldPaidOnOrderId")),
        SyncTable("orders", "ORDER", mapOf("customerId" to "customers")),
        SyncTable("order_items", "ORDER_ITEM", mapOf("orderId" to "orders", "designId" to "designs")),
        SyncTable("order_costs", "ORDER_COST", mapOf("orderId" to "orders")),
        SyncTable("status_changes", "STATUS_CHANGE", mapOf("orderId" to "orders")),
        SyncTable("shipments", "SHIPMENT", mapOf("orderId" to "orders")),
        SyncTable("tracking_events", "TRACKING_EVENT", mapOf("shipmentId" to "shipments")),
        SyncTable("stock_batches", "STOCK_BATCH", mapOf("designId" to "designs")),
        SyncTable("stock_batch_costs", "STOCK_BATCH_COST", mapOf("batchId" to "stock_batches")),
        SyncTable("stock_sales", "STOCK_SALE", mapOf("batchId" to "stock_batches", "customerId" to "customers")),
        SyncTable("payments", "PAYMENT", mapOf("orderId" to "orders", "stockSaleId" to "stock_sales")),
    )

    val byType = ALL.associateBy { it.type }

    /** Columns that belong to the sync machinery, not to the record's data. */
    val LOCAL_COLUMNS = setOf("id", "uid", "syncUpdatedAt", "dirty")

    private const val NOW_MS = "CAST(ROUND((julianday('now') - 2440587.5) * 86400000) AS INTEGER)"
    private const val NOT_APPLYING = "(SELECT applying FROM sync_state WHERE id = 1) = 0"

    // Android's SQLite runs with recursive triggers on, so the trigger's own UPDATE would fire it
    // again; it sets the same flag the sync engine uses while it writes.
    private const val GUARD_ON = "UPDATE sync_state SET applying = 1 WHERE id = 1;"
    private const val GUARD_OFF = "UPDATE sync_state SET applying = 0 WHERE id = 1;"

    /** Creates the sync bookkeeping tables and change-tracking triggers. Safe to run repeatedly. */
    fun install(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS sync_state (id INTEGER PRIMARY KEY CHECK (id = 1), " +
                "applying INTEGER NOT NULL DEFAULT 0, cursor INTEGER NOT NULL DEFAULT 0)",
        )
        db.execSQL("INSERT OR IGNORE INTO sync_state (id, applying, cursor) VALUES (1, 0, 0)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS sync_tombstones (type TEXT NOT NULL, uid TEXT NOT NULL, " +
                "deletedAt INTEGER NOT NULL, PRIMARY KEY (type, uid))",
        )
        ALL.forEach { t ->
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS sync_ins_${t.name} AFTER INSERT ON ${t.name} WHEN $NOT_APPLYING BEGIN $GUARD_ON " +
                    "UPDATE ${t.name} SET uid = COALESCE(NEW.uid, lower(hex(randomblob(16)))), dirty = 1, " +
                    "syncUpdatedAt = $NOW_MS WHERE id = NEW.id; $GUARD_OFF END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS sync_upd_${t.name} AFTER UPDATE ON ${t.name} WHEN $NOT_APPLYING BEGIN $GUARD_ON " +
                    "UPDATE ${t.name} SET uid = COALESCE(NEW.uid, OLD.uid), dirty = 1, " +
                    "syncUpdatedAt = $NOW_MS WHERE id = NEW.id; $GUARD_OFF END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS sync_del_${t.name} AFTER DELETE ON ${t.name} " +
                    "WHEN $NOT_APPLYING AND OLD.uid IS NOT NULL BEGIN " +
                    "INSERT OR REPLACE INTO sync_tombstones (type, uid, deletedAt) VALUES ('${t.type}', OLD.uid, $NOW_MS); END",
            )
        }
    }

    /** Installs triggers on a freshly created database. */
    val onCreate = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) = install(db)
    }
}
