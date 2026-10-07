package com.lapel.app.ui.customers

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lapel.app.R
import com.lapel.app.ui.common.dial
import com.lapel.app.ui.common.formatted
import com.lapel.app.ui.common.openWhatsApp
import com.lapel.app.ui.orders.OrderCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenOrder: (Long) -> Unit,
    onNewOrder: (Long) -> Unit,
    viewModel: CustomerDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val customer = state.customer

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(customer?.name.orEmpty()) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) } },
                actions = { IconButton(onClick = { onEdit(viewModel.id) }) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) } },
            )
        },
    ) { padding ->
        if (customer == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    customer.organization?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                    customer.phone?.let { Text(it) }
                    customer.email?.let { Text(it) }
                    Text(stringResource(R.string.customer_since_value, customer.customerSince.formatted()), style = MaterialTheme.typography.bodySmall)
                    if (customer.paymentTermsDays > 0) {
                        Text(stringResource(R.string.customer_terms_value, customer.paymentTermsDays), style = MaterialTheme.typography.bodySmall)
                    }
                    customer.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    customer.phone?.let { phone ->
                        FilledTonalButton(onClick = { context.dial(phone) }) {
                            Icon(Icons.Outlined.Call, null); Text(" " + stringResource(R.string.action_call))
                        }
                        FilledTonalButton(onClick = { context.openWhatsApp(phone) }) {
                            Icon(Icons.Outlined.Chat, null); Text(" WhatsApp")
                        }
                    }
                    FilledTonalButton(onClick = { onNewOrder(viewModel.id) }) {
                        Icon(Icons.Outlined.Add, null); Text(" " + stringResource(R.string.order_new))
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat(stringResource(R.string.orders_count), state.orders.size.toString())
                        Stat(stringResource(R.string.revenue), state.revenue.formatted())
                        Stat(stringResource(R.string.profit), state.profit.formatted())
                        Stat(stringResource(R.string.owes), state.owed.formatted(), highlight = state.owed.isPositive)
                    }
                }
            }
            items(state.orders, key = { it.first.orderId }) { (row, f) ->
                OrderCard(row, f, overdue = false, modifier = Modifier.clickable { onOpenOrder(row.orderId) })
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, highlight: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(
            value,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}
