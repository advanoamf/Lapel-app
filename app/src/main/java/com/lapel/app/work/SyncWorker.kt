package com.lapel.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lapel.app.data.sync.NotLoggedInException
import com.lapel.app.data.sync.SyncEngine
import com.lapel.app.data.sync.SyncSettings
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Syncs with the server in the background. "Local only" runs skip the work when nothing changed here. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
    private val settings: SyncSettings,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settings.isConfigured) return Result.success()
        if (inputData.getBoolean(KEY_LOCAL_ONLY, false) && engine.pendingChanges() == 0) return Result.success()
        return try {
            engine.sync()
            Result.success()
        } catch (_: NotLoggedInException) {
            Result.failure()
        } catch (_: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_LOCAL_ONLY = "localOnly"
    }
}

object SyncScheduler {
    private const val PERIODIC = "sync-periodic"
    private const val NOW = "sync-now"
    private const val SOON = "sync-soon"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Full sync (send and receive) as soon as there is a connection. */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    /** Sends local edits shortly after they happen; repeated edits push it back (debounce). */
    fun syncSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(network)
            .setInitialDelay(15, TimeUnit.SECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_LOCAL_ONLY to true))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SOON, ExistingWorkPolicy.REPLACE, request)
    }
}
