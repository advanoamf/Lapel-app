package com.lapel.app.ui.orders

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lapel.app.R

@StringRes
private fun OrderFilter.label(): Int = when (this) {
    OrderFilter.ALL -> R.string.filter_all
    OrderFilter.UNPAID -> R.string.filter_unpaid
    OrderFilter.ADDRESS -> R.string.filter_address
    OrderFilter.IN_TRANSIT -> R.string.filter_in_transit
    OrderFilter.DELIVERED_UNPAID -> R.string.filter_delivered_unpaid
    OrderFilter.OVERDUE -> R.string.filter_overdue
    OrderFilter.COMPLETED -> R.string.filter_completed
    OrderFilter.DRAFTS -> R.string.filter_drafts
    OrderFilter.CANCELLED -> R.string.filter_cancelled
}

@Composable
fun OrdersScreen(
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    viewModel: OrdersViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Outlined.Add, stringResource(R.string.order_new)) }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.query.value = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text(stringResource(R.string.orders_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrderFilter.entries.forEach { f ->
                        FilterChip(selected = f == filter, onClick = { viewModel.filter.value = f }, label = { Text(stringResource(f.label())) })
                    }
                }
            }
            if (items.isEmpty()) {
                item { Text(stringResource(R.string.orders_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(items, key = { it.row.orderId }) { item ->
                OrderCard(item.row, item.financials, item.overdue, Modifier.clickable { onOpen(item.row.orderId) }, item.address)
            }
        }
    }
}
