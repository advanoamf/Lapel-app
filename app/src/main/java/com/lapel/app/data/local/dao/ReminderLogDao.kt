package com.lapel.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lapel.app.data.local.entity.ReminderLogEntity

@Dao
interface ReminderLogDao {
    @Query("SELECT * FROM reminder_log")
    suspend fun getAll(): List<ReminderLogEntity>

    @Insert suspend fun insert(entry: ReminderLogEntity)
}
