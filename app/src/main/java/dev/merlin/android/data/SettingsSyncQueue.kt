package dev.merlin.android.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.merlin.android.network.MerlinApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Äquivalent zu `SettingsSyncQueue.swift`: holt einen wegen fehlender Verbindung
 * fehlgeschlagenen `updateSettings`-Push nach, sobald das Netz zurückkehrt.
 *
 * Anders als [OfflineMutationQueue]/[OfflineHighlightQueue] muss diese Queue sich
 * nicht merken, *was* sich geändert hat: Settings werden immer als ein flaches
 * Snapshot-Objekt gepusht ([PreferencesStore.toServerSettings]), es reicht also,
 * sich zu merken, *dass* ein Push aussteht, und beim Retry den dann aktuellen
 * lokalen Stand zu senden – das fasst beliebig viele Offline-Änderungen automatisch
 * zu einem PUT zusammen.
 */
@Singleton
class SettingsSyncQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesStore: PreferencesStore,
    private val api: MerlinApi,
) {
    private val mutex = Mutex()

    /** Ob ein vorheriger Push fehlgeschlagen und noch an den Server zu übermitteln ist. */
    suspend fun isDirty(): Boolean = preferencesStore.needsSettingsSync()

    /** Merkt einen ausstehenden Push vor – aufgerufen, wenn `updateSettings` mit einem `IOException` fehlschlägt. */
    suspend fun markDirty() = preferencesStore.setNeedsSettingsSync(true)

    /**
     * Holt einen ausstehenden Push mit dem *aktuellen* lokalen Stand nach. Bleibt bei
     * erneutem Fehlschlag markiert, damit der nächste Reconnect es erneut versucht.
     */
    suspend fun retryIfNeeded() = mutex.withLock {
        if (!preferencesStore.needsSettingsSync()) return@withLock
        runCatching { api.updateSettings(preferencesStore.toServerSettings()) }
            .onSuccess { preferencesStore.setNeedsSettingsSync(false) }
    }

    /** Stößt einen einmaligen, netzwerkabhängigen Retry-Job über WorkManager an (siehe [SettingsSyncWorker]). */
    fun scheduleRetry() {
        val request = OneTimeWorkRequestBuilder<SettingsSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "settings_sync",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
