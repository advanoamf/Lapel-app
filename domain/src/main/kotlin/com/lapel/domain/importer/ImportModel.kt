package com.lapel.domain.importer

import com.lapel.domain.model.CostType
import com.lapel.domain.model.DeliveryMethod
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.Money
import com.lapel.domain.model.PaymentMilestone
import com.lapel.domain.model.sum
import java.time.LocalDate

data class ImportedCustomer(
    /** Phone digits when known, otherwise name + branch; used to merge repeat customers. */
    val key: String,
    val name: String,
    val phone: String?,
    val organization: String?,
    val paymentTermsDays: Int,
    val firstSeen: LocalDate?,
)

data class ImportedItem(
    val designName: String,
    val quantityOrdered: Int,
    val quantitySold: Int,
    val unitPrice: Money,
)

data class ImportedCost(val type: CostType, val amount: Money, val note: String? = null)

data class ImportedPayment(val amount: Money, val milestone: PaymentMilestone, val date: LocalDate?)

data class ImportedOrder(
    val sheetRow: Int,
    val orderNumber: String,
    val customerKey: String,
    val title: String,
    val orderDate: LocalDate,
    val orderDateEstimated: Boolean,
    val status: FulfillmentStatus,
    val deliveryMethod: DeliveryMethod,
    val qualityOk: Boolean?,
    val supplier: String?,
    val items: List<ImportedItem>,
    val costs: List<ImportedCost>,
    val discount: Money,
    val payments: List<ImportedPayment>,
    val notes: String?,
) {
    val sellingTotal: Money get() = items.map { it.unitPrice * it.quantitySold }.sum() - discount
    val paidTotal: Money get() = payments.map { it.amount }.sum()
    val costTotal: Money get() = costs.map { it.amount }.sum()
    val outstanding: Money get() = (sellingTotal - paidTotal).coerceAtLeastZero()
}

data class ImportedStockSale(
    val sheetRow: Int,
    val customerKey: String,
    val date: LocalDate?,
    val quantity: Int,
    val unitPrice: Money,
    val shippingCharged: Money,
    /** Price reduction agreed with this buyer. */
    val discount: Money,
    val payments: List<ImportedPayment>,
    val sent: Boolean,
    val arrived: Boolean,
    val trackingNumber: String?,
    val notes: String?,
) {
    val total: Money get() = unitPrice * quantity + shippingCharged - discount
    val outstanding: Money get() = (total - payments.map { it.amount }.sum()).coerceAtLeastZero()
}

data class ImportedStockBatch(
    val sheetRow: Int,
    val orderNumber: String,
    val designName: String,
    val quantityReceived: Int,
    val purchasedOn: LocalDate,
    val supplier: String?,
    val costs: List<ImportedCost>,
    val sales: List<ImportedStockSale>,
)

data class ImportWarning(val sheet: String, val row: Int, val message: String)

data class SpreadsheetImport(
    val customers: List<ImportedCustomer>,
    val orders: List<ImportedOrder>,
    val stockBatches: List<ImportedStockBatch>,
    val warnings: List<ImportWarning>,
) {
    val outstandingOrders: List<ImportedOrder> get() = orders.filter { it.outstanding.isPositive }
    val totalOutstanding: Money
        get() = orders.map { it.outstanding }.sum() +
            stockBatches.flatMap { it.sales }.map { it.outstanding }.sum()
}
