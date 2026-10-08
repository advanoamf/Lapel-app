package com.lapel.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lapel.app.data.sync.SyncTables

/** v2: shipping address per order and whether it was passed to the manufacturer. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE orders ADD COLUMN shippingAddress TEXT")
        db.execSQL("ALTER TABLE orders ADD COLUMN addressSentToSupplier INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v3: sync with the server. Every synced row gets a uid and change-tracking columns; existing rows
 * are marked as changed so the first sync uploads everything already on the phone.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        SyncTables.ALL.forEach { t ->
            db.execSQL("ALTER TABLE ${t.name} ADD COLUMN uid TEXT")
            db.execSQL("ALTER TABLE ${t.name} ADD COLUMN syncUpdatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE ${t.name} ADD COLUMN dirty INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE ${t.name} SET uid = lower(hex(randomblob(16))), dirty = 1, syncUpdatedAt = 1")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_${t.name}_uid ON ${t.name} (uid)")
        }
        SyncTables.install(db)
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
