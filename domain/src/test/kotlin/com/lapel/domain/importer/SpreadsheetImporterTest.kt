package com.lapel.domain.importer

import com.lapel.domain.importer.TestXlsx.C
import com.lapel.domain.model.CostType
import com.lapel.domain.model.DeliveryMethod
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpreadsheetImporterTest {

    private fun ils(v: Double) = SpreadsheetImporter.money(v)

    private fun orderRow(
        row: Int, number: String, name: String, phone: String?, branch: String,
        unitsOrdered: Int, unitUsd: Double, moldUsd: Double, shipUsd: Double, rate: Double?,
        sold: Int, price: Double, customs: Double = 0.0, referral: Double = 0.0,
        costFormula: String = "=K$row+(H$row+G$row+(F$row*E$row))*S$row", revenueFormula: String = "=N$row*M$row",
        revenueValue: Double = sold * price,
    ): Map<String, C> = buildMap {
        put("A$row", C(number)); put("B$row", C(name)); phone?.let { put("C$row", C(it)) }; put("D$row", C(branch))
        put("E$row", C(unitsOrdered)); put("F$row", C(unitUsd)); put("G$row", C(moldUsd)); put("H$row", C(shipUsd))
        put("I$row", C("Supplier")); put("J$row", C(customs)); put("K$row", C(referral)); put("L$row", C(0, costFormula))
        put("M$row", C(sold)); put("N$row", C(price)); put("O$row", C(revenueValue, revenueFormula))
        rate?.let { put("R$row", C(it)) }
    }

    private fun workbook(): XlsxWorkbook {
        val orders = orderRow(4, "001", "Dana", "050-1111111", "Branch A", 73, 0.3, 37.5, 30.0, 3.41, 70, 9.0, customs = 50.0) +
            orderRow(5, "002", "Dana", "050-111-1111", "Branch B", 30, 0.5, 40.0, 29.0, null, 30, 19.0,
                costFormula = "=K5+(H5+G5+(F5*E5))*S99+8", revenueFormula = "=N5*M5-90", revenueValue = 480.0) +
            // quantity and price typed in the wrong columns
            orderRow(6, "95", "Noa", null, "Branch C", 355, 0.34, 40.0, 80.0, 3.84, 7, 350.0, revenueValue = 2450.0) +
            mapOf("A7" to C("42H"), "B7" to C("Empty"), "O7" to C(0, "=N7*M7")) +
            // stock design whose sales live on their own sheet
            orderRow(8, "89", "Stock pin", null, "", 700, 0.36, 46.0, 103.0, 3.84, 0, 9.0).minus("D8") +
            mapOf("M8" to C(3, "='Stock pin'!H10"))

        val payments = mapOf(
            "F4" to C("31.7.22"), "G4" to C("כן"), "H4" to C(315), "I4" to C(315), "L4" to C("עצמי"),
            "F5" to C("16.8.22"), "H5" to C(240), "K5" to C("שוטף +60"), "L5" to C("fedex"),
            "F6" to C("1.9.22"),
            "H8" to C(2646),
        )

        val stock = mapOf(
            "C2" to C("שם"), "D2" to C("טלפון"), "E2" to C("סניף"), "F2" to C("תאריך הזמנה"),
            "H2" to C("מס' יחידות"), "I2" to C("תשלום"), "N2" to C("הגיע?"), "O2" to C("מספר\nמשלוח"),
            "K3" to C("יתרה לתשלום"),
            "C4" to C("Lia"), "D4" to C("050-2222222"), "F4" to C("14.1.25"), "H4" to C(40), "I4" to C(200), "J4" to C(200),
            "K4" to C(20, "=(H4*10)-I4-J4+IF(G4,20,0)"), "N4" to C(true), "O4" to C("RR0001485077P"),
            "E5" to C("Branch D"), "H5" to C(77), "I5" to C(300), "J5" to C(400), "K5" to C(0, "=(H5*10)-I5-J5-70"), "N5" to C(true),
            "D10" to C("סה\"כ"), "H10" to C(117, "=SUM(H4:H5)"),
        )

        val bytes = TestXlsx.build(
            mapOf(SpreadsheetImporter.ORDERS_SHEET to orders, SpreadsheetImporter.PAYMENTS_SHEET to payments, "Stock pin" to stock, "Notes" to mapOf("A1" to C("shared"))),
        )
        return XlsxReader.read(bytes.inputStream())
    }

    private val result by lazy { SpreadsheetImporter().import(workbook()) }

    @Test fun `costs use the row's own rate plus 3 percent and include customs`() {
        val o = result.orders.first { it.orderNumber == "001" }
        val alibaba = o.costs.single { it.type == CostType.ALIBABA_PAYMENT }
        assertEquals(ils((73 * 0.3 + 37.5 + 30) * 3.41 * 1.03), alibaba.amount)
        assertEquals(ils(50.0), o.costs.single { it.type == CostType.CUSTOMS }.amount)
        assertEquals(LocalDate.of(2022, 7, 31), o.orderDate)
        assertEquals(FulfillmentStatus.COMPLETED, o.status)
        assertEquals(DeliveryMethod.SELF_PICKUP, o.deliveryMethod)
        assertEquals(true, o.qualityOk)
    }

    @Test fun `missing rate uses default, plus 8 becomes bank fee, minus 90 becomes discount`() {
        val o = result.orders.first { it.orderNumber == "002" }
        assertEquals(ils((30 * 0.5 + 40 + 29) * 3.84 * 1.03), o.costs.single { it.type == CostType.ALIBABA_PAYMENT }.amount)
        assertEquals(Money.shekels(8), o.costs.single { it.type == CostType.BANK_FEE }.amount)
        assertEquals(Money.shekels(90), o.discount)
        assertEquals(Money.shekels(480), o.sellingTotal)
        assertEquals(Money.shekels(240), o.outstanding)
        assertEquals(FulfillmentStatus.DELIVERED, o.status)
        assertTrue(result.warnings.any { it.message.contains("3.84") })
    }

    @Test fun `repeat customer is merged by phone and keeps payment terms`() {
        val dana = result.customers.filter { it.name == "Dana" }
        assertEquals(1, dana.size)
        assertEquals(60, dana.single().paymentTermsDays)
        assertEquals(LocalDate.of(2022, 7, 31), dana.single().firstSeen)
    }

    @Test fun `swapped quantity and price are fixed`() {
        val o = result.orders.first { it.orderNumber == "95" }
        assertEquals(350, o.items.single().quantitySold)
        assertEquals(Money.shekels(7), o.items.single().unitPrice)
        assertTrue(o.notes!!.contains("3.84").not()) // row has its own rate
    }

    @Test fun `row without quantity is skipped with a warning`() {
        assertTrue(result.orders.none { it.orderNumber == "42H" })
        assertTrue(result.warnings.any { it.message.contains("42H") })
    }

    @Test fun `stock design becomes a batch with sales and stops at the totals row`() {
        val batch = result.stockBatches.single()
        assertEquals("Stock pin", batch.designName)
        assertEquals(700, batch.quantityReceived)
        assertEquals(2, batch.sales.size)

        val lia = batch.sales[0]
        assertEquals(Money.shekels(10), lia.unitPrice)
        assertEquals(Money.shekels(20), lia.shippingCharged) // balance 20 + paid 400 − 40×10
        assertEquals(Money.shekels(20), lia.outstanding)
        assertEquals("RR0001485077P", lia.trackingNumber)

        val unnamed = batch.sales[1]
        assertEquals(Money.shekels(70), unnamed.discount)
        assertEquals(Money.ZERO, unnamed.outstanding)
        assertEquals("Branch D", result.customers.first { it.key == unnamed.customerKey }.name)

        // Main-sheet payments for the stock row are not double counted.
        assertTrue(result.orders.none { it.orderNumber == "89" })
        assertTrue(result.warnings.any { it.message.contains("2646") })
    }

    @Test fun `unused sheets are listed`() {
        assertTrue(result.warnings.any { it.message.contains("Notes") })
    }

    @Test fun `formula helpers`() {
        assertEquals(8.0, SpreadsheetImporter.trailingConstant("=K4+(H4+G4)*S4+8"))
        assertEquals(-90.0, SpreadsheetImporter.trailingConstant("=N15*M15-90"))
        assertNull(SpreadsheetImporter.trailingConstant("=L22+L29+L37"))
        assertNull(SpreadsheetImporter.trailingConstant("=K4+(H4+G4)*S4"))
        assertEquals("שרוליק 1.4.23", SpreadsheetImporter.referencedSheet("='שרוליק 1.4.23'!F32"))
        assertEquals(12.0, SpreadsheetImporter.unitPriceFrom("=(F23*12)-G23+18-H23"))
        assertEquals(LocalDate.of(2025, 7, 9), SpreadsheetImporter.parseDate("9.7.25"))
        assertNull(SpreadsheetImporter.parseDate("לבדוק"))
    }
}
