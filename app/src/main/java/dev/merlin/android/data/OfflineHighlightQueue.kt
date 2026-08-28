package dev.merlin.android.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.merlin.android.models.Highlight
import dev.merlin.android.models.HighlightCreate
import dev.merlin.android.network.MerlinApi
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Äquivalent zu `OfflineHighlightQueue.swift`: speichert Highlight-Mutationen,
 * die wegen fehlender Verbindung nicht sofort an den Server geschickt werden
 * konnten, persistent in Room (statt `UserDefaults`) und spielt sie beim
 * nächsten Drain ein (WorkManager-`NetworkType.CONNECTED`-Constraint statt
 * `NWPathMonitor`).
 *
 * Kritische Dedup-Regel 1:1 vom iOS-Original übernommen: Wird ein offline
 * erstelltes Highlight (noch mit lokaler Platzhalter-ID, [tempId]) gelöscht,
 * bevor die Create-Mutation gedraint wurde, wird einfach die wartende
 * `CREATE`-Mutation entfernt statt eine zum Scheitern verurteilte `DELETE`
 * einzureihen ([cancelPendingCreate]).
 */
@Singleton
class OfflineHighlightQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mutationDao: PendingHighlightMutationDao,
    private val api: MerlinApi,
    private val highlightCacheService: HighlightCacheService,
    private val json: Json,
) {
    private val mutex = Mutex()

    /** Wird nach jedem nicht-leeren Drain aufgerufen, damit die UI sich aktualisieren kann. */
    var onDrained: (suspend () -> Unit)? = null

    suspend fun isEmpty(): Boolean = mutationDao.count() == 0

    /**
     * Legt ein Highlight optimistisch im Cache an (negative Platzhalter-ID,
     * da Server-IDs positiv sind) und reiht die Create-Mutation ein. Gibt das
     * optimistische [Highlight] zurück, damit die UI es sofort anzeigen kann.
     */
    suspend fun enqueueCreate(articleId: Int, payload: HighlightCreate): Highlight = mutex.withLock {
        val tempId = "tmp_${UUID.randomUUID()}"
        val localId = -(tempId.hashCode().absoluteValue.coerceAtLeast(1))
        val optimistic = Highlight(
            id = localId,
            articleId = articleId,
            highlightedText = payload.highlightedText,
            startXpath = payload.startXpath,
            startOffset = payload.startOffset,
            endXpath = payload.endXpath,
            endOffset = payload.endOffset,
            color = payload.color,
            createdAt = Instant.now().toString(),
        )
        highlightCacheService.upsert(optimistic)
        mutationDao.insert(
            PendingHighlightMutationEntity(
                id = UUID.randomUUID().toString(),
                articleId = articleId,
                kind = PendingHighlightMutationKind.CREATE,
                tempId = tempId,
                localHighlightId = localId,
                payloadJson = json.encodeToString(payload),
            ),
        )
        optimistic
    }

    /**
     * Entfernt [highlight] aus dem Cache und queued die Löschung. Ist
     * [highlight] noch nicht serverseitig angelegt (negative, lokale ID),
     * wird stattdessen die wartende `CREATE`-Mutation storniert
     * ([cancelPendingCreate]) statt eine doomed `DELETE` einzureihen.
     */
    suspend fun enqueueDelete(highlight: Highlight) = mutex.withLock {
        highlightCacheService.remove(highlight.id, highlight.articleId)
        if (highlight.id < 0) {
            val pendingCreate = mutationDao.getAll()
                .firstOrNull { it.kind == PendingHighlightMutationKind.CREATE && it.localHighlightId == highlight.id }
            if (pendingCreate != null) {
                mutationDao.deleteById(pendingCreate.id)
                return@withLock
            }
        }
        mutationDao.insert(
            PendingHighlightMutationEntity(
                id = UUID.randomUUID().toString(),
                articleId = highlight.articleId,
                kind = PendingHighlightMutationKind.DELETE,
                highlightId = highlight.id,
            ),
        )
    }

    /**
     * Spielt alle wartenden Mutationen ein. Anders als bei
     * [OfflineMutationQueue] gibt es hier keine Paritäts-/Last-write-wins-
     * Berechnung – jede Highlight-Mutation ist unabhängig und wird in
     * Einreihungsreihenfolge ausgeführt. Die Queue wird vor Ausführung
     * geleert, damit währenddessen neu eingereihte Mutationen nicht
     * verloren gehen.
     */
    suspend fun drain() {
        val pending = mutex.withLock { mutationDao.getAll() }
        if (pending.isEmpty()) return

        mutex.withLock { mutationDao.clear() }

        val failed = mutableListOf<PendingHighlightMutationEntity>()
        for (mutation in pending) {
            val ok = try {
                execute(mutation)
                true
            } catch (e: HttpException) {
                // 404 beim Löschen heißt: Highlight ist bereits weg – kein Fehler, kein Retry
                // (Äquivalent zur 404-Behandlung in OfflineMutationQueue/MerlinAPI.swift).
                mutation.kind == PendingHighlightMutationKind.DELETE && e.code() == 404
            } catch (e: Exception) {
                false
            }
            if (!ok) failed += mutation
        }

        if (failed.isNotEmpty()) {
            mutex.withLock { mutationDao.insertAll(failed) }
        }

        onDrained?.invoke()
    }

    private suspend fun execute(mutation: PendingHighlightMutationEntity) {
        when (mutation.kind) {
            PendingHighlightMutationKind.CREATE -> {
                val payload = mutation.payloadJson?.let {
                    runCatching { json.decodeFromString<HighlightCreate>(it) }.getOrNull()
                } ?: return
                val real = api.createHighlight(mutation.articleId, payload)
                if (mutation.localHighlightId != null) {
                    highlightCacheService.remove(mutation.localHighlightId, mutation.articleId)
                }
                highlightCacheService.upsert(real)
            }
            PendingHighlightMutationKind.DELETE -> {
                val id = mutation.highlightId ?: return
                api.deleteHighlight(id)
            }
        }
    }

    /** Stößt einen einmaligen, netzwerkabhängigen Drain-Job über WorkManager an. */
    fun scheduleDrain() {
        val request = OneTimeWorkRequestBuilder<HighlightMutationDrainWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "highlight_mutation_drain",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
