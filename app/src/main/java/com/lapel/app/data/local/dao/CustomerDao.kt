package com.lapel.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.lapel.app.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query(
        """
        SELECT * FROM customers
        WHERE name LIKE '%' || :query || '%' OR organization LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%'
        ORDER BY name COLLATE NOCASE
        """,
    )
    fun search(query: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    fun observe(id: Long): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun get(id: Long): CustomerEntity?

    @Insert suspend fun insert(customer: CustomerEntity): Long
    @Update suspend fun update(customer: CustomerEntity)
}
