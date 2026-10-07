package com.lapel.app.ui.common

import com.lapel.domain.model.Money
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val moneyFormat = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))
private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** ₪1,234.5 – shekel sign first, works the same in Hebrew and English layouts. */
fun Money.formatted(): String {
    val sign = if (agorot < 0) "-" else ""
    return "$sign₪${moneyFormat.format(Math.abs(agorot) / 100.0)}"
}

fun LocalDate.formatted(): String = format(dateFormat)
