package com.lapel.domain.importer

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test

/**
 * Manual check against the owner's real workbook (never committed):
 * LAPEL_XLSX=/path/to/file.xlsx ./gradlew -p domain test --tests '*RealWorkbookReport*' -i
 */
class RealWorkbookReport {
    @Test fun `print import report`() {
        val path = System.getenv("LAPEL_XLSX")
        assumeTrue(path != null && File(path).exists())
        val result = SpreadsheetImporter().import(File(path!!).inputStream().use(XlsxReader::read))

        println("customers=${result.customers.size} orders=${result.orders.size} stock=${result.stockBatches.size}")
        println("revenue=${result.orders.fold(com.lapel.domain.model.Money.ZERO) { a, o -> a + o.sellingTotal }}")
        println("profit=${result.orders.fold(com.lapel.domain.model.Money.ZERO) { a, o -> a + o.sellingTotal - o.costTotal }}")
        println("OUTSTANDING total=${result.totalOutstanding}")
        result.outstandingOrders.forEach { println("  #${it.orderNumber} row ${it.sheetRow}: sold ${it.sellingTotal} paid ${it.paidTotal} owes ${it.outstanding}") }
        result.stockBatches.forEach { b ->
            println("STOCK ${b.designName}: received ${b.quantityReceived}, sold ${b.sales.sumOf { it.quantity }}, costs ${b.costs.fold(com.lapel.domain.model.Money.ZERO) { a, c -> a + c.amount }}")
            b.sales.filter { it.outstanding.isPositive }.forEach { println("    row ${it.sheetRow} owes ${it.outstanding}") }
        }
        result.warnings.forEach { println("WARN ${it.sheet}:${it.row} ${it.message}") }
        result.orders.take(3).forEach { println(it) }
        println(result.customers.filter { it.paymentTermsDays > 0 }.map { it.name to it.paymentTermsDays })
    }
}
