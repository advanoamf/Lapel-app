package com.lapel.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.lapel.app.data.local.entity.PaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE orderId = :orderId ORDER BY receivedOn, id")
    fun observeForOrder(orderId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE stockSaleId = :saleId ORDER BY receivedOn, id")
    fun observeForStockSale(saleId: Long): Flow<List<PaymentEntity>>

    @Insert suspend fun insert(payment: PaymentEntity): Long
    @Delete suspend fun delete(payment: PaymentEntity)
}
