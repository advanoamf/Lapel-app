package com.lapel.app.ui.customers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lapel.app.data.local.dao.OrderSummaryRow
import com.lapel.app.data.local.entity.CustomerEntity
import com.lapel.app.data.repository.CustomerRepository
import com.lapel.app.data.repository.OrderRepository
import com.lapel.app.ui.navigation.CustomerDetailRoute
import com.lapel.domain.finance.OrderFinancials
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.Money
import com.lapel.domain.model.sum
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CustomerDetailState(
    val customer: CustomerEntity?,
    val orders: List<Pair<OrderSummaryRow, OrderFinancials>>,
) {
    private val active get() = orders.filter { it.first.fulfillmentStatus != FulfillmentStatus.CANCELLED }
    val revenue: Money get() = active.map { it.second.sellingTotal }.sum()
    val profit: Money get() = active.map { it.second.netProfit }.sum()
    val owed: Money get() = orders.map { it.second.outstanding }.sum()
}

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    customers: CustomerRepository,
    orders: OrderRepository,
) : ViewModel() {
    val id = savedStateHandle.toRoute<CustomerDetailRoute>().id

    val state: StateFlow<CustomerDetailState> = combine(
        customers.observe(id),
        orders.observeSummariesForCustomer(id),
    ) { c, list -> CustomerDetailState(c, list.map { it to it.financials() }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomerDetailState(null, emptyList()))
}
