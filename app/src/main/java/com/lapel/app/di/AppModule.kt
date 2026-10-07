package com.lapel.app.di

import android.content.Context
import androidx.room.Room
import com.lapel.app.data.local.LapelDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LapelDatabase =
        Room.databaseBuilder(context, LapelDatabase::class.java, LapelDatabase.NAME).build()

    /** Business dates (order day, overdue) are always in Israel time. */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.system(ZoneId.of("Asia/Jerusalem"))
}
