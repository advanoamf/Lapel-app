package com.lapel.app.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lapel.app.R
import com.lapel.app.ui.common.OverdueChip
import com.lapel.app.ui.common.formatted
import com.lapel.domain.finance.PeriodSummary
import com.lapel.domain.model.Money
import com.lapel.domain.model.ReminderType

@StringRes
private fun SummaryPeriod.label(): Int = when (this) {
    SummaryPeriod.LAST_30_DAYS -> R.string.period_last_30_days
    SummaryPeriod.THIS_MONTH -> R.string.period_this_month
    SummaryPeriod.LAST_MONTH -> R.string.period_last_month
}

@StringRes
private fun ReminderType.title(): Int = when (this) {
    ReminderType.DEPOSIT_DUE -> R.string.todo_deposit
    ReminderType.BALANCE_DUE -> R.string.todo_balance
    ReminderType.BALANCE_OVERDUE -> R.string.todo_balance_overdue
    ReminderType.STALE_DRAFT -> R.string.todo_draft
    ReminderType.ADDRESS_MISSING -> R.string.todo_address_missing
    ReminderType.ADDRESS_NOT_SENT -> R.string.todo_address_not_sent
    else -> R.string.todo_other
}

@Composable
fun DashboardScreen(
    onOpenOrder: (Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text(stringResource(R.string.tab_dashboard), style = MaterialTheme.typography.headlineMedium) }
        item {
            SummaryCard(state, onPeriod = { viewModel.period.value = it })
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(R.string.todo_title, state.tasks.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.totalToCollect.isPositive) {
                    Text(
                        stringResource(R.string.todo_total_to_collect, state.totalToCollect.formatted()),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        if (state.tasks.isEmpty()) {
            item { Text(stringResource(R.string.todo_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(state.tasks, key = { "${it.order.orderId}-${it.item.reminder.type}" }) { task ->
            TaskCard(task, Modifier.clickable { onOpenOrder(task.order.orderId) })
        }
    }
}

@Composable
private fun SummaryCard(state: DashboardState, onPeriod: (SummaryPeriod) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SummaryPeriod.entries.forEach { p ->
                    FilterChip(selected = p == state.period, onClick = { onPeriod(p) }, label = { Text(stringResource(p.label())) })
                }
            }
            val s = state.summary ?: return@Column
            if (state.from != null && state.to != null) {
                Text(
                    stringResource(R.string.summary_range, state.from.formatted(), state.to.formatted(), s.orderCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SummaryLine(R.string.summary_income, s.income)
            SummaryLine(R.string.summary_expenses, s.expenses)
            HorizontalDivider()
            ProfitLine(s)
            Text(
                stringResource(R.string.summary_cash, s.received.formatted(), s.toCollect.formatted()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryLine(@StringRes label: Int, amount: Money) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
        Text(amount.formatted(), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ProfitLine(s: PeriodSummary) {
    val color = if (s.profit.agorot < 0) MaterialTheme.colorScheme.error else Color.Unspecified
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.summary_profit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Column(horizontalAlignment = Alignment.End) {
            Text(s.profit.formatted(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            s.marginPercent?.let {
                Text(
                    stringResource(R.string.summary_margin, Math.round(it).toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TaskCard(task: DashboardTask, modifier: Modifier = Modifier) {
    val r = task.item.reminder
    val order = task.order
    Card(modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(r.type.title()),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (task.item.overdue) MaterialTheme.colorScheme.error else Color.Unspecified,
                    )
                    if (task.item.overdue && r.type != ReminderType.BALANCE_OVERDUE) OverdueChip()
                }
                Text(
                    stringResource(
                        R.string.todo_order_line,
                        listOfNotNull(order.customerName, order.title.takeIf { it != order.customerName }).joinToString(" · "),
                        order.orderNumber,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (r.amount.isPositive) {
                Text(r.amount.formatted(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
