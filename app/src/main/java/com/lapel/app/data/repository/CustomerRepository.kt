package com.lapel.app.data.repository

import com.lapel.app.data.local.LapelDatabase
import com.lapel.app.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerRepository @Inject constructor(
    db: LapelDatabase,
    private val clock: Clock,
) {
    private val customers = db.customerDao()

    fun observeAll(): Flow<List<CustomerEntity>> = customers.observeAll()
    fun search(query: String): Flow<List<CustomerEntity>> =
        if (query.isBlank()) customers.observeAll() else customers.search(query.trim())
    fun observe(id: Long): Flow<CustomerEntity?> = customers.observe(id)

    suspend fun save(customer: CustomerEntity): Long {
        val now = clock.instant()
        return if (customer.id == 0L) {
            customers.insert(customer.copy(createdAt = now, updatedAt = now))
        } else {
            customers.update(customer.copy(updatedAt = now))
            customer.id
        }
    }
}
