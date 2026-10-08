package com.lapel.app.di

import android.content.Context
import androidx.room.Room
import com.lapel.app.data.local.ALL_MIGRATIONS
import com.lapel.app.data.local.LapelDatabase
import com.lapel.app.data.sync.SyncTables
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
        Room.databaseBuilder(context, LapelDatabase::class.java, LapelDatabase.NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .addCallback(SyncTables.onCreate)
            .build()

    @Provides
    @Singleton
    fun provideSyncApi(api: com.lapel.app.data.sync.HttpSyncApi): com.lapel.app.data.sync.SyncApi = api

    /** Business dates (order day, overdue) are always in Israel time. */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.system(ZoneId.of("Asia/Jerusalem"))
}
