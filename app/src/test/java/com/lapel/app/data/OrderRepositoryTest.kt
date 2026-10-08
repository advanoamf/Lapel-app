package com.lapel.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lapel.app.data.local.LapelDatabase
import com.lapel.app.data.local.entity.CustomerEntity
import com.lapel.app.data.local.entity.OrderCostEntity
import com.lapel.app.data.local.entity.OrderEntity
import com.lapel.app.data.local.entity.OrderItemEntity
import com.lapel.app.data.local.entity.PaymentEntity
import com.lapel.app.data.local.entity.ShipmentEntity
import com.lapel.app.data.local.entity.TrackingEventEntity
import com.lapel.app.data.repository.OrderRepository
import com.lapel.domain.model.ChangeSource
import com.lapel.domain.model.CostType
import com.lapel.domain.model.DeliveryMethod
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.Money
import com.lapel.domain.model.PaymentMethod
import com.lapel.domain.model.PaymentMilestone
import com.lapel.domain.model.PaymentStatus
import com.lapel.domain.model.PinType
import com.lapel.domain.model.ShipmentStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrderRepositoryTest {

    private lateinit var db: LapelDatabase
    private lateinit var repo: OrderRepository
    private val now = Instant.parse("2026-03-10T08:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Asia/Jerusalem"))

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LapelDatabase::class.java).allowMainThreadQueries().build()
        repo = OrderRepository(db, clock)
    }

    @After fun tearDown() = db.close()

    private suspend fun newCustomer(terms: Int = 0): Long = db.customerDao().insert(
        CustomerEntity(
            name = "Test customer", phone = "050-0000000", email = null, organization = "Branch",
            paymentTermsDays = terms, notes = null, customerSince = LocalDate.of(2026, 1, 1),
            createdAt = now, updatedAt = now,
        ),
    )

    private suspend fun newOrder(customerId: Long, number: String = "154"): Long = repo.createOrder(
        order = OrderEntity(
            customerId = customerId, orderNumber = number, title = "200 pins", orderDate = LocalDate.of(2026, 3, 1),
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

    private suspend fun pay(orderId: Long, shekels: Long, milestone: PaymentMilestone) = repo.recordPayment(
        PaymentEntity(
            orderId = orderId, stockSaleId = null, amountAgorot = shekels * 100, method = PaymentMethod.BIT,
            milestone = milestone, receivedOn = LocalDate.of(2026, 3, 10), reference = null, createdAt = now,
        ),
    )

    @Test fun `new order gets the default bank fee and correct totals`() = runTest {
        val id = newOrder(newCustomer())
        val summary = db.orderDao().getSummary(id)!!
        assertEquals(300_000L, summary.sellingTotal)
        assertEquals(135_800L, summary.costTotal) // 1350 + 8 bank fee
        val f = summary.financials()
        assertEquals(Money.shekels(1_642), f.netProfit)
        assertEquals(PaymentStatus.UNPAID, f.paymentStatus)
        assertEquals(CostType.BANK_FEE, repo.observeCosts(id).first().last().type)
    }

    @Test fun `deposit, FedEx delivery and balance complete the order`() = runTest {
        val id = newOrder(newCustomer())
        repo.setStatus(id, FulfillmentStatus.ORDERED_FROM_ALIBABA)
        pay(id, 1_500, PaymentMilestone.DEPOSIT)
        assertEquals(PaymentStatus.DEPOSIT_PAID, db.orderDao().getSummary(id)!!.financials().paymentStatus)

        db.shipmentDao().insert(
            ShipmentEntity(
                orderId = id, trackingNumber = "794612345678", status = ShipmentStatus.DELIVERED,
                statusDescription = "Delivered", lastEventAt = now, shippedAt = now, deliveredAt = now,
                estimatedDelivery = null, receivedBy = null, lastSyncedAt = now, syncError = null,
                trackingActive = false, createdAt = now,
            ),
        )
        db.withTransactionForTest { repo.reconcile(id) }
        val delivered = db.orderDao().getOrder(id)!!
        assertEquals(FulfillmentStatus.DELIVERED, delivered.fulfillmentStatus)
        assertNotNull(delivered.deliveredAt)
        assertEquals(Money.shekels(1_500), db.orderDao().getSummary(id)!!.financials().dueNow)

        pay(id, 1_500, PaymentMilestone.BALANCE)
        assertEquals(FulfillmentStatus.COMPLETED, db.orderDao().getOrder(id)!!.fulfillmentStatus)

        val history = repo.observeStatusChanges(id).first()
        assertEquals(
            listOf(FulfillmentStatus.DRAFT, FulfillmentStatus.ORDERED_FROM_ALIBABA, FulfillmentStatus.DELIVERED, FulfillmentStatus.COMPLETED),
            history.map { it.toStatus },
        )
        assertEquals(ChangeSource.FEDEX_SYNC, history[2].source)
        assertEquals(ChangeSource.SYSTEM, history[3].source)
    }

    @Test fun `duplicate tracking events are ignored`() = runTest {
        val id = newOrder(newCustomer())
        val shipmentId = db.shipmentDao().insert(
            ShipmentEntity(
                orderId = id, trackingNumber = "794612345679", status = ShipmentStatus.IN_TRANSIT,
                statusDescription = null, lastEventAt = null, shippedAt = null, deliveredAt = null,
                estimatedDelivery = null, receivedBy = null, lastSyncedAt = null, syncError = null, createdAt = now,
            ),
        )
        val event = TrackingEventEntity(shipmentId = shipmentId, occurredAt = now, eventCode = "PU", description = "Picked up", location = "SHENZHEN, CN")
        db.shipmentDao().insertEvents(listOf(event))
        db.shipmentDao().insertEvents(listOf(event))
        assertEquals(1, db.shipmentDao().observeEvents(shipmentId).first().size)
    }

    @Test fun `next order number skips suffixed numbers`() = runTest {
        val customer = newCustomer()
        newOrder(customer, "153")
        newOrder(customer, "91D")
        newOrder(customer, "102-148")
        assertEquals("154", repo.nextOrderNumber())
    }

    @Test fun `moving an imported delivered order back to ordered clears the delivery date`() = runTest {
        val id = newOrder(newCustomer())
        repo.setStatus(id, FulfillmentStatus.DELIVERED)
        assertNotNull(db.orderDao().getOrder(id)!!.deliveredAt)
        repo.setStatus(id, FulfillmentStatus.ORDERED_FROM_ALIBABA)
        val order = db.orderDao().getOrder(id)!!
        assertEquals(FulfillmentStatus.ORDERED_FROM_ALIBABA, order.fulfillmentStatus)
        assertEquals(null, order.deliveredAt)
        assertNotNull(order.alibabaOrderedOn)
    }

    @Test fun `shipping address and sent-to-manufacturer flag reach the summary`() = runTest {
        val id = newOrder(newCustomer())
        assertEquals(false, db.orderDao().getSummary(id)!!.hasAddress)
        val order = db.orderDao().getOrder(id)!!
        repo.updateOrder(order.copy(shippingAddress = "  "), emptyList())
        assertEquals(false, db.orderDao().getSummary(id)!!.hasAddress) // blank does not count
        repo.updateOrder(order.copy(shippingAddress = "Herzl 1, Tel Aviv"), emptyList())
        repo.setAddressSent(id, true)
        val summary = db.orderDao().getSummary(id)!!
        assertEquals(true, summary.hasAddress)
        assertEquals(true, summary.addressSentToSupplier)
    }
}
