package com.lapel.app.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lapel.app.data.local.dao.OrderSummaryRow
import com.lapel.app.data.repository.OrderRepository
import com.lapel.domain.finance.OrderFinancials
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.reminder.AddressStatus
import com.lapel.domain.reminder.ReminderPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import javax.inject.Inject

enum class OrderFilter { ALL, UNPAID, ADDRESS, IN_TRANSIT, DELIVERED_UNPAID, OVERDUE, COMPLETED, DRAFTS, CANCELLED }

data class OrderListItem(
    val row: OrderSummaryRow,
    val financials: OrderFinancials,
    val overdue: Boolean,
    val address: AddressStatus,
)

@HiltViewModel
class OrdersViewModel @Inject constructor(
    repository: OrderRepository,
    clock: Clock,
) : ViewModel() {
    private val policy = ReminderPolicy()

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(OrderFilter.ALL)

    private val all = repository.observeSummaries()

    val items: StateFlow<List<OrderListItem>> = combine(all, query, filter) { rows, q, f ->
        val now = clock.instant()
        rows.asSequence()
            .map { r ->
                val fin = r.financials()
                val snapshot = r.reminderSnapshot(fin)
                OrderListItem(r, fin, policy.isOverdue(snapshot, now), policy.addressStatus(snapshot))
            }
            .filter { it.matches(f) }
            .filter { q.isBlank() || it.row.matches(q.trim()) }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun OrderListItem.matches(f: OrderFilter): Boolean {
        val s = row.fulfillmentStatus
        val active = s != FulfillmentStatus.DRAFT && s != FulfillmentStatus.CANCELLED
        return when (f) {
            OrderFilter.ALL -> s != FulfillmentStatus.CANCELLED
            OrderFilter.UNPAID -> active && financials.outstanding.isPositive
            OrderFilter.ADDRESS -> address == AddressStatus.MISSING || address == AddressStatus.NOT_SENT
            OrderFilter.IN_TRANSIT -> s == FulfillmentStatus.SHIPPED
            OrderFilter.DELIVERED_UNPAID -> s == FulfillmentStatus.DELIVERED && financials.outstanding.isPositive
            OrderFilter.OVERDUE -> overdue
            OrderFilter.COMPLETED -> s == FulfillmentStatus.COMPLETED
            OrderFilter.DRAFTS -> s == FulfillmentStatus.DRAFT
            OrderFilter.CANCELLED -> s == FulfillmentStatus.CANCELLED
        }
    }

    private fun OrderSummaryRow.matches(q: String): Boolean =
        listOfNotNull(orderNumber, title, customerName, customerOrganization, customerPhone)
            .any { it.contains(q, ignoreCase = true) }
}
