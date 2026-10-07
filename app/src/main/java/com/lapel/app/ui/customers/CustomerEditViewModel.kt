package com.lapel.app.ui.customers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lapel.app.data.local.entity.CustomerEntity
import com.lapel.app.data.repository.CustomerRepository
import com.lapel.app.ui.navigation.CustomerEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class CustomerForm(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val organization: String = "",
    val paymentTermsDays: String = "0",
    val notes: String = "",
    val customerSince: LocalDate,
    val showErrors: Boolean = false,
) {
    val nameError get() = name.isBlank()
    val termsError get() = paymentTermsDays.toIntOrNull()?.let { it < 0 } ?: true
    val isValid get() = !nameError && !termsError
}

@HiltViewModel
class CustomerEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CustomerRepository,
    clock: Clock,
) : ViewModel() {
    private val id = savedStateHandle.toRoute<CustomerEditRoute>().id
    val isNew = id == 0L
    private var original: CustomerEntity? = null

    val form = MutableStateFlow(CustomerForm(customerSince = LocalDate.now(clock)))
    val savedId = MutableStateFlow<Long?>(null)

    init {
        if (!isNew) viewModelScope.launch {
            repository.observe(id).first()?.let { c ->
                original = c
                form.value = CustomerForm(
                    name = c.name, phone = c.phone.orEmpty(), email = c.email.orEmpty(),
                    organization = c.organization.orEmpty(), paymentTermsDays = c.paymentTermsDays.toString(),
                    notes = c.notes.orEmpty(), customerSince = c.customerSince,
                )
            }
        }
    }

    fun edit(block: CustomerForm.() -> CustomerForm) = form.update(block)

    fun save() {
        val f = form.value
        if (!f.isValid) { form.update { it.copy(showErrors = true) }; return }
        viewModelScope.launch {
            val base = original
            val entity = CustomerEntity(
                id = base?.id ?: 0,
                name = f.name.trim(),
                phone = f.phone.trim().ifEmpty { null },
                email = f.email.trim().ifEmpty { null },
                organization = f.organization.trim().ifEmpty { null },
                paymentTermsDays = f.paymentTermsDays.toInt(),
                notes = f.notes.trim().ifEmpty { null },
                customerSince = f.customerSince,
                createdAt = base?.createdAt ?: java.time.Instant.EPOCH,
                updatedAt = base?.updatedAt ?: java.time.Instant.EPOCH,
            )
            savedId.value = repository.save(entity)
        }
    }
}
