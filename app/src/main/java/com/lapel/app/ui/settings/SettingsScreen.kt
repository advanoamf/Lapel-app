package com.lapel.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lapel.app.R
import com.lapel.app.ui.common.formatted
import com.lapel.domain.importer.SpreadsheetImport
import com.lapel.domain.model.Money

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@Composable
fun SettingsScreen(viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onFilePicked)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium) }
        item { SyncCard() }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.import_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.import_description), style = MaterialTheme.typography.bodyMedium)
                    when (val s = state) {
                        ImportState.Idle -> Button(onClick = { picker.launch(arrayOf(XLSX_MIME)) }) {
                            Text(stringResource(R.string.import_pick_file))
                        }
                        ImportState.Working -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.import_working))
                        }
                        is ImportState.Preview -> ImportPreview(s.data, s.databaseHasOrders, viewModel::confirm, viewModel::reset)
                        is ImportState.Done -> {
                            Text(
                                stringResource(R.string.import_done, s.counts.customers, s.counts.orders, s.counts.stockBatches, s.counts.stockSales, s.counts.payments),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            OutlinedButton(onClick = viewModel::reset) { Text(stringResource(R.string.action_close)) }
                        }
                        is ImportState.Failed -> {
                            Text(stringResource(R.string.import_failed, s.message), color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = viewModel::reset) { Text(stringResource(R.string.action_close)) }
                        }
                    }
                }
            }
        }
        item {
            val context = androidx.compose.ui.platform.LocalContext.current
            var ran by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.reminders_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.reminders_description), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { com.lapel.app.work.ReminderScheduler.runNow(context); ran = true }) {
                        Text(stringResource(R.string.reminders_run_now))
                    }
                    if (ran) Text(stringResource(R.string.reminders_run_now_done), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Text(
                stringResource(R.string.app_version, com.lapel.app.BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val preview = state as? ImportState.Preview
        if (preview != null) {
            if (preview.data.outstandingOrders.isNotEmpty()) {
                item { Text(stringResource(R.string.import_outstanding_title), style = MaterialTheme.typography.titleMedium) }
                items(preview.data.outstandingOrders) { o ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("#${o.orderNumber} · ${o.title}", modifier = Modifier.weight(1f))
                        Text(o.outstanding.formatted(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (preview.data.warnings.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Text(stringResource(R.string.import_warnings_title), style = MaterialTheme.typography.titleMedium)
                }
                items(preview.data.warnings) { w ->
                    val where = if (w.row > 0) "${w.sheet} · ${w.row}: " else ""
                    Text("$where${w.message}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ImportPreview(data: SpreadsheetImport, databaseHasOrders: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val revenue = data.orders.fold(Money.ZERO) { acc, o -> acc + o.sellingTotal }
    val profit = data.orders.fold(Money.ZERO) { acc, o -> acc + o.sellingTotal - o.costTotal }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.import_preview_counts, data.customers.size, data.orders.size, data.stockBatches.size))
            Text(stringResource(R.string.import_preview_revenue, revenue.formatted(), profit.formatted()))
            Text(
                stringResource(R.string.import_preview_outstanding, data.totalOutstanding.formatted()),
                fontWeight = FontWeight.Bold,
            )
        }
    }
    if (databaseHasOrders) {
        Text(stringResource(R.string.import_blocked_existing), color = MaterialTheme.colorScheme.error)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onConfirm, enabled = !databaseHasOrders) { Text(stringResource(R.string.import_confirm)) }
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
    }
}
