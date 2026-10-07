package com.lapel.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lapel.app.data.local.LapelDatabase
import com.lapel.app.data.local.dao.outstandingAgorot
import com.lapel.app.data.local.dao.remaining
import com.lapel.app.data.repository.ImportRepository
import com.lapel.domain.importer.ImportedCost
import com.lapel.domain.importer.ImportedCustomer
import com.lapel.domain.importer.ImportedItem
import com.lapel.domain.importer.ImportedOrder
import com.lapel.domain.importer.ImportedPayment
import com.lapel.domain.importer.ImportedStockBatch
import com.lapel.domain.importer.ImportedStockSale
import com.lapel.domain.importer.SpreadsheetImport
import com.lapel.domain.model.CostType
import com.lapel.domain.model.DeliveryMethod
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.Money
import com.lapel.domain.model.PaymentMilestone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportRepositoryTest {

    private lateinit var db: LapelDatabase
    private lateinit var repo: ImportRepository
    private val clock = Clock.fixed(Instant.parse("2026-03-10T08:00:00Z"), ZoneId.of("Asia/Jerusalem"))

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LapelDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = ImportRepository(db, clock)
    }

    @After fun tearDown() = db.close()

    private val day = LocalDate.of(2025, 1, 14)

    private val data = SpreadsheetImport(
        customers = listOf(
            ImportedCustomer("0501111111", "Dana", "050-1111111", "Branch A", 60, LocalDate.of(2022, 7, 31)),
            ImportedCustomer("0502222222", "Lia", "050-2222222", null, 0, day),
        ),
        orders = listOf(
            ImportedOrder(
                sheetRow = 69, orderNumber = "98", customerKey = "0501111111", title = "Branch A", orderDate = day,
                orderDateEstimated = false, status = FulfillmentStatus.DELIVERED, deliveryMethod = DeliveryMethod.FEDEX,
                qualityOk = true, supplier = "Supplier",
                items = listOf(ImportedItem("Branch A", 62, 60, Money.shekels(12))),
                costs = listOf(ImportedCost(CostType.ALIBABA_PAYMENT, Money.shekels(300)), ImportedCost(CostType.BANK_FEE, Money.shekels(8))),
                discount = Money.ZERO,
                payments = listOf(ImportedPayment(Money.shekels(360), PaymentMilestone.DEPOSIT, day)),
                notes = "יובא מהגיליון (שורה 69)",
            ),
        ),
        stockBatches = listOf(
            ImportedStockBatch(
                sheetRow = 56, orderNumber = "89", designName = "Stock pin", quantityReceived = 700, purchasedOn = day,
                supplier = "Supplier", costs = listOf(ImportedCost(CostType.ALIBABA_PAYMENT, Money.shekels(1_900))),
                sales = listOf(
                    ImportedStockSale(4, "0502222222", day, 40, Money.shekels(10), Money.shekels(20), Money.ZERO,
                        listOf(ImportedPayment(Money.shekels(200), PaymentMilestone.DEPOSIT, day)), true, true, "RR0001485077P", null),
                ),
            ),
        ),
        warnings = emptyList(),
    )

    @Test fun `import writes customers, orders, payments and stock in one go`() = runTest {
        assertFalse(repo.hasOrders())
        val counts = repo.save(data)
        assertEquals(2, counts.customers)
        assertEquals(1, counts.orders)
        assertEquals(1, counts.stockSales)
        assertEquals(2, counts.payments)
        assertTrue(repo.hasOrders())

        val summary = db.orderDao().observeSummaries().first().single()
        assertEquals("98", summary.orderNumber)
        assertEquals(60, summary.paymentTermsDays)
        val f = summary.financials()
        assertEquals(Money.shekels(720), f.sellingTotal)
        assertEquals(Money.shekels(360), f.outstanding)
        assertEquals(Money.shekels(412), f.netProfit) // 720 − 300 − 8
        assertEquals(FulfillmentStatus.DELIVERED, summary.fulfillmentStatus)

        val stock = db.stockDao().observeBatchSummaries().first().single()
        assertEquals(660, stock.remaining)
        assertEquals(42_000L, stock.revenue) // 40 × ₪10 + ₪20 postage
        assertEquals(22_000L, stock.outstandingAgorot)
    }
}
