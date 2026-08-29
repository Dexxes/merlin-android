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

    /** Liefert gecachte Artikel passend zu [filter]/[tagId], nach vorheriger Eviction.
     * [showArchivedForTag] entscheidet nur bei gesetztem [tagId], ob archivierte
     * Artikel mitgezählt werden (Äquivalent zu iOS' `showArchivedForTag`, siehe
     * `ArticleCacheService.swift`). */
    suspend fun loadFiltered(
        filter: ArticleFilter,
        tagId: Int?,
        showArchivedForTag: Boolean = false,
    ): List<Article> = mutex.withLock {
        evictExpiredInternal()
        val matching = articleDao.getAll()
            .mapNotNull { decode(it.json) }
            .filter { matches(it, filter, tagId, showArchivedForTag) }
        if (filter == ArticleFilter.PAGES_FAVORITES || filter == ArticleFilter.VIDEOS_FAVORITES) {
            matching.sortedByDescending { it.favoritedAt ?: "" }
        } else {
            matching.sortedByDescending { it.createdAt }
        }
    }

    /**
     * Alle gecachten Artikel ohne jeden Filter (unabhängig von Archiv-/Favoriten-
     * Status oder Seiten/Videos-Kategorie) - für Fallback-Lookups, die über die
     * sechs [ArticleFilter]-Ansichten hinausgehen (Bilder-Prefetch beim App-
     * Start, Offline-Fallback im Reader per Artikel-ID).
     */
    suspend fun loadAllCached(): List<Article> = mutex.withLock {
        articleDao.getAll().mapNotNull { decode(it.json) }
    }

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
                articles.map { ArticleEntity(id = it.id, json = json.encodeToString(it), cachedAt = now) },
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

    // MARK: – Filter-Replikation (spiegelt ArticlesViewModel.fetchForFilter)

    private fun matches(article: Article, filter: ArticleFilter, tagId: Int?, showArchivedForTag: Boolean = false): Boolean {
        if (tagId != null) {
            if (article.tags.none { it.id == tagId }) return false
            // Einzel-Tag-Ansicht ignoriert den aktiven Filter komplett (siehe
            // `ArticlesViewModel.fetchForFilter`) – stattdessen entscheidet
            // `showArchivedForTag`, ob archivierte Artikel mitgezählt werden.
            return showArchivedForTag || !article.isArchived
        }
        val isVideo = article.category == "Video"
        return when (filter) {
            ArticleFilter.PAGES_UNREAD -> !article.isArchived && !isVideo
            // Bewusst OHNE isArchived-Bedingung: Favoriten unabhängig vom Archiv-Status.
            ArticleFilter.PAGES_FAVORITES -> article.isFavorite && !isVideo
            ArticleFilter.PAGES_ARCHIVE -> article.isArchived && !isVideo
            ArticleFilter.VIDEOS_UNREAD -> !article.isArchived && isVideo
            ArticleFilter.VIDEOS_FAVORITES -> article.isFavorite && isVideo
            ArticleFilter.VIDEOS_ARCHIVE -> article.isArchived && isVideo
        }
    }

    private fun decode(value: String): Article? = runCatching { json.decodeFromString<Article>(value) }.getOrNull()
}
