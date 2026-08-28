package dev.merlin.android.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Äquivalent zum `NWPathMonitor`-getriggerten Retry im iOS-Original, hier als
 * WorkManager-Job: robuster gegen Prozess-Tod als ein reiner
 * `ConnectivityManager.NetworkCallback`, läuft mit `NetworkType.CONNECTED`-
 * Constraint (siehe [SettingsSyncQueue.scheduleRetry]).
 */
@HiltWorker
class SettingsSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val queue: SettingsSyncQueue,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            queue.retryIfNeeded()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
