package com.lapel.app.ui.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lapel.app.R
import com.lapel.app.data.local.entity.CustomerEntity
import com.lapel.app.ui.common.DateInput
import com.lapel.app.ui.common.TextInput
import com.lapel.domain.model.DeliveryMethod
import com.lapel.domain.model.FulfillmentStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderEditScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onNewCustomer: () -> Unit,
    viewModel: OrderEditViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val savedId by viewModel.savedId.collectAsStateWithLifecycle()
    LaunchedEffect(savedId) { savedId?.let(onSaved) }
    val err = form.showErrors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isNew) R.string.order_new else R.string.order_edit)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CustomerPicker(customers, form.customerId, err && form.customerError, { id -> viewModel.edit { copy(customerId = id) } }, onNewCustomer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(form.orderNumber, { v -> viewModel.edit { copy(orderNumber = v) } }, stringResource(R.string.field_order_number), Modifier.weight(1f), isError = err && form.numberError)
                TextInput(form.depositPercent, { v -> viewModel.edit { copy(depositPercent = v.filter(Char::isDigit)) } }, stringResource(R.string.field_deposit_percent), Modifier.weight(1f), KeyboardType.Number, isError = err && form.depositError)
            }
            TextInput(form.title, { v -> viewModel.edit { copy(title = v) } }, stringResource(R.string.field_title))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(form.orderDate, { d -> d?.let { viewModel.edit { copy(orderDate = it) } } }, stringResource(R.string.field_order_date), Modifier.weight(1f))
                DateInput(form.dueDate, { d -> viewModel.edit { copy(dueDate = d) } }, stringResource(R.string.field_due_date), Modifier.weight(1f))
            }
            if (viewModel.isNew) {
                Text(stringResource(R.string.field_initial_status), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(FulfillmentStatus.DRAFT, FulfillmentStatus.ORDERED_FROM_ALIBABA).forEach { s ->
                        FilterChip(form.initialStatus == s, { viewModel.edit { copy(initialStatus = s) } }, { Text(stringResource(if (s == FulfillmentStatus.DRAFT) R.string.status_draft_quote else R.string.status_ordered)) })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(form.deliveryMethod == DeliveryMethod.FEDEX, { viewModel.edit { copy(deliveryMethod = DeliveryMethod.FEDEX) } }, { Text("FedEx") })
                FilterChip(form.deliveryMethod == DeliveryMethod.SELF_PICKUP, { viewModel.edit { copy(deliveryMethod = DeliveryMethod.SELF_PICKUP) } }, { Text(stringResource(R.string.delivery_pickup)) })
            }

            Text(stringResource(R.string.section_items), style = MaterialTheme.typography.titleMedium)
            form.items.forEachIndexed { index, item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextInput(item.designName, { v -> viewModel.editItem(index) { copy(designName = v) } }, stringResource(R.string.field_design), Modifier.weight(1f), isError = err && item.designName.isBlank())
                            if (form.items.size > 1) IconButton(onClick = { viewModel.removeItem(index) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextInput(item.quantitySold, { v -> viewModel.editItem(index) { copy(quantitySold = v.filter(Char::isDigit)) } }, stringResource(R.string.field_qty_sold), Modifier.weight(1f), KeyboardType.Number, isError = err && item.quantitySold.toIntOrNull() == null)
                            TextInput(item.quantityOrdered, { v -> viewModel.editItem(index) { copy(quantityOrdered = v.filter(Char::isDigit)) } }, stringResource(R.string.field_qty_ordered), Modifier.weight(1f), KeyboardType.Number)
                            TextInput(item.unitPrice, { v -> viewModel.editItem(index) { copy(unitPrice = v) } }, stringResource(R.string.field_unit_price), Modifier.weight(1f), KeyboardType.Decimal, isError = err && com.lapel.app.ui.common.parseShekels(item.unitPrice) == null)
                        }
                    }
                }
            }
            OutlinedButton(onClick = viewModel::addItem) { Icon(Icons.Outlined.Add, null); Text(" " + stringResource(R.string.item_add)) }

            if (viewModel.isNew) {
                TextInput(form.alibabaPayment, { v -> viewModel.edit { copy(alibabaPayment = v) } }, stringResource(R.string.field_alibaba_payment), keyboardType = KeyboardType.Decimal)
                Text(stringResource(R.string.bank_fee_hint), style = MaterialTheme.typography.bodySmall)
            }
            TextInput(
                form.customs, { v -> viewModel.edit { copy(customs = v) } }, stringResource(R.string.field_customs),
                keyboardType = KeyboardType.Decimal, isError = err && form.customs.isNotBlank() && com.lapel.app.ui.common.parseShekels(form.customs) == null,
            )
            TextInput(form.discount, { v -> viewModel.edit { copy(discount = v) } }, stringResource(R.string.field_discount), keyboardType = KeyboardType.Decimal)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(form.alibabaOrderNumber, { v -> viewModel.edit { copy(alibabaOrderNumber = v) } }, stringResource(R.string.field_alibaba_number), Modifier.weight(1f))
                DateInput(form.alibabaOrderedOn, { d -> viewModel.edit { copy(alibabaOrderedOn = d) } }, stringResource(R.string.field_alibaba_date), Modifier.weight(1f))
            }
            TextInput(form.supplierName, { v -> viewModel.edit { copy(supplierName = v) } }, stringResource(R.string.field_supplier))
            if (form.deliveryMethod == DeliveryMethod.FEDEX) {
                TextInput(form.shippingAddress, { v -> viewModel.edit { copy(shippingAddress = v) } }, stringResource(R.string.field_shipping_address), singleLine = false)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = form.addressSentToSupplier,
                        enabled = form.shippingAddress.isNotBlank(),
                        onCheckedChange = { c -> viewModel.edit { copy(addressSentToSupplier = c) } },
                    )
                    Text(stringResource(R.string.field_address_sent))
                }
            }
            TextInput(form.notes, { v -> viewModel.edit { copy(notes = v) } }, stringResource(R.string.field_notes), singleLine = false)
            if (err && !form.isValid) Text(stringResource(R.string.form_errors), color = MaterialTheme.colorScheme.error)
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@Composable
private fun CustomerPicker(
    customers: List<CustomerEntity>,
    selectedId: Long,
    isError: Boolean,
    onSelect: (Long) -> Unit,
    onNewCustomer: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val selected = customers.firstOrNull { it.id == selectedId }
    val interaction = remember { MutableInteractionSource() }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { if (it is PressInteraction.Release) open = true }
    }
    OutlinedTextField(
        value = selected?.let { listOfNotNull(it.name, it.organization).joinToString(" · ") }.orEmpty(),
        onValueChange = {},
        readOnly = true,
        isError = isError,
        label = { Text(stringResource(R.string.field_customer)) },
        modifier = Modifier.fillMaxWidth(),
        interactionSource = interaction,
    )
    if (open) {
        val filtered = customers.filter { c ->
            query.isBlank() || listOfNotNull(c.name, c.organization, c.phone).any { it.contains(query.trim(), ignoreCase = true) }
        }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.field_customer)) },
            text = {
                Column {
                    OutlinedTextField(query, { query = it }, placeholder = { Text(stringResource(R.string.customers_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(filtered, key = { it.id }) { c ->
                            Text(
                                listOfNotNull(c.name, c.organization).joinToString(" · "),
                                modifier = Modifier.fillMaxWidth().clickable { onSelect(c.id); open = false }.padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false; onNewCustomer() }) { Text(stringResource(R.string.customer_new)) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
