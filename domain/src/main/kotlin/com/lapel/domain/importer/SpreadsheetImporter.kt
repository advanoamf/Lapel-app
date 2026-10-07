package com.lapel.domain.importer

import com.lapel.domain.model.CostType
import com.lapel.domain.model.DeliveryMethod
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.Money
import com.lapel.domain.model.PaymentMilestone
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Turns the owner's existing order workbook (sheets "הזמנות" and "אספקה ותשלום", plus per-design
 * stock sheets) into app records. See docs/architecture/07-spreadsheet-review.md.
 *
 * Fixes the spreadsheet's known problems instead of copying them:
 * - each row uses its own USD rate (+3% card fee), never another row's;
 * - customs are always counted;
 * - payments are matched by row number, so balances are not hidden by misaligned formulas;
 * - a trailing "+8" in the cost formula becomes a ₪8 bank fee line.
 */
class SpreadsheetImporter(
    private val defaultUsdRate: Double = 3.84,
    private val cardFeePercent: Double = 3.0,
    private val bankFeeShekels: Double = 8.0,
) {
    private val warnings = mutableListOf<ImportWarning>()
    private val customers = linkedMapOf<String, ImportedCustomer>()

    fun import(workbook: XlsxWorkbook): SpreadsheetImport {
        warnings.clear()
        customers.clear()

        val orders = workbook.sheet(ORDERS_SHEET) ?: error("Sheet \"$ORDERS_SHEET\" not found")
        val payments = workbook.sheet(PAYMENTS_SHEET)
        if (payments == null) warn(PAYMENTS_SHEET, 0, "גיליון התשלומים לא נמצא – ההזמנות יובאו ללא תשלומים")

        val importedOrders = mutableListOf<ImportedOrder>()
        val batches = mutableListOf<ImportedStockBatch>()
        var lastDate: LocalDate? = null
        var defaultRateRows = 0

        for (row in 4..orders.maxRow) {
            val number = orders["A", row]?.text
            val name = orders["B", row]?.text
            val branch = orders["D", row]?.text
            val revenueCell = orders["O", row]
            if (number == null && name == null && branch == null) continue
            if (revenueCell == null) continue

            val date = payments?.get("F", row)?.text?.let(::parseDate)
            if (date != null) lastDate = date
            val orderDate = date ?: lastDate ?: FALLBACK_DATE

            val ownRate = orders["R", row]?.number
            if (ownRate == null) defaultRateRows++
            val costs = costsFor(orders, row, ownRate ?: defaultUsdRate)
            val orderNumber = number ?: "שורה $row"
            val supplier = orders["I", row]?.text?.takeUnless { it == "---" }

            val referenced = referencedSheet(orders["M", row]?.formula)
            val stockSheet = referenced?.let(workbook::sheet)
            if (referenced != null && stockSheet == null) {
                warn(ORDERS_SHEET, row, "\"${name ?: orderNumber}\": גיליון \"$referenced\" לא נמצא – יובא כהזמנה אחת עם הסכום הכולל")
            }
            if (stockSheet != null) {
                val received = orders["E", row]?.number?.toInt() ?: 0
                batches += ImportedStockBatch(
                    sheetRow = row,
                    orderNumber = orderNumber,
                    designName = name ?: branch ?: orderNumber,
                    quantityReceived = received,
                    purchasedOn = orderDate,
                    supplier = supplier,
                    costs = costs,
                    sales = stockSales(stockSheet),
                )
                payments?.let { p ->
                    val paid = listOfNotNull(p["H", row]?.number, p["I", row]?.number).sum()
                    if (paid > 0) {
                        warn(ORDERS_SHEET, row, "מלאי \"${name ?: orderNumber}\": התשלומים נלקחו מגיליון \"${stockSheet.name}\" (בגיליון הראשי רשום ₪${paid.toLong()})")
                    }
                }
                continue
            }

            var sold = orders["M", row]?.number
            var price = orders["N", row]?.number
            if (sold == null || price == null || sold == 0.0) {
                warn(ORDERS_SHEET, row, "הזמנה $orderNumber: חסרים כמות או מחיר – לא יובאה")
                continue
            }
            if (price > 100 && sold < 50) {
                warn(ORDERS_SHEET, row, "הזמנה $orderNumber: כמות ($sold) ומחיר ($price) הוחלפו – תוקן")
                sold = price.also { price = sold }
            }

            val customer = customerFor(name, orders["C", row]?.text, branch, orderDate, payments?.get("K", row)?.text)
            val ordered = orders["E", row]?.number?.toInt() ?: sold.toInt()
            val discount = trailingConstant(revenueCell.formula)?.let { -it } ?: 0.0
            val extraCosts = if (discount == 0.0) profitAdjustment(orders["P", row]?.formula) else emptyList()

            val paymentList = payments?.let { paymentsFor(it, row, date) }.orEmpty()
            val selling = money(price * sold) - money(discount)
            val paid = paymentList.fold(Money.ZERO) { acc, p -> acc + p.amount }

            importedOrders += ImportedOrder(
                sheetRow = row,
                orderNumber = orderNumber,
                customerKey = customer.key,
                title = branch ?: name ?: orderNumber,
                orderDate = orderDate,
                orderDateEstimated = date == null,
                status = if (paid >= selling) FulfillmentStatus.COMPLETED else FulfillmentStatus.DELIVERED,
                deliveryMethod = if (payments?.get("L", row)?.text?.contains("עצמי") == true) DeliveryMethod.SELF_PICKUP else DeliveryMethod.FEDEX,
                qualityOk = payments?.get("G", row)?.text?.let { it == "כן" },
                supplier = supplier,
                items = listOf(ImportedItem(branch ?: name ?: orderNumber, ordered, sold.toInt(), money(price))),
                costs = costs + extraCosts,
                discount = money(discount),
                payments = paymentList,
                notes = notesFor(payments, row, ownRate),
            )
        }

        val used = setOf(ORDERS_SHEET, PAYMENTS_SHEET) + batches.mapNotNull { b -> referencedSheet(orders["M", b.sheetRow]?.formula) }
        val skipped = workbook.sheets.map { it.name }.filterNot { it in used }
        if (skipped.isNotEmpty()) warn("", 0, "גיליונות שלא יובאו (מידע בלבד): ${skipped.joinToString(", ")}")

        if (defaultRateRows > 0) {
            warn(ORDERS_SHEET, 0, "ב-$defaultRateRows שורות אין שער דולר – חושב לפי $defaultUsdRate + $cardFeePercent%")
        }
        return SpreadsheetImport(customers.values.toList(), importedOrders, batches, warnings.toList())
    }

    // ---- orders -------------------------------------------------------------------------------

    private fun costsFor(sheet: XlsxSheet, row: Int, rate: Double): List<ImportedCost> {
        val costs = mutableListOf<ImportedCost>()
        val units = sheet["E", row]?.number
        val unitUsd = sheet["F", row]?.number ?: 0.0
        val moldUsd = sheet["G", row]?.number ?: 0.0
        val shippingUsd = sheet["H", row]?.number ?: 0.0
        val customs = sheet["J", row]?.number ?: 0.0
        val referral = sheet["K", row]?.number ?: 0.0
        val effectiveRate = rate * (1 + cardFeePercent / 100)

        if (units != null) {
            val usd = units * unitUsd + moldUsd + shippingUsd
            costs += ImportedCost(
                CostType.ALIBABA_PAYMENT,
                money(usd * effectiveRate),
                "\$${round2(usd)} × ${round4(effectiveRate)}",
            )
        } else {
            // No unit data (e.g. "פירוט באפליקציה"): take cost from revenue − profit as the sheet did.
            val revenue = sheet["O", row]?.number
            val profit = sheet["P", row]?.number
            if (revenue != null && profit != null) {
                costs += ImportedCost(CostType.ALIBABA_PAYMENT, money(revenue - profit - referral - customs), "לפי עמודת הרווח")
                warn(ORDERS_SHEET, row, "אין פירוט יחידות – העלות חושבה מעמודות ההכנסה והרווח")
            }
        }
        if (customs > 0) costs += ImportedCost(CostType.CUSTOMS, money(customs))
        if (referral > 0) costs += ImportedCost(CostType.REFERRAL_COMMISSION, money(referral))

        trailingConstant(sheet["L", row]?.formula)?.let { c ->
            if (c == bankFeeShekels) {
                costs += ImportedCost(CostType.BANK_FEE, money(c))
            } else {
                costs += ImportedCost(CostType.OTHER, money(c), "התאמה ידנית מהגיליון")
            }
        }
        return costs
    }

    private fun profitAdjustment(formula: String?): List<ImportedCost> {
        val c = trailingConstant(formula) ?: return emptyList()
        return listOf(ImportedCost(CostType.OTHER, money(-c), "התאמה ידנית מעמודת הרווח"))
    }

    private fun paymentsFor(sheet: XlsxSheet, row: Int, date: LocalDate?): List<ImportedPayment> = listOfNotNull(
        sheet["H", row]?.number?.takeIf { it > 0 }?.let { ImportedPayment(money(it), PaymentMilestone.DEPOSIT, date) },
        sheet["I", row]?.number?.takeIf { it > 0 }?.let { ImportedPayment(money(it), PaymentMilestone.BALANCE, date) },
    )

    private fun notesFor(payments: XlsxSheet?, row: Int, ownRate: Double?): String? {
        val parts = mutableListOf("יובא מהגיליון (שורה $row)")
        if (ownRate == null) parts += "שער דולר משוער $defaultUsdRate"
        payments?.let { p ->
            listOf("K", "M", "N", "R").mapNotNull { col -> p[col, row]?.text?.takeIf { p[col, row]?.value is String } }
                .forEach { parts += it }
            trailingConstant(p["J", row]?.formula)?.let { parts += "התאמת יתרה בגיליון: ${formatNumber(it)}" }
        }
        return parts.joinToString(" · ")
    }

    // ---- stock sheets -------------------------------------------------------------------------

    private fun stockSales(sheet: XlsxSheet): List<ImportedStockSale> {
        val headerRow = (1..minOf(sheet.maxRow, 30)).firstOrNull { r -> columns.any { sheet[it, r]?.text == "שם" } }
            ?: return emptyList<ImportedStockSale>().also { warn(sheet.name, 0, "לא נמצאה שורת כותרות") }

        fun col(vararg names: String, row: Int = headerRow): String? = columns.firstOrNull { c ->
            val t = sheet[c, row]?.text?.replace("\n", " ") ?: return@firstOrNull false
            names.any { t.contains(it) }
        }

        val nameCol = col("שם") ?: return emptyList()
        val phoneCol = col("טלפון")
        val branchCol = col("סניף")
        val dateCol = col("תאריך")
        val qtyCol = col("יחידות") ?: return emptyList()
        val payCol = col("תשלום")
        val pay2Col = payCol?.let(::nextColumn)
        val balanceCol = col("יתרה", row = headerRow + 1)
        val arrivedCol = col("הגיע")
        val trackingCol = col("מספר משלוח")
        val addressCol = col("כתובת")
        val pickupCol = col("משלוח/איסוף")

        val sales = mutableListOf<ImportedStockSale>()
        for (row in headerRow + 2..sheet.maxRow) {
            // Totals ("סה"כ", "הוזמנו", "יתרת סיכות", "נשאר") end the list of sales.
            if (columns.any { c -> sheet[c, row]?.text?.let { t -> SUMMARY_WORDS.any(t::startsWith) } == true }) break
            val qtyCell = sheet[qtyCol, row]
            if (qtyCell?.formula?.contains("SUM", ignoreCase = true) == true) break
            val qty = qtyCell?.number?.toInt() ?: continue
            if (qty <= 0) continue
            val name = sheet[nameCol, row]?.text
            val branch = branchCol?.let { sheet[it, row]?.text }
            if (name == null) warn(sheet.name, row, "מכירה ללא שם קונה ($qty סיכות)")
            val date = dateCol?.let { sheet[it, row]?.text }?.let(::parseDate)
            val customer = customerFor(name ?: branch ?: "קונה ללא שם", phoneCol?.let { sheet[it, row]?.text }, branch, date, null)

            val paid = listOfNotNull(
                payCol?.let { sheet[it, row]?.number }?.takeIf { it > 0 }?.let { ImportedPayment(money(it), PaymentMilestone.DEPOSIT, date) },
                pay2Col?.let { sheet[it, row]?.number }?.takeIf { it > 0 }?.let { ImportedPayment(money(it), PaymentMilestone.BALANCE, date) },
            )
            val paidTotal = paid.sumOf { it.amount.agorot }
            val balanceCell = balanceCol?.let { sheet[it, row] }
            val unitPrice = money(unitPriceFrom(balanceCell?.formula) ?: DEFAULT_STOCK_PRICE)
            // Total owed = what the sheet says is still due + what was paid.
            val total = balanceCell?.number?.let { Money(money(it).agorot + paidTotal) } ?: (unitPrice * qty)
            val difference = total - unitPrice * qty
            val shipping = difference.coerceAtLeastZero()
            val discount = (-difference).coerceAtLeastZero()

            val tracking = trackingCol?.let { sheet[it, row]?.text }
            val arrived = arrivedCol?.let { sheet[it, row]?.bool } ?: true
            sales += ImportedStockSale(
                sheetRow = row,
                customerKey = customer.key,
                date = date,
                quantity = qty,
                unitPrice = unitPrice,
                shippingCharged = shipping,
                discount = discount,
                payments = paid,
                sent = arrived || tracking != null,
                arrived = arrived,
                trackingNumber = tracking,
                notes = listOfNotNull(
                    addressCol?.let { sheet[it, row]?.text },
                    pickupCol?.let { sheet[it, row]?.text },
                ).joinToString(" · ").ifEmpty { null },
            )
        }
        return sales
    }

    // ---- customers ----------------------------------------------------------------------------

    private fun customerFor(name: String?, phone: String?, branch: String?, date: LocalDate?, note: String?): ImportedCustomer {
        val digits = phone?.filter { it.isDigit() }?.takeIf { it.length >= 9 }
        val displayName = name ?: branch ?: "ללא שם"
        val key = digits ?: "${displayName.trim()}|${branch?.trim().orEmpty()}"
        val terms = note?.let { TERMS.find(it) }?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val existing = customers[key]
        val merged = if (existing == null) {
            ImportedCustomer(key, displayName, phone?.takeIf { digits != null }, branch, terms, date)
        } else {
            existing.copy(
                paymentTermsDays = maxOf(existing.paymentTermsDays, terms),
                firstSeen = listOfNotNull(existing.firstSeen, date).minOrNull(),
                organization = existing.organization ?: branch,
            )
        }
        customers[key] = merged
        return merged
    }

    // ---- helpers ------------------------------------------------------------------------------

    private fun warn(sheet: String, row: Int, message: String) {
        warnings += ImportWarning(sheet, row, message)
    }

    companion object {
        const val ORDERS_SHEET = "הזמנות"
        const val PAYMENTS_SHEET = "אספקה ותשלום"
        private const val DEFAULT_STOCK_PRICE = 10.0
        private val SUMMARY_WORDS = listOf("סה\"כ", "הוזמנו", "יתרת", "נשאר")
        private val FALLBACK_DATE: LocalDate = LocalDate.of(2022, 7, 1)
        private val columns = ('A'..'Z').map { it.toString() }
        private val TERMS = Regex("""שוטף\s*\+?\s*(\d+)""")
        private val TRAILING = Regex("""([+-])\s*(\d+(?:\.\d+)?)\s*$""")
        private val SHEET_REF = Regex("""^=?'?([^'!]+)'?!""")
        private val UNIT_PRICE = Regex("""\(\s*[A-Z]+\d+\s*\*\s*(\d+(?:\.\d+)?)\s*\)""")
        private val DATE = Regex("""^(\d{1,2})[./](\d{1,2})[./](\d{2,4})$""")

        fun money(shekels: Double): Money =
            Money(BigDecimal.valueOf(shekels).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong())

        /** "31.7.22" / "9.7.25" / "16/12/2022" → date. */
        fun parseDate(text: String): LocalDate? {
            val m = DATE.find(text.trim()) ?: return null
            val (d, mo, y) = m.destructured
            val year = y.toInt().let { if (it < 100) 2000 + it else it }
            return runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull()
        }

        /** "=K4+(...)*S4+8" → 8.0; "=N15*M15-90" → -90.0; null when the formula ends in a cell. */
        fun trailingConstant(formula: String?): Double? {
            val f = formula?.trim() ?: return null
            val m = TRAILING.find(f) ?: return null
            // Make sure the digits are a literal, not the end of a cell reference like "+L37".
            val start = m.range.first
            if (start > 0 && f[start - 1].isLetter()) return null
            val value = m.groupValues[2].toDouble()
            return if (m.groupValues[1] == "-") -value else value
        }

        fun referencedSheet(formula: String?): String? = formula?.let { SHEET_REF.find(it.trim())?.groupValues?.get(1) }

        fun unitPriceFrom(formula: String?): Double? = formula?.let { UNIT_PRICE.find(it)?.groupValues?.get(1)?.toDouble() }

        private fun nextColumn(c: String): String = (c.single() + 1).toString()
        private fun round2(v: Double) = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toPlainString()
        private fun round4(v: Double) = BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        private fun formatNumber(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
    }
}
