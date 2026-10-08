package com.lapel.app.ui.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lapel.app.R
import com.lapel.app.ui.common.FulfillmentChip
import com.lapel.app.ui.common.PaymentChip
import com.lapel.app.ui.common.formatted
import com.lapel.app.ui.common.label
import com.lapel.app.ui.common.openWhatsApp
import com.lapel.domain.finance.OrderFinancials
import com.lapel.domain.model.ChangeSource
import com.lapel.domain.model.DeliveryMethod
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.Money
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Jerusalem"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenCustomer: (Long) -> Unit,
    viewModel: OrderDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val order = state.order
    val f = state.financials
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var paymentDialog by remember { mutableStateOf(false) }
    var costDialog by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var statusDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(order?.let { "#${it.orderNumber}" }.orEmpty()) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = { onEdit(viewModel.id) }) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.status_change)) },
                            onClick = { menu = false; statusDialog = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.order_cancel)) },
                            enabled = order?.fulfillmentStatus != FulfillmentStatus.CANCELLED,
                            onClick = { menu = false; confirmCancel = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (order == null || f == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(order.title, style = MaterialTheme.typography.headlineSmall)
                    state.customer?.let { c ->
                        Text(
                            listOfNotNull(c.name, c.organization).joinToString(" · "),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onOpenCustomer(c.id) },
                        )
                    }
                    Text(stringResource(R.string.order_date_value, order.orderDate.formatted()), style = MaterialTheme.typography.bodySmall)
                    order.dueDate?.let { Text(stringResource(R.string.due_date_value, it.formatted()), style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FulfillmentChip(order.fulfillmentStatus)
                        if (order.fulfillmentStatus != FulfillmentStatus.DRAFT) PaymentChip(f.paymentStatus)
                    }
                }
            }
            item { StatusSteps(order.fulfillmentStatus) }
            state.nextStatus?.let { next ->
                item {
                    Button(onClick = viewModel::advance, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.mark_as, stringResource(next.label())))
                    }
                }
            }
            if (order.deliveryMethod == DeliveryMethod.FEDEX) {
                item {
                    AddressCard(
                        address = order.shippingAddress,
                        sent = order.addressSentToSupplier,
                        onSentChange = viewModel::setAddressSent,
                        onAsk = state.customer?.phone?.let { phone ->
                            val msg = context.getString(R.string.whatsapp_address_request, state.customer?.name.orEmpty(), order.orderNumber)
                            ({ context.openWhatsApp(phone, msg) })
                        },
                        onCopy = { text ->
                            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                            cm?.setPrimaryClip(android.content.ClipData.newPlainText("address", text))
                        },
                        onEdit = { onEdit(viewModel.id) },
                    )
                }
            }
            item {
                MoneyCard(f, onRecordPayment = { paymentDialog = true }, onRemind = state.customer?.phone?.takeIf { f.outstanding.isPositive }?.let { phone ->
                    val msg = context.getString(R.string.whatsapp_reminder, state.customer?.name.orEmpty(), order.orderNumber, f.outstanding.formatted())
                    ({ context.openWhatsApp(phone, msg) })
                })
            }

            item { SectionTitle(stringResource(R.string.section_items)) }
            items(state.items, key = { "i${it.id}" }) { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(item.designName)
                        Text(
                            stringResource(R.string.item_quantities, item.quantitySold, item.quantityOrdered, Money(item.unitPriceAgorot).formatted()),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text((Money(item.unitPriceAgorot) * item.quantitySold).formatted())
                }
            }
            if (order.discountAgorot != 0L) {
                item { Text(stringResource(R.string.discount_value, Money(order.discountAgorot).formatted()), style = MaterialTheme.typography.bodySmall) }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(stringResource(R.string.section_costs), Modifier.weight(1f))
                    TextButton(onClick = { costDialog = true }) { Text(stringResource(R.string.cost_add)) }
                }
            }
            items(state.costs, key = { "c${it.id}" }) { cost ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(cost.type.label()))
                        cost.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    Text(Money(cost.amountAgorot).formatted())
                    IconButton(onClick = { viewModel.deleteCost(cost) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                }
            }

            item { SectionTitle(stringResource(R.string.section_payments)) }
            if (state.payments.isEmpty()) item { Text(stringResource(R.string.payments_empty), style = MaterialTheme.typography.bodySmall) }
            items(state.payments, key = { "p${it.id}" }) { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${stringResource(p.milestone.label())} · ${stringResource(p.method.label())}")
                        Text(listOfNotNull(p.receivedOn.formatted(), p.reference).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(Money(p.amountAgorot).formatted(), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { viewModel.deletePayment(p) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                }
            }

            item { SectionTitle(stringResource(R.string.section_history)) }
            items(state.history, key = { "h${it.id}" }) { h ->
                val source = when (h.source) {
                    ChangeSource.MANUAL -> R.string.source_manual
                    ChangeSource.FEDEX_SYNC -> R.string.source_fedex
                    ChangeSource.SYSTEM -> R.string.source_system
                }
                Text(
                    "${timeFormat.format(h.at)} · ${stringResource(h.toStatus.label())} · ${stringResource(source)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            order.notes?.let { notes ->
                item { HorizontalDivider() }
                item { Text(notes, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    if (paymentDialog) {
        RecordPaymentDialog(
            suggested = state.suggestedPayment,
            suggestedMilestone = state.suggestedMilestone,
            today = viewModel.today(),
            onDismiss = { paymentDialog = false },
            onSave = { amount, method, milestone, date, ref ->
                viewModel.recordPayment(amount, method, milestone, date, ref)
                paymentDialog = false
            },
        )
    }
    if (costDialog) {
        AddCostDialog(
            onDismiss = { costDialog = false },
            onSave = { type, amount, note -> viewModel.addCost(type, amount, note); costDialog = false },
        )
    }
    if (statusDialog) {
        AlertDialog(
            onDismissRequest = { statusDialog = false },
            title = { Text(stringResource(R.string.status_change)) },
            text = {
                Column {
                    FulfillmentStatus.entries.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().clickable { viewModel.setStatus(s); statusDialog = false }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = order?.fulfillmentStatus == s, onClick = { viewModel.setStatus(s); statusDialog = false })
                            Text(stringResource(s.label()))
                        }
                    }
                    Text(stringResource(R.string.status_change_hint), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { statusDialog = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            text = { Text(stringResource(R.string.order_cancel_confirm)) },
            confirmButton = { TextButton(onClick = { viewModel.cancel(); confirmCancel = false }) { Text(stringResource(R.string.order_cancel)) } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.action_back)) } },
        )
    }
}

@Composable
private fun AddressCard(
    address: String?,
    sent: Boolean,
    onSentChange: (Boolean) -> Unit,
    onAsk: (() -> Unit)?,
    onCopy: (String) -> Unit,
    onEdit: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.field_shipping_address), style = MaterialTheme.typography.titleSmall)
            if (address.isNullOrBlank()) {
                Text(stringResource(R.string.address_missing), color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onAsk?.let { OutlinedButton(onClick = it) { Text(stringResource(R.string.address_ask_whatsapp)) } }
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.address_add)) }
                }
            } else {
                Text(address)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sent, onCheckedChange = onSentChange)
                    Text(stringResource(R.string.field_address_sent), modifier = Modifier.weight(1f))
                    TextButton(onClick = { onCopy(address) }) { Text(stringResource(R.string.action_copy)) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(top = 4.dp))
}

