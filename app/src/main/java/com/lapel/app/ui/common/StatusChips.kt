package com.lapel.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.lapel.app.R
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.PaymentStatus

@StringRes
fun FulfillmentStatus.label(): Int = when (this) {
    FulfillmentStatus.DRAFT -> R.string.status_draft
    FulfillmentStatus.ORDERED_FROM_ALIBABA -> R.string.status_ordered
    FulfillmentStatus.SHIPPED -> R.string.status_shipped
    FulfillmentStatus.DELIVERED -> R.string.status_delivered
    FulfillmentStatus.COMPLETED -> R.string.status_completed
    FulfillmentStatus.CANCELLED -> R.string.status_cancelled
}

@StringRes
fun PaymentStatus.label(): Int = when (this) {
    PaymentStatus.UNPAID -> R.string.payment_unpaid
    PaymentStatus.DEPOSIT_PAID -> R.string.payment_partial
    PaymentStatus.FULLY_PAID -> R.string.payment_full
    PaymentStatus.OVERPAID -> R.string.payment_over
}

private data class ChipColors(val container: Color, val content: Color)

@Composable
private fun palette(light: Long, dark: Long): ChipColors {
    val dk = isSystemInDarkTheme()
    val base = Color(if (dk) dark else light)
    return ChipColors(base.copy(alpha = if (dk) 0.30f else 0.14f), base)
}

@Composable
private fun fulfillmentColors(status: FulfillmentStatus): ChipColors = when (status) {
    FulfillmentStatus.DRAFT, FulfillmentStatus.CANCELLED -> palette(0xFF5F6368, 0xFFBDC1C6)
    FulfillmentStatus.ORDERED_FROM_ALIBABA -> palette(0xFF1F5FA8, 0xFFA7C8FF)
    FulfillmentStatus.SHIPPED -> palette(0xFF4B3FB0, 0xFFC5BFFF)
    FulfillmentStatus.DELIVERED -> palette(0xFF00796B, 0xFF80CBC4)
    FulfillmentStatus.COMPLETED -> palette(0xFF2E7D32, 0xFFA5D6A7)
}

@Composable
private fun paymentColors(status: PaymentStatus): ChipColors = when (status) {
    PaymentStatus.UNPAID -> palette(0xFFC62828, 0xFFFFB4AB)
    PaymentStatus.DEPOSIT_PAID -> palette(0xFF8A5A00, 0xFFFFB955)
    PaymentStatus.FULLY_PAID -> palette(0xFF2E7D32, 0xFFA5D6A7)
    PaymentStatus.OVERPAID -> palette(0xFF7B1FA2, 0xFFE1BEE7)
}

@Composable
private fun Chip(text: String, colors: ChipColors, modifier: Modifier = Modifier, strike: Boolean = false) {
    Surface(color = colors.container, contentColor = colors.content, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            textDecoration = if (strike) TextDecoration.LineThrough else null,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun FulfillmentChip(status: FulfillmentStatus, modifier: Modifier = Modifier) =
    Chip(stringResource(status.label()), fulfillmentColors(status), modifier, strike = status == FulfillmentStatus.CANCELLED)

@Composable
fun PaymentChip(status: PaymentStatus, modifier: Modifier = Modifier) =
    Chip(stringResource(status.label()), paymentColors(status), modifier)

@Composable
fun AlertChip(text: String, modifier: Modifier = Modifier) = Chip(text, palette(0xFF8A5A00, 0xFFFFB955), modifier)

@Composable
fun OverdueChip(modifier: Modifier = Modifier) =
    Chip(stringResource(R.string.overdue), palette(0xFFC62828, 0xFFFFB4AB), modifier)
