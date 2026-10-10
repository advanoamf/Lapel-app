package com.lapel.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lapel.app.data.local.dao.OrderSummaryRow
import com.lapel.app.data.repository.OrderRepository
import com.lapel.domain.finance.PeriodOrder
import com.lapel.domain.finance.PeriodSummary
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.Money
import com.lapel.domain.model.sum
import com.lapel.domain.reminder.OpenItem
import com.lapel.domain.reminder.ReminderPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class SummaryPeriod { LAST_30_DAYS, THIS_MONTH, LAST_MONTH }

data class DashboardTask(val item: OpenItem, val order: OrderSummaryRow)

data class DashboardState(
    val period: SummaryPeriod = SummaryPeriod.LAST_30_DAYS,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val summary: PeriodSummary? = null,
    val tasks: List<DashboardTask> = emptyList(),
    /** Everything customers still owe, across all orders. */
    val totalToCollect: Money = Money.ZERO,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    repository: OrderRepository,
    private val clock: Clock,
) : ViewModel() {
    private val policy = ReminderPolicy()
    val period = MutableStateFlow(SummaryPeriod.LAST_30_DAYS)

    val state: StateFlow<DashboardState> = combine(repository.observeSummaries(), period) { rows, p ->
        val today = LocalDate.now(clock)
        val (from, to) = p.range(today)
        val withFinancials = rows.map { it to it.financials() }
        val byId = rows.associateBy { it.orderId }

        DashboardState(
            period = p,
            from = from,
            to = to,
            summary = PeriodSummary.of(withFinancials.map { (r, f) -> PeriodOrder(r.orderDate, r.fulfillmentStatus, f) }, from, to),
            tasks = policy.openItems(withFinancials.map { (r, f) -> r.reminderSnapshot(f) }, clock.instant())
                .mapNotNull { item -> byId[item.reminder.orderId]?.let { DashboardTask(item, it) } },
            totalToCollect = withFinancials
                .filter { (r, _) -> r.fulfillmentStatus != FulfillmentStatus.DRAFT }
                .map { (_, f) -> f.outstanding }
                .sum(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    private fun SummaryPeriod.range(today: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
        SummaryPeriod.LAST_30_DAYS -> today.minusDays(29) to today
        SummaryPeriod.THIS_MONTH -> today.withDayOfMonth(1) to today
        SummaryPeriod.LAST_MONTH -> today.minusMonths(1).withDayOfMonth(1).let { it to it.withDayOfMonth(it.lengthOfMonth()) }
    }
}
