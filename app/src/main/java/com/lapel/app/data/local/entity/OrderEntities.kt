package com.lapel.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lapel.domain.model.ChangeSource
import com.lapel.domain.model.CostType
import com.lapel.domain.model.DeliveryMethod
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.PinType
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "orders",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("customerId"), Index("fulfillmentStatus"), Index("orderDate"), Index("orderNumber")],
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    /** Keeps the existing numbering: "091", "008B", "102-148". */
    val orderNumber: String,
    val title: String,
    val orderDate: LocalDate,
    /** Delivery date promised to the client. */
    val dueDate: LocalDate?,
    val fulfillmentStatus: FulfillmentStatus,
    val depositPercent: Int = 50,
    /** Agreed discount or adjustment, taken off the selling total. */
    val discountAgorot: Long = 0,
    val deliveryMethod: DeliveryMethod,
    val alibabaOrderNumber: String?,
    val alibabaOrderedOn: LocalDate?,
    val supplierName: String?,
    /** "תקינות?" – the client received the pins in good condition. */
    val qualityOk: Boolean?,
    val deliveredAt: Instant?,
    val completedAt: Instant?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Where FedEx delivers; usually received from the customer about a week and a half after ordering. */
    val shippingAddress: String? = null,
    /** The address was passed on to the manufacturer. */
    @ColumnInfo(defaultValue = "0") val addressSentToSupplier: Boolean = false,
)

@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DesignEntity::class,
            parentColumns = ["id"],
            childColumns = ["designId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("orderId"), Index("designId")],
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val designId: Long?,
    val designName: String,
    val pinType: PinType,
    val sizeMm: Int?,
    val plating: String?,
    /** Ordered from the supplier, spares included. */
    val quantityOrdered: Int,
    /** Billed to the client. */
    val quantitySold: Int,
    val unitPriceAgorot: Long,
    val artworkUri: String?,
)

@Entity(
    tableName = "order_costs",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("orderId")],
)
data class OrderCostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val type: CostType,
    val amountAgorot: Long,
    val note: String?,
)

@Entity(
    tableName = "status_changes",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("orderId")],
)
data class StatusChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val fromStatus: FulfillmentStatus?,
    val toStatus: FulfillmentStatus,
    val source: ChangeSource,
    val at: Instant,
)