private val steps = listOf(
    FulfillmentStatus.DRAFT,
    FulfillmentStatus.ORDERED_FROM_ALIBABA,
    FulfillmentStatus.SHIPPED,
    FulfillmentStatus.DELIVERED,
    FulfillmentStatus.COMPLETED,
)

@Composable
private fun StatusSteps(current: FulfillmentStatus) {
    if (current == FulfillmentStatus.CANCELLED) return
    val index = steps.indexOf(current)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(progress = { (index + 1f) / steps.size }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            steps.forEachIndexed { i, s ->
                Text(
                    stringResource(s.label()),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == index) FontWeight.Bold else FontWeight.Normal,
                    color = if (i <= index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MoneyCard(f: OrderFinancials, onRecordPayment: () -> Unit, onRemind: (() -> Unit)?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MoneyRow(stringResource(R.string.money_selling), f.sellingTotal)
            MoneyRow(stringResource(R.string.money_received), f.paidTotal)
            MoneyRow(stringResource(R.string.money_outstanding), f.outstanding, big = true, alert = f.outstanding.isPositive)
            if (f.dueNow.isPositive) MoneyRow(stringResource(R.string.money_due_now), f.dueNow, alert = true)
            HorizontalDivider()
            MoneyRow(stringResource(R.string.money_costs), f.costTotal)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.money_profit))
                Text(
                    f.netProfit.formatted() + (f.marginPercent?.let { " (%.1f%%)".format(it) } ?: ""),
                    fontWeight = FontWeight.Bold,
                )
            }
            f.costPerPin?.let { MoneyRow(stringResource(R.string.money_cost_per_pin), it) }
            if (f.cashPosition.isNegative) {
                Text(stringResource(R.string.money_out_of_pocket, (-f.cashPosition).formatted()), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRecordPayment) { Text(stringResource(R.string.payment_record)) }
                onRemind?.let { OutlinedButton(onClick = it) { Text(stringResource(R.string.remind_whatsapp)) } }
            }
        }
    }
}

@Composable
private fun MoneyRow(label: String, value: Money, big: Boolean = false, alert: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        Text(
            value.formatted(),
            style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (big) FontWeight.Bold else FontWeight.Normal,
            color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
