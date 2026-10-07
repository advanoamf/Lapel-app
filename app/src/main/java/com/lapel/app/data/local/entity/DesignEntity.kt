package com.lapel.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A pin design that can be ordered again (reorders carry no mold cost) or kept in stock. */
@Entity(tableName = "designs", indices = [Index("name")])
data class DesignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val artworkUri: String?,
    val supplierName: String?,
    /** The order on which the mold was paid; later orders of this design have no mold cost. */
    val moldPaidOnOrderId: Long?,
    val notes: String?,
)
