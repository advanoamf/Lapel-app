package com.lapel.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lapel.domain.model.PaymentMethod
import com.lapel.domain.model.PaymentMilestone
import java.time.Instant
import java.time.LocalDate

/** A payment recorded by hand. Belongs to either an order or a stock sale. */
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = StockSaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["stockSaleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["uid"], unique = true), Index("orderId"), Index("stockSaleId")],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long?,
    val stockSaleId: Long?,
    val amountAgorot: Long,
    val method: PaymentMethod,
    val milestone: PaymentMilestone,
    val receivedOn: LocalDate,
    /** Bank reference / Bit confirmation. */
    val reference: String?,
    val createdAt: Instant,
    /** Sync id shared with the server; set by a database trigger. */
    val uid: String? = null,
    /** When this row last changed (ms); set by a database trigger. */
    @ColumnInfo(defaultValue = "0") val syncUpdatedAt: Long = 0,
    /** Changed here and not yet sent to the server; set by a database trigger. */
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
)
