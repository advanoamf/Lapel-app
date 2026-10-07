package com.lapel.app.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Israeli mobile "050-1234567" → "972501234567" for WhatsApp links. */
fun whatsAppNumber(phone: String): String? {
    val digits = phone.filter { it.isDigit() }
    return when {
        digits.startsWith("972") -> digits
        digits.startsWith("0") && digits.length == 10 -> "972" + digits.drop(1)
        digits.startsWith("1") && digits.length == 11 -> digits // North America (+1)
        digits.length >= 9 -> digits
        else -> null
    }
}

fun Context.dial(phone: String) {
    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() || it == '+' }}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun Context.openWhatsApp(phone: String, message: String? = null) {
    val number = whatsAppNumber(phone) ?: return
    val text = message?.let { "?text=" + Uri.encode(it) }.orEmpty()
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number$text")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
