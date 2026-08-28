package dev.merlin.android.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Äquivalent zum `NWPathMonitor`-getriggerten Drain im iOS-Original, hier als
 * WorkManager-Job (siehe [MutationDrainWorker] für das analoge Pendant der
 * Artikel-Mutationen).
 */
@HiltWorker
class HighlightMutationDrainWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val queue: OfflineHighlightQueue,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            queue.drain()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
