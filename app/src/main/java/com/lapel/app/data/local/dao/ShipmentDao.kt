package com.lapel.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lapel.app.data.local.entity.ShipmentEntity
import com.lapel.app.data.local.entity.TrackingEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShipmentDao {
    @Query("SELECT * FROM shipments WHERE orderId = :orderId ORDER BY createdAt")
    fun observeForOrder(orderId: Long): Flow<List<ShipmentEntity>>

    @Query("SELECT * FROM shipments WHERE orderId = :orderId")
    suspend fun getForOrder(orderId: Long): List<ShipmentEntity>

    @Query("SELECT * FROM shipments WHERE trackingActive = 1")
    suspend fun getActive(): List<ShipmentEntity>

    @Insert suspend fun insert(shipment: ShipmentEntity): Long
    @Update suspend fun update(shipment: ShipmentEntity)

    @Query("SELECT * FROM tracking_events WHERE shipmentId = :shipmentId ORDER BY occurredAt DESC")
    fun observeEvents(shipmentId: Long): Flow<List<TrackingEventEntity>>

    /** Re-polling returns the same events; duplicates are ignored by the unique index. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<TrackingEventEntity>)
}
