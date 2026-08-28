package dev.merlin.android.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.merlin.android.network.MerlinApi
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Äquivalent zu `OfflineMutationQueue.swift`: speichert Mutationen, die wegen
 * fehlender Verbindung nicht sofort an den Server geschickt werden konnten,
 * persistent in Room (überlebt Prozess-Tod, anders als eine reine In-Memory-
 * Queue) und spielt sie beim nächsten Drain (Reconnect, App-Start, manueller
 * Trigger) ein. Dedup-/Drain-Semantik 1:1 vom iOS-Original übernommen:
 * - `delete` verdrängt alle anderen für den Artikel wartenden Mutationen und
 *   ist selbst nicht verdrängbar.
 * - `setTags` merged Add-/Remove-Deltas pro Artikel (kein Diff gegen ein
 *   volles Ziel-Set mehr, siehe [enqueueSetTags]/[applyTagDelta]); wird
 *   übersprungen, wenn bereits ein `delete` für den Artikel wartet.
 * - `toggleArchive`/`toggleFavorite` werden beim Enqueue einfach angehängt
 *   (keine sofortige Stornierung); beim Drain wird pro Artikel die Parität
 *   gezählt – gerade Anzahl = Netto-No-Op (kein API-Call), ungerade Anzahl =
 *   genau ein API-Call.
 * - Die Queue wird VOR Ausführung der berechneten Mutationen geleert, damit
 *   währenddessen neu eingereihte Mutationen nicht verloren gehen.
 * - Fehlgeschlagene Mutationen werden unverändert wieder angehängt (ohne
 *   erneute Dedup-Anwendung).
 */
