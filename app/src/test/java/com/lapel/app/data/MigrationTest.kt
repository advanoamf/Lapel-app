package com.lapel.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.lapel.app.data.local.LapelDatabase
import com.lapel.app.data.local.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Upgrading the app must keep the owner's existing orders. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LapelDatabase::class.java)

    @Test fun `v1 to v2 keeps orders and adds empty address fields`() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO customers (id, name, phone, email, organization, paymentTermsDays, notes, customerSince, createdAt, updatedAt) " +
                    "VALUES (1, 'Dana', '050-1111111', NULL, 'Branch', 60, NULL, 19000, 0, 0)",
            )
            db.execSQL(
                "INSERT INTO orders (id, customerId, orderNumber, title, orderDate, dueDate, fulfillmentStatus, depositPercent, " +
                    "discountAgorot, deliveryMethod, alibabaOrderNumber, alibabaOrderedOn, supplierName, qualityOk, deliveredAt, " +
                    "completedAt, notes, createdAt, updatedAt) " +
                    "VALUES (1, 1, '170', 'Branch', 20000, NULL, 'DRAFT', 50, 0, 'FEDEX', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0)",
            )
        }

        helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT orderNumber, shippingAddress, addressSentToSupplier FROM orders").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("170", c.getString(0))
                assertTrue(c.isNull(1))
                assertEquals(0, c.getInt(2))
            }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
