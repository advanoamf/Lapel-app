package com.lapel.app.data.local

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/** Instants are stored as epoch millis, calendar dates as epoch days. Enums are stored by name by Room. */
class Converters {
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun localDateToLong(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun longToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
}