@Singleton
class OfflineMutationQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mutationDao: MutationDao,
    private val api: MerlinApi,
    private val articleCacheService: ArticleCacheService,
    private val imageCacheService: ImageCacheService,
    private val json: Json,
) {
    private val mutex = Mutex()

    /** Wird nach jedem nicht-leeren Drain aufgerufen, damit die UI sich aktualisieren kann. */
    var onDrained: (suspend () -> Unit)? = null

    suspend fun isEmpty(): Boolean = mutationDao.count() == 0

    suspend fun enqueueToggleArchive(articleId: Int) = mutex.withLock {
        mutationDao.insert(
            PendingMutationEntity(id = UUID.randomUUID().toString(), articleId = articleId, kind = PendingMutationKind.TOGGLE_ARCHIVE),
        )
    }

    suspend fun enqueueToggleFavorite(articleId: Int) = mutex.withLock {
        mutationDao.insert(
            PendingMutationEntity(id = UUID.randomUUID().toString(), articleId = articleId, kind = PendingMutationKind.TOGGLE_FAVORITE),
        )
    }

    /**
     * Merged mit einer bereits wartenden SET_TAGS-Mutation für denselben Artikel
     * statt sie zu überschreiben (Last-write-wins auf Delta-Ebene): ein späteres
     * Add hebt ein wartendes Remove derselben Tag-ID auf und umgekehrt. Entfällt,
     * falls bereits ein `delete` wartet.
     */
    suspend fun enqueueSetTags(articleId: Int, addTagIds: List<Int>, removeTagIds: List<Int>) = mutex.withLock {
        val pending = mutationDao.getAll().filter { it.articleId == articleId }
        if (pending.any { it.kind == PendingMutationKind.DELETE }) return@withLock

        val existing = pending.firstOrNull { it.kind == PendingMutationKind.SET_TAGS }
        val existingAdd = existing?.addTagIdsJson?.let { runCatching { json.decodeFromString<List<Int>>(it) }.getOrNull() }?.toSet() ?: emptySet()
        val existingRemove = existing?.removeTagIdsJson?.let { runCatching { json.decodeFromString<List<Int>>(it) }.getOrNull() }?.toSet() ?: emptySet()
        val newAdd = addTagIds.toSet()
        val newRemove = removeTagIds.toSet()

        val mergedAdd = (existingAdd - newRemove) + newAdd
        val mergedRemove = (existingRemove - newAdd) + newRemove

        if (existing != null) mutationDao.deleteByArticleAndKind(articleId, PendingMutationKind.SET_TAGS)
        if (mergedAdd.isEmpty() && mergedRemove.isEmpty()) return@withLock

        mutationDao.insert(
            PendingMutationEntity(
                id = UUID.randomUUID().toString(),
                articleId = articleId,
                kind = PendingMutationKind.SET_TAGS,
                addTagIdsJson = json.encodeToString(mergedAdd.toList()),
                removeTagIdsJson = json.encodeToString(mergedRemove.toList()),
            ),
        )
    }

    /** `delete` verdrängt alle anderen wartenden Mutationen für den Artikel. */
    suspend fun enqueueDelete(articleId: Int) = mutex.withLock {
        mutationDao.deleteByArticle(articleId)
        mutationDao.insert(
            PendingMutationEntity(id = UUID.randomUUID().toString(), articleId = articleId, kind = PendingMutationKind.DELETE),
        )
    }

    /**
     * Spielt alle wartenden Mutationen ein. Berechnet zuerst pro Artikel die
     * effektive Mutation (Paritäts-/Last-write-wins-Regeln), leert dann die
     * Queue und führt erst danach die API-Calls aus.
     */
    suspend fun drain() {
        val pending = mutex.withLock { mutationDao.getAll() }
        if (pending.isEmpty()) return

        val byArticle = pending.groupBy { it.articleId }
        val effective = byArticle.mapValues { (_, mutations) -> computeEffective(mutations) }

        mutex.withLock { mutationDao.clear() }

        val failed = mutableListOf<PendingMutationEntity>()
        for ((articleId, mutations) in effective) {
            for (mutation in mutations) {
                val ok = try {
                    execute(articleId, mutation)
                    true
                } catch (e: HttpException) {
                    // 404 beim Löschen heißt: Artikel ist bereits weg – kein Fehler, kein Retry
                    // (Äquivalent zu MerlinAPIError.notFound im iOS-Original).
                    mutation.kind == PendingMutationKind.DELETE && e.code() == 404
                } catch (e: Exception) {
                    false
                }
                if (!ok) failed += mutation
            }
        }

        if (failed.isNotEmpty()) {
            mutex.withLock { mutationDao.insertAll(failed) }
        }

        onDrained?.invoke()
    }

    /** Liefert pro Artikel die Liste der tatsächlich auszuführenden Mutationen. */
    private fun computeEffective(mutations: List<PendingMutationEntity>): List<PendingMutationEntity> {
        val delete = mutations.lastOrNull { it.kind == PendingMutationKind.DELETE }
        if (delete != null) return listOf(delete)

        val result = mutableListOf<PendingMutationEntity>()

        val setTags = mutations.lastOrNull { it.kind == PendingMutationKind.SET_TAGS }
        if (setTags != null) result += setTags

        val archiveCount = mutations.count { it.kind == PendingMutationKind.TOGGLE_ARCHIVE }
        if (archiveCount % 2 == 1) result += mutations.last { it.kind == PendingMutationKind.TOGGLE_ARCHIVE }

        val favoriteCount = mutations.count { it.kind == PendingMutationKind.TOGGLE_FAVORITE }
        if (favoriteCount % 2 == 1) result += mutations.last { it.kind == PendingMutationKind.TOGGLE_FAVORITE }

        return result
    }

    private suspend fun execute(articleId: Int, mutation: PendingMutationEntity) {
        when (mutation.kind) {
            PendingMutationKind.TOGGLE_ARCHIVE -> {
                api.toggleArchive(articleId)
                refreshArticle(articleId)
            }
            PendingMutationKind.TOGGLE_FAVORITE -> {
                api.toggleFavorite(articleId)
                refreshArticle(articleId)
            }
            PendingMutationKind.SET_TAGS -> {
                val addIds = mutation.addTagIdsJson?.let { runCatching { json.decodeFromString<List<Int>>(it) }.getOrNull() } ?: emptyList()
                val removeIds = mutation.removeTagIdsJson?.let { runCatching { json.decodeFromString<List<Int>>(it) }.getOrNull() } ?: emptyList()
                applyTagDelta(articleId, addIds, removeIds)
                refreshArticle(articleId)
            }
            PendingMutationKind.DELETE -> {
                api.deleteArticle(articleId)
                articleCacheService.remove(articleId)
                imageCacheService.evict(articleId)
            }
        }
    }

    /**
     * Wendet genau [addTagIds]/[removeTagIds] an, gegen den aktuellen Server-
     * Stand gefiltert. Bewusst KEIN Diff gegen ein volles Ziel-Set mehr: das
     * würde jedes Tag entfernen, das zwischenzeitlich von einem anderen
     * Client hinzugefügt wurde, da es im alten Ziel-Set nicht vorkäme (der
     * eigentliche Tag-Overwrite-Bug). Der Server hat außerdem keinen Unique-
     * Index auf (article_id, tag_id) – ein Add auf ein bereits vorhandenes
     * Tag würde also eine doppelte Zeile anlegen, daher der current-Filter.
     */
    private suspend fun applyTagDelta(articleId: Int, addTagIds: List<Int>, removeTagIds: List<Int>) {
        val article = api.getArticle(articleId)
        val current = article.tags.map { it.id }.toSet()
        (addTagIds.toSet() - current).forEach { api.addTagToArticle(articleId, it) }
        (removeTagIds.toSet() intersect current).forEach { api.removeTagFromArticle(articleId, it) }
    }

    private suspend fun refreshArticle(articleId: Int) {
        val article = api.getArticle(articleId)
        articleCacheService.upsert(article)
    }

    /** Stößt einen einmaligen, netzwerkabhängigen Drain-Job über WorkManager an. */
    fun scheduleDrain() {
        val request = OneTimeWorkRequestBuilder<MutationDrainWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "mutation_drain",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
