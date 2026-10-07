package com.lapel.app.ui.orders

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lapel.app.R
import com.lapel.app.ui.common.DateInput
import com.lapel.app.ui.common.TextInput
import com.lapel.app.ui.common.agorotToInput
import com.lapel.app.ui.common.parseShekels
import com.lapel.domain.model.CostType
import com.lapel.domain.model.Money
import com.lapel.domain.model.PaymentMethod
import com.lapel.domain.model.PaymentMilestone
import java.time.LocalDate

@StringRes
fun PaymentMethod.label(): Int = when (this) {
    PaymentMethod.BIT -> R.string.method_bit
    PaymentMethod.PAYBOX -> R.string.method_paybox
    PaymentMethod.BANK_TRANSFER -> R.string.method_bank
    PaymentMethod.CASH -> R.string.method_cash
    PaymentMethod.OTHER -> R.string.method_other
}

@StringRes
fun CostType.label(): Int = when (this) {
    CostType.ALIBABA_PAYMENT -> R.string.cost_alibaba
    CostType.CUSTOMS -> R.string.cost_customs
    CostType.REFERRAL_COMMISSION -> R.string.cost_referral
    CostType.BANK_FEE -> R.string.cost_bank_fee
    CostType.FEDEX -> R.string.cost_fedex
    CostType.OTHER -> R.string.cost_other
}

@StringRes
fun PaymentMilestone.label(): Int = when (this) {
    PaymentMilestone.DEPOSIT -> R.string.milestone_deposit
    PaymentMilestone.BALANCE -> R.string.milestone_balance
    PaymentMilestone.OTHER -> R.string.milestone_other
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordPaymentDialog(
    suggested: Money,
    suggestedMilestone: PaymentMilestone,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (Money, PaymentMethod, PaymentMilestone, LocalDate, String?) -> Unit,
) {
    var amount by remember { mutableStateOf(if (suggested.isPositive) agorotToInput(suggested.agorot) else "") }
    var method by remember { mutableStateOf(PaymentMethod.BIT) }
    var milestone by remember { mutableStateOf(suggestedMilestone) }
    var date by remember { mutableStateOf(today) }
    var reference by remember { mutableStateOf("") }
    val parsed = parseShekels(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.payment_record)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(amount, { amount = it }, stringResource(R.string.field_amount), keyboardType = KeyboardType.Decimal, isError = amount.isNotEmpty() && (parsed == null || parsed <= 0))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaymentMethod.entries.forEach { m ->
                        FilterChip(selected = m == method, onClick = { method = m }, label = { Text(stringResource(m.label())) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaymentMilestone.entries.forEach { m ->
                        FilterChip(selected = m == milestone, onClick = { milestone = m }, label = { Text(stringResource(m.label())) })
                    }
                }
                DateInput(date, { d -> d?.let { date = it } }, stringResource(R.string.field_date))
                TextInput(reference, { reference = it }, stringResource(R.string.field_reference))
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null && parsed > 0,
                onClick = { onSave(Money(parsed!!), method, milestone, date, reference.trim().ifEmpty { null }) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddCostDialog(onDismiss: () -> Unit, onSave: (CostType, Money, String?) -> Unit) {
    var type by remember { mutableStateOf(CostType.ALIBABA_PAYMENT) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val parsed = parseShekels(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cost_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CostType.entries.forEach { t ->
                        FilterChip(selected = t == type, onClick = { type = t }, label = { Text(stringResource(t.label())) })
                    }
                }
                TextInput(amount, { amount = it }, stringResource(R.string.field_amount), keyboardType = KeyboardType.Decimal, isError = amount.isNotEmpty() && parsed == null)
                TextInput(note, { note = it }, stringResource(R.string.field_notes))
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null && parsed != 0L, onClick = { onSave(type, Money(parsed!!), note.trim().ifEmpty { null }) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
