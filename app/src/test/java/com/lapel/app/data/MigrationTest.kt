package com.lapel.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lapel.app.data.local.ALL_MIGRATIONS
import com.lapel.app.data.local.LapelDatabase
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Upgrading the app must keep the owner's existing orders: build a database exactly as
 * version 1 created it (from the exported schema), then open it with the current app code.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun createVersion(version: Int, name: String, orderColumns: String = "", orderValues: String = "") {
        val schema = JSONObject(File("schemas/com.lapel.app.data.local.LapelDatabase/$version.json").readText())
            .getJSONObject("database")
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL(
                "INSERT INTO customers (id, name, phone, email, organization, paymentTermsDays, notes, customerSince, createdAt, updatedAt) " +
                    "VALUES (1, 'Dana', '050-1111111', NULL, 'Branch', 60, NULL, 19000, 0, 0)",
            )
            db.execSQL(
                "INSERT INTO orders (id, customerId, orderNumber, title, orderDate, dueDate, fulfillmentStatus, depositPercent, " +
                    "discountAgorot, deliveryMethod, alibabaOrderNumber, alibabaOrderedOn, supplierName, qualityOk, deliveredAt, " +
                    "completedAt, notes, createdAt, updatedAt$orderColumns) " +
                    "VALUES (1, 1, '170', 'Branch', 20000, NULL, 'DRAFT', 50, 0, 'FEDEX', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0$orderValues)",
            )
            db.version = version
        }
    }

    @Test fun `upgrade from version 1 keeps orders and adds empty address fields`() = runTest {
        val name = "migration-test.db"
        createVersion(1, name)

        val db = Room.databaseBuilder(context, LapelDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            // Opening runs the migration and Room checks the result matches the current schema.
            val summary = db.orderDao().getSummary(1)!!
            assertEquals("170", summary.orderNumber)
            assertEquals(60, summary.paymentTermsDays)
            assertEquals(false, summary.hasAddress)
            assertEquals(false, summary.addressSentToSupplier)
            assertEquals(3, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    @Test fun `upgrade from version 2 marks every existing row for the first upload and tracks new changes`() = runTest {
        val name = "migration-test-2.db"
        createVersion(2, name, ", shippingAddress, addressSentToSupplier", ", 'Herzl 1, Tel Aviv', 1")

        val db = Room.databaseBuilder(context, LapelDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val summary = db.orderDao().getSummary(1)!!
            assertEquals(true, summary.hasAddress)
            assertEquals(true, summary.addressSentToSupplier)

            val sql = db.openHelper.writableDatabase
            sql.query("SELECT uid, dirty FROM orders WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(32, c.getString(0).length)
                assertEquals(1, c.getInt(1))
            }
            val customerUid = sql.query("SELECT uid FROM customers WHERE id = 1").use { c -> c.moveToFirst(); c.getString(0) }
            assertNotEquals(customerUid, sql.query("SELECT uid FROM orders WHERE id = 1").use { c -> c.moveToFirst(); c.getString(0) })

            // The triggers installed by the migration track new edits and deletions.
            sql.execSQL("UPDATE customers SET dirty = 0")
            val customer = db.customerDao().get(1)!!
            db.customerDao().update(customer.copy(name = "Dana B"))
            sql.query("SELECT dirty, uid FROM customers WHERE id = 1").use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
                assertEquals(customerUid, c.getString(1))
            }
            db.orderDao().deleteOrder(db.orderDao().getOrder(1)!!)
            sql.query("SELECT type FROM sync_tombstones").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("ORDER", c.getString(0))
            }
        } finally {
            db.close()
        }
    }
}
