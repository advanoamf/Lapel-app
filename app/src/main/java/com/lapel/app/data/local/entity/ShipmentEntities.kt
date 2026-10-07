package com.lapel.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lapel.domain.model.ReminderType
import com.lapel.domain.model.ShipmentStatus
import java.time.Instant

@Entity(
    tableName = "shipments",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("orderId"), Index(value = ["trackingNumber"], unique = true)],
)
data class ShipmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val trackingNumber: String,
    val status: ShipmentStatus,
    /** FedEx's own description, shown as-is. */
    val statusDescription: String?,
    val lastEventAt: Instant?,
    val shippedAt: Instant?,
    val deliveredAt: Instant?,
    val estimatedDelivery: Instant?,
    /** Proof-of-delivery signature name. */
    val receivedBy: String?,
    val lastSyncedAt: Instant?,
    val syncError: String?,
    /** False once delivered or tracking stopped; inactive shipments are not polled. */
    val trackingActive: Boolean = true,
    val createdAt: Instant,
)

@Entity(
    tableName = "tracking_events",
    foreignKeys = [
        ForeignKey(
            entity = ShipmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["shipmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // Re-polling the same FedEx data must not create duplicates.
    indices = [Index(value = ["shipmentId", "occurredAt", "eventCode"], unique = true)],
)
data class TrackingEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipmentId: Long,
    val occurredAt: Instant,
    val eventCode: String,
    val description: String,
    val location: String?,
)

@Entity(tableName = "reminder_log", indices = [Index(value = ["orderId", "type"])])
data class ReminderLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val type: ReminderType,
    val sentAt: Instant,
    val snoozedUntil: Instant?,
)
