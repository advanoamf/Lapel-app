package com.lapel.app.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lapel.app.data.local.LapelDatabase
import com.lapel.app.data.local.entity.CustomerEntity
import com.lapel.app.data.local.entity.OrderCostEntity
import com.lapel.app.data.local.entity.OrderEntity
import com.lapel.app.data.local.entity.OrderItemEntity
import com.lapel.app.data.local.entity.PaymentEntity
import com.lapel.app.data.repository.OrderRepository
import com.lapel.domain.model.CostType
import com.lapel.domain.model.DeliveryMethod
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.PaymentMethod
import com.lapel.domain.model.PaymentMilestone
import com.lapel.domain.model.PinType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Behaves like server/SyncService: newest edit wins, pulls return everything changed since the cursor. */
private class FakeServer : SyncApi {
    val records = linkedMapOf<String, RemoteRecord>()
    private var now = 1_000_000L

    override suspend fun login(server: String, password: String) = LoginResult("token", Long.MAX_VALUE)

    override suspend fun pull(server: String, token: String, since: Long): PullResult {
        val cursor = ++now
        return PullResult(cursor, records.values.filter { it.syncedAt >= since }.sortedBy { it.syncedAt })
    }

    override suspend fun push(server: String, token: String, records: List<RemoteRecord>): PushResult {
        var accepted = 0
        val stale = mutableListOf<String>()
        records.forEach { r ->
            val key = "${r.type}/${r.id}"
            val existing = this.records[key]
            if (existing != null && existing.updatedAt >= r.updatedAt) {
                stale += key
            } else {
                this.records[key] = r.copy(syncedAt = ++now)
                accepted++
            }
        }
        return PushResult(accepted, stale, emptyList(), now)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncEngineTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-03-10T08:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Asia/Jerusalem"))
    private val server = FakeServer()
    private val settings = SyncSettings(context).apply { saveLogin("https://example.invalid/", "token", Long.MAX_VALUE) }

    private lateinit var phone: LapelDatabase
    private lateinit var computer: LapelDatabase
    private lateinit var phoneSync: SyncEngine
    private lateinit var computerSync: SyncEngine

    private fun newDb() = Room.inMemoryDatabaseBuilder(context, LapelDatabase::class.java)
        .addCallback(SyncTables.onCreate)
        .allowMainThreadQueries()
        .build()

    @Before fun setUp() {
        phone = newDb()
        computer = newDb()
        phoneSync = SyncEngine(phone, server, settings, clock)
        computerSync = SyncEngine(computer, server, settings, clock)
    }

    @After fun tearDown() {
        phone.close()
        computer.close()
    }

    /** Trigger timestamps are wall-clock ms; make sure the next edit is strictly newer. */
    private fun tick() = Thread.sleep(5)

    private suspend fun createOrder(db: LapelDatabase): Long {
        val customerId = db.customerDao().insert(
            CustomerEntity(
                name = "Dana", phone = "050-0000000", email = null, organization = "Branch", paymentTermsDays = 60,
                notes = null, customerSince = LocalDate.of(2026, 1, 1), createdAt = now, updatedAt = now,
            ),
        )
        val repo = OrderRepository(db, clock)
        val orderId = repo.createOrder(
            order = OrderEntity(
                customerId = customerId, orderNumber = "170", title = "200 pins", orderDate = LocalDate.of(2026, 3, 1),
                dueDate = null, fulfillmentStatus = FulfillmentStatus.DRAFT, deliveryMethod = DeliveryMethod.FEDEX,
                alibabaOrderNumber = null, alibabaOrderedOn = null, supplierName = "Supplier", qualityOk = null,
                deliveredAt = null, completedAt = null, notes = null, createdAt = now, updatedAt = now,
            ),
            items = listOf(
                OrderItemEntity(
                    orderId = 0, designId = null, designName = "Logo", pinType = PinType.SOFT_ENAMEL, sizeMm = 25,
                    plating = "gold", quantityOrdered = 205, quantitySold = 200, unitPriceAgorot = 1_500, artworkUri = null,
                ),
            ),
            costs = listOf(OrderCostEntity(orderId = 0, type = CostType.ALIBABA_PAYMENT, amountAgorot = 135_000, note = null)),
        )
        repo.recordPayment(
            PaymentEntity(
                orderId = orderId, stockSaleId = null, amountAgorot = 150_000, method = PaymentMethod.BIT,
                milestone = PaymentMilestone.DEPOSIT, receivedOn = LocalDate.of(2026, 3, 10), reference = null, createdAt = now,
            ),
        )
        return orderId
    }

    private fun onlyOrderId(db: LapelDatabase): Long =
        db.openHelper.readableDatabase.query("SELECT id FROM orders").use { c -> assertTrue(c.moveToFirst()); c.getLong(0) }

    @Test fun `an order made on the phone arrives on the other device with its links intact`() = runTest {
        createOrder(phone)
        assertTrue(phoneSync.pendingChanges() > 0)
        phoneSync.sync()
        assertEquals(0, phoneSync.pendingChanges())

        computerSync.sync()
        val summary = computer.orderDao().getSummary(onlyOrderId(computer))!!
        assertEquals("170", summary.orderNumber)
        assertEquals(60, summary.paymentTermsDays)
        assertEquals(300_000L, summary.sellingTotal)
        assertEquals(135_800L, summary.costTotal) // goods + bank fee
        assertEquals(150_000L, summary.paidTotal)
        // Applying remote records is not a local change, so nothing bounces back.
        assertEquals(0, computerSync.pendingChanges())
    }

    @Test fun `the newer edit wins and deletions reach the other device`() = runTest {
        createOrder(phone)
        phoneSync.sync()
        computerSync.sync()

        tick()
        val repo = OrderRepository(computer, clock)
        repo.setAddressSent(onlyOrderId(computer), true)
        computerSync.sync()
        phoneSync.sync()
        assertEquals(true, phone.orderDao().getOrder(onlyOrderId(phone))!!.addressSentToSupplier)

        val phoneOrder = phone.orderDao().getOrder(onlyOrderId(phone))!!
        assertEquals(0, phoneSync.pendingChanges())

        tick()
        phone.orderDao().deleteOrder(phoneOrder)
        phoneSync.sync()
        computerSync.sync()
        assertEquals(0, count(computer, "orders"))
        assertEquals(0, count(computer, "payments"))
        assertEquals(1, count(computer, "customers"))
    }

    @Test fun `a server copy older than the local edit is ignored`() = runTest {
        createOrder(phone)
        phoneSync.sync()
        computerSync.sync()

        tick()
        OrderRepository(computer, clock).setAddressSent(onlyOrderId(computer), true)
        tick()
        OrderRepository(phone, clock).setAddressSent(onlyOrderId(phone), false)
        phone.openHelper.writableDatabase.execSQL("UPDATE orders SET notes = 'phone edit'")

        computerSync.sync() // older edit reaches the server first
        phoneSync.sync() // newer edit replaces it; the older copy is not applied here
        computerSync.sync()
        assertEquals("phone edit", computer.orderDao().getOrder(onlyOrderId(computer))!!.notes)
        assertEquals("phone edit", phone.orderDao().getOrder(onlyOrderId(phone))!!.notes)
    }

    private fun count(db: LapelDatabase, table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { c -> c.moveToFirst(); c.getInt(0) }
}
