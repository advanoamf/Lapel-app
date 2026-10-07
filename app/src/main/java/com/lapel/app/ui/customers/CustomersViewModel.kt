package com.lapel.app.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lapel.app.data.local.entity.CustomerEntity
import com.lapel.app.data.repository.CustomerRepository
import com.lapel.app.data.repository.OrderRepository
import com.lapel.domain.model.Money
import com.lapel.domain.model.sum
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CustomerRow(val customer: CustomerEntity, val orderCount: Int, val owed: Money)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CustomersViewModel @Inject constructor(
    customers: CustomerRepository,
    orders: OrderRepository,
) : ViewModel() {

    val query = MutableStateFlow("")

    val rows: StateFlow<List<CustomerRow>> = combine(
        query.flatMapLatest { customers.search(it) },
        orders.observeSummaries(),
    ) { list, summaries ->
        val byCustomer = summaries.groupBy { it.customerId }
        list.map { c ->
            val own = byCustomer[c.id].orEmpty()
            CustomerRow(c, own.size, own.map { it.financials().outstanding }.sum())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
