package dev.merlin.android.data

import dev.merlin.android.models.Article
import dev.merlin.android.models.ArticleFilter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Äquivalent zu `ArticleCacheService.swift`: persistiert Artikel (inkl.
 * vollständigem Inhalt) lokal, damit die App offline benutzbar bleibt.
 *
 * **Eviction-Policy:** Artikel bleiben [PreferencesStore.cacheRetentionDays]
 * Tage seit ihrem letzten lokalen Sync (Upsert) erhalten – einheitlich für
 * ALLE Artikel unabhängig von Archiv-/Favoriten-Status, nutzerkonfigurierbar
 * über Settings → "Artikel offline speichern" (0–365 Tage). 0 Tage bedeutet,
 * dass nichts über die aktuelle Sitzung hinaus vorgehalten wird. Gelöschte
 * Artikel werden sofort entfernt. Der Cache ist ein additiver Merge nach
 * Artikel-ID — das Laden der Unread-Liste verwirft nicht bereits gecachte
 * Favoriten usw.
 *
 * Anders als das iOS-Original (Swift `actor`, JSON-Datei) liegt die
 * Persistenz hier in Room (siehe [ArticleDao]); die Filter-/Eviction-Logik
 * selbst ist bewusst 1:1 in Kotlin nachgebaut, nicht in SQL verlagert, damit
 * sie exakt dieselben Ergebnisse wie `ArticlesViewModel.fetchForFilter`
 * liefert. Eine [Mutex] ersetzt den Swift-`actor`-Isolationsmechanismus.
 */
@Singleton
class ArticleCacheService @Inject constructor(
    private val articleDao: ArticleDao,
    private val json: Json,
    private val preferencesStore: PreferencesStore,
) {
    private val mutex = Mutex()

    /** Liefert gecachte Artikel passend zu [filter]/[tagScope], nach vorheriger Eviction.
     * [tagScope] ist in der Einzel-Tag-Ansicht der gewählte Tag plus seine Unter-Tags
     * (siehe [dev.merlin.android.models.TagTree.scope]), sonst `null`.
     * [showArchivedForTag] entscheidet nur bei gesetztem [tagScope], ob archivierte
     * Artikel mitgezählt werden (Äquivalent zu iOS' `showArchivedForTag`, siehe
     * `ArticleCacheService.swift`). Sortierung wie `ArticlesViewModel.sortedForFilter`. */
    suspend fun loadFiltered(
        filter: ArticleFilter,
        tagScope: Set<Int>?,
        showArchivedForTag: Boolean = false,
    ): List<Article> = mutex.withLock {
        evictExpiredInternal()
        val matching = articleDao.getAll()
            .mapNotNull { decode(it.json) }
            .filter { filter.matches(it, tagScope, showArchivedForTag) }
        if (tagScope != null) return@withLock matching.sortedByDescending { it.createdAt }
        when (filter.kind) {
            ArticleFilter.Kind.CONTINUE -> matching.sortedByDescending { it.scrollUpdatedAt }
            ArticleFilter.Kind.FAVORITES -> matching.sortedByDescending { it.favoritedAt ?: "" }
            ArticleFilter.Kind.ARCHIVE -> matching.sortedByDescending { it.archivedAt ?: "" }
            ArticleFilter.Kind.UNREAD -> matching.sortedByDescending { it.createdAt }
        }
    }

    /** Alle gecachten Artikel, unabhängig von Filter und Archiv-Status. */
    suspend fun loadAllCached(): List<Article> = mutex.withLock {
        evictExpiredInternal()
        articleDao.getAll().mapNotNull { decode(it.json) }
    }

    /** Einzelner gecachter Artikel (Reader offline, Deep-Link). */
    suspend fun article(id: Int): Article? = loadAllCached().firstOrNull { it.id == id }

    /**
     * Merged einen Batch von Artikeln in den Cache. Setzt `cachedAt` für jeden
     * Eintrag neu – die Retention zählt ab dem letzten Sync, nicht ab dem
     * allerersten Cachen.
     */
    suspend fun upsert(articles: List<Article>) {
        if (articles.isEmpty()) return
        mutex.withLock {
            val now = System.currentTimeMillis()
            articleDao.upsertAll(
                // supportBox nie persistieren: Login-Status/Akzentfarbe können sich ändern, die Box wird bei
                // jedem Einzelabruf frisch vom Server geliefert (siehe Article.supportBox).
                articles.map { ArticleEntity(id = it.id, json = json.encodeToString(it.copy(supportBox = null)), cachedAt = now) },
            )
        }
    }

    suspend fun upsert(article: Article) = upsert(listOf(article))

    /** Entfernt einen einzelnen Artikel (nach permanentem Löschen). */
    suspend fun remove(id: Int) = mutex.withLock {
        articleDao.deleteById(id)
    }

    /** Stößt explizit die Retention-Eviction an (z. B. einmalig beim App-Start). */
    suspend fun evict() = mutex.withLock {
        evictExpiredInternal()
    }

    /** Leert den gesamten Cache (Logout/Account-Wechsel). */
    suspend fun clear() = mutex.withLock {
        articleDao.clearAll()
    }

    // MARK: – Eviction

    private suspend fun evictExpiredInternal() {
        val retentionDays = preferencesStore.getCacheRetentionDays()
        val cutoffMillis = System.currentTimeMillis() - retentionDays * 86_400_000L
        val expiredIds = articleDao.getAll().mapNotNull { entity ->
            if (entity.cachedAt < cutoffMillis) entity.id else null
        }
        if (expiredIds.isNotEmpty()) articleDao.deleteByIds(expiredIds)
    }

    private fun decode(value: String): Article? = runCatching { json.decodeFromString<Article>(value) }.getOrNull()
}
