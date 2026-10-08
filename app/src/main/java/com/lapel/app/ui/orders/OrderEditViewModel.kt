package com.lapel.app.ui.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lapel.app.data.local.entity.CustomerEntity
import com.lapel.app.data.local.entity.OrderCostEntity
import com.lapel.app.data.local.entity.OrderEntity
import com.lapel.app.data.local.entity.OrderItemEntity
import com.lapel.app.data.repository.CustomerRepository
import com.lapel.app.data.repository.OrderRepository
import com.lapel.app.ui.common.agorotToInput
import com.lapel.app.ui.common.parseShekels
import com.lapel.app.ui.navigation.OrderEditRoute
import com.lapel.domain.model.CostType
import com.lapel.domain.model.DeliveryMethod
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.PinType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class ItemForm(
    val id: Long = 0,
    val designName: String = "",
    val quantityOrdered: String = "",
    val quantitySold: String = "",
    val unitPrice: String = "",
    val pinType: PinType = PinType.SOFT_ENAMEL,
) {
    val valid get() = designName.isNotBlank() && (quantitySold.toIntOrNull() ?: -1) >= 0 &&
        (quantityOrdered.ifBlank { quantitySold }.toIntOrNull() ?: -1) >= 0 && parseShekels(unitPrice) != null
}

data class OrderForm(
    val customerId: Long = 0,
    val orderNumber: String = "",
    val title: String = "",
    val orderDate: LocalDate,
    val dueDate: LocalDate? = null,
    val deliveryMethod: DeliveryMethod = DeliveryMethod.FEDEX,
    val depositPercent: String = "50",
    val discount: String = "",
    val alibabaOrderNumber: String = "",
    val alibabaOrderedOn: LocalDate? = null,
    val supplierName: String = "",
    val notes: String = "",
    val shippingAddress: String = "",
    val addressSentToSupplier: Boolean = false,
    /** Only for a new order: start as a draft (quote) or as already ordered from Alibaba. */
    val initialStatus: FulfillmentStatus = FulfillmentStatus.ORDERED_FROM_ALIBABA,
    val items: List<ItemForm> = listOf(ItemForm()),
    /** Only on a new order: the ₪ charged for the Alibaba order. Later costs are added on the order screen. */
    val alibabaPayment: String = "",
    val showErrors: Boolean = false,
) {
    val customerError get() = customerId == 0L
    val numberError get() = orderNumber.isBlank()
    val depositError get() = depositPercent.toIntOrNull()?.let { it !in 0..100 } ?: true
    val itemsError get() = items.isEmpty() || items.any { !it.valid }
    val isValid get() = !customerError && !numberError && !depositError && !itemsError &&
        (discount.isBlank() || parseShekels(discount) != null) && (alibabaPayment.isBlank() || parseShekels(alibabaPayment) != null)
}

@HiltViewModel
class OrderEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orders: OrderRepository,
    customers: CustomerRepository,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<OrderEditRoute>()
    val isNew = route.id == 0L
    private var original: OrderEntity? = null

    val form = MutableStateFlow(OrderForm(customerId = route.customerId, orderDate = LocalDate.now(clock)))
    val savedId = MutableStateFlow<Long?>(null)
    val customers: StateFlow<List<CustomerEntity>> =
        customers.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            if (isNew) {
                val next = orders.nextOrderNumber()
                form.update { it.copy(orderNumber = next) }
            } else {
                val o = orders.observeOrder(route.id).first() ?: return@launch
                original = o
                val items = orders.observeItems(route.id).first()
                form.value = OrderForm(
                    customerId = o.customerId,
                    orderNumber = o.orderNumber,
                    title = o.title,
                    orderDate = o.orderDate,
                    dueDate = o.dueDate,
                    deliveryMethod = o.deliveryMethod,
                    depositPercent = o.depositPercent.toString(),
                    discount = if (o.discountAgorot == 0L) "" else agorotToInput(o.discountAgorot),
                    alibabaOrderNumber = o.alibabaOrderNumber.orEmpty(),
                    alibabaOrderedOn = o.alibabaOrderedOn,
                    supplierName = o.supplierName.orEmpty(),
                    notes = o.notes.orEmpty(),
                    shippingAddress = o.shippingAddress.orEmpty(),
                    addressSentToSupplier = o.addressSentToSupplier,
                    initialStatus = o.fulfillmentStatus,
                    items = items.map {
                        ItemForm(it.id, it.designName, it.quantityOrdered.toString(), it.quantitySold.toString(), agorotToInput(it.unitPriceAgorot), it.pinType)
                    }.ifEmpty { listOf(ItemForm()) },
                )
            }
        }
    }

    fun edit(block: OrderForm.() -> OrderForm) = form.update(block)

    fun editItem(index: Int, block: ItemForm.() -> ItemForm) = form.update { f ->
        f.copy(items = f.items.mapIndexed { i, item -> if (i == index) item.block() else item })
    }

    fun addItem() = form.update { it.copy(items = it.items + ItemForm()) }
    fun removeItem(index: Int) = form.update { it.copy(items = it.items.filterIndexed { i, _ -> i != index }) }

    fun save() {
        val f = form.value
        if (!f.isValid) { form.update { it.copy(showErrors = true) }; return }
        viewModelScope.launch {
            val now = Instant.now(clock)
            val base = original
            val title = f.title.trim().ifEmpty { f.items.first().designName.trim() }
            val entity = OrderEntity(
                id = base?.id ?: 0,
                customerId = f.customerId,
                orderNumber = f.orderNumber.trim(),
                title = title,
                orderDate = f.orderDate,
                dueDate = f.dueDate,
                fulfillmentStatus = base?.fulfillmentStatus ?: f.initialStatus,
                depositPercent = f.depositPercent.toInt(),
                discountAgorot = parseShekels(f.discount) ?: 0,
                deliveryMethod = f.deliveryMethod,
                alibabaOrderNumber = f.alibabaOrderNumber.trim().ifEmpty { null },
                alibabaOrderedOn = f.alibabaOrderedOn
                    ?: f.orderDate.takeIf { base == null && f.initialStatus != FulfillmentStatus.DRAFT },
                supplierName = f.supplierName.trim().ifEmpty { null },
                qualityOk = base?.qualityOk,
                deliveredAt = base?.deliveredAt,
                completedAt = base?.completedAt,
                notes = f.notes.trim().ifEmpty { null },
                shippingAddress = f.shippingAddress.trim().ifEmpty { null },
                addressSentToSupplier = f.addressSentToSupplier && f.shippingAddress.isNotBlank(),
                createdAt = base?.createdAt ?: now,
                updatedAt = now,
            )
            val items = f.items.map {
                val sold = it.quantitySold.toInt()
                OrderItemEntity(
                    orderId = entity.id, designId = null, designName = it.designName.trim(), pinType = it.pinType,
                    sizeMm = null, plating = null, quantityOrdered = it.quantityOrdered.ifBlank { it.quantitySold }.toInt(),
                    quantitySold = sold, unitPriceAgorot = parseShekels(it.unitPrice)!!, artworkUri = null,
                )
            }
            savedId.value = if (base == null) {
                val costs = parseShekels(f.alibabaPayment)?.takeIf { it > 0 }?.let {
                    listOf(OrderCostEntity(orderId = 0, type = CostType.ALIBABA_PAYMENT, amountAgorot = it, note = null))
                }.orEmpty()
                orders.createOrder(entity, items, costs)
            } else {
                orders.updateOrder(entity, items)
                entity.id
            }
        }
    }
}
