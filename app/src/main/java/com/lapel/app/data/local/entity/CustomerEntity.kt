package com.lapel.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "customers", indices = [Index("name")])
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Used for call / WhatsApp. */
    val phone: String?,
    val email: String?,
    /** Branch / organization – "סניף". */
    val organization: String?,
    /** 0 = pay on delivery; 60 = "שוטף+60". */
    val paymentTermsDays: Int = 0,
    val notes: String?,
    /** Editable "customer since" date, defaults to the day the customer was added. */
    val customerSince: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,
)
