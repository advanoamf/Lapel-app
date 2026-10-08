package com.lapel.app.ui.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lapel.app.data.local.entity.CustomerEntity
import com.lapel.app.data.local.entity.OrderCostEntity
import com.lapel.app.data.local.entity.OrderEntity
import com.lapel.app.data.local.entity.OrderItemEntity
import com.lapel.app.data.local.entity.PaymentEntity
import com.lapel.app.data.local.entity.StatusChangeEntity
import com.lapel.app.data.repository.CustomerRepository
import com.lapel.app.data.repository.OrderRepository
import com.lapel.app.ui.navigation.OrderDetailRoute
import com.lapel.domain.finance.CostLine
import com.lapel.domain.finance.FinancialInput
import com.lapel.domain.finance.OrderFinancials
import com.lapel.domain.finance.OrderLine
import com.lapel.domain.model.CostType
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.Money
import com.lapel.domain.model.PaymentMethod
import com.lapel.domain.model.PaymentMilestone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class OrderDetailState(
    val order: OrderEntity? = null,
    val customer: CustomerEntity? = null,
    val items: List<OrderItemEntity> = emptyList(),
    val costs: List<OrderCostEntity> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
    val history: List<StatusChangeEntity> = emptyList(),
    val financials: OrderFinancials? = null,
) {
    /** The next manual step offered as a button, if any. */
    val nextStatus: FulfillmentStatus?
        get() = when (order?.fulfillmentStatus) {
            FulfillmentStatus.DRAFT -> FulfillmentStatus.ORDERED_FROM_ALIBABA
            FulfillmentStatus.ORDERED_FROM_ALIBABA -> FulfillmentStatus.SHIPPED
            FulfillmentStatus.SHIPPED -> FulfillmentStatus.DELIVERED
            else -> null
        }

    /** Amount suggested in the payment dialog: what is due now, else everything still owed. */
    val suggestedPayment: Money
        get() = financials?.let { if (it.dueNow.isPositive) it.dueNow else it.outstanding } ?: Money.ZERO

    val suggestedMilestone: PaymentMilestone
        get() = if (financials?.paidTotal?.isPositive == true) PaymentMilestone.BALANCE else PaymentMilestone.DEPOSIT
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: OrderRepository,
    customers: CustomerRepository,
    private val clock: Clock,
) : ViewModel() {
    val id = savedStateHandle.toRoute<OrderDetailRoute>().id

    private val order = repository.observeOrder(id)
    private val customer = order.flatMapLatest { o -> o?.let { customers.observe(it.customerId) } ?: flowOf(null) }

    val state: StateFlow<OrderDetailState> = combine(
        combine(order, customer, repository.observeItems(id)) { o, c, i -> Triple(o, c, i) },
        repository.observeCosts(id),
        repository.observePayments(id),
        repository.observeStatusChanges(id),
    ) { (o, c, items), costs, payments, history ->
        val financials = o?.let {
            OrderFinancials.calculate(
                FinancialInput(
                    lines = items.map { OrderLine(it.quantityOrdered, it.quantitySold, Money(it.unitPriceAgorot)) },
                    costs = costs.map { CostLine(it.type, Money(it.amountAgorot)) },
                    payments = payments.map { Money(it.amountAgorot) },
                    discount = Money(it.discountAgorot),
                    depositPercent = it.depositPercent,
                    status = it.fulfillmentStatus,
                ),
            )
        }
        OrderDetailState(o, c, items, costs, payments, history, financials)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrderDetailState())

    fun advance() {
        val next = state.value.nextStatus ?: return
        viewModelScope.launch { repository.setStatus(id, next) }
    }

    fun cancel() = viewModelScope.launch { repository.setStatus(id, FulfillmentStatus.CANCELLED) }

    /** Manual correction to any status (e.g. an imported order that is really still in production). */
    fun setStatus(status: FulfillmentStatus) = viewModelScope.launch { repository.setStatus(id, status) }

    fun setAddressSent(sent: Boolean) = viewModelScope.launch { repository.setAddressSent(id, sent) }

    fun recordPayment(amount: Money, method: PaymentMethod, milestone: PaymentMilestone, date: LocalDate, reference: String?) {
        if (!amount.isPositive) return
        viewModelScope.launch {
            repository.recordPayment(
                PaymentEntity(
                    orderId = id, stockSaleId = null, amountAgorot = amount.agorot, method = method,
                    milestone = milestone, receivedOn = date, reference = reference, createdAt = Instant.EPOCH,
                ),
            )
        }
    }

    fun deletePayment(payment: PaymentEntity) = viewModelScope.launch { repository.deletePayment(payment) }

    fun addCost(type: CostType, amount: Money, note: String?) {
        if (amount.agorot == 0L) return
        viewModelScope.launch { repository.addCost(OrderCostEntity(orderId = id, type = type, amountAgorot = amount.agorot, note = note)) }
    }

    fun deleteCost(cost: OrderCostEntity) = viewModelScope.launch { repository.deleteCost(cost) }

    fun today(): LocalDate = LocalDate.now(clock)
}
