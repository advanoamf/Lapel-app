package com.lapel.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lapel.domain.model.CostType
import java.time.LocalDate

/** One bulk purchase of a design kept in stock and sold a few pins at a time. */
@Entity(
    tableName = "stock_batches",
    foreignKeys = [
        ForeignKey(
            entity = DesignEntity::class,
            parentColumns = ["id"],
            childColumns = ["designId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("designId")],
)
data class StockBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val designId: Long,
    val quantityReceived: Int,
    /** Defective / given away. */
    val writtenOff: Int = 0,
    val purchasedOn: LocalDate,
    val notes: String?,
)

@Entity(
    tableName = "stock_batch_costs",
    foreignKeys = [
        ForeignKey(
            entity = StockBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("batchId")],
)
data class StockBatchCostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val type: CostType,
    val amountAgorot: Long,
    val note: String?,
)

@Entity(
    tableName = "stock_sales",
    foreignKeys = [
        ForeignKey(
            entity = StockBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("batchId"), Index("customerId")],
)
data class StockSaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val customerId: Long,
    val soldOn: LocalDate,
    val quantity: Int,
    val unitPriceAgorot: Long,
    /** Postage the buyer paid (e.g. ₪20); 0 for pickup. */
    val shippingChargedAgorot: Long,
    /** What the post office actually cost. */
    val shippingCostAgorot: Long,
    /** Price reduction agreed with this buyer. */
    val discountAgorot: Long = 0,
    val sent: Boolean,
    val arrived: Boolean,
    /** Israel Post number ("RR…IL"); shown and copied, not tracked automatically. */
    val postTrackingNumber: String?,
    val notes: String?,
)
