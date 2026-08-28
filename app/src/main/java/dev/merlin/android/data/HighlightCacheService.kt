package dev.merlin.android.data

import dev.merlin.android.models.Highlight
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Äquivalent zu `HighlightCacheService.swift`: persistiert Highlights lokal,
 * gruppiert nach Artikel, damit der Reader auch offline angezeigte
 * Markierungen kennt. Anders als das iOS-Original (Swift `actor`, eine
 * JSON-Datei für alle Artikel) liegt die Persistenz hier in Room (siehe
 * [HighlightDao]), eine Zeile pro Artikel – ein [Mutex] ersetzt die
 * `actor`-Isolation.
 */
@Singleton
class HighlightCacheService @Inject constructor(
    private val highlightDao: HighlightDao,
    private val json: Json,
) {
    private val mutex = Mutex()

    suspend fun highlights(articleId: Int): List<Highlight> = mutex.withLock {
        loadInternal(articleId)
    }

    /** Ersetzt den gesamten Highlight-Bestand eines Artikels (z. B. nach Server-Fetch). */
    suspend fun replaceAll(highlights: List<Highlight>, articleId: Int) = mutex.withLock {
        saveInternal(articleId, highlights)
    }

    /** Fügt ein Highlight hinzu oder ersetzt ein bestehendes mit derselben [Highlight.id]. */
    suspend fun upsert(highlight: Highlight) = mutex.withLock {
        val current = loadInternal(highlight.articleId).filterNot { it.id == highlight.id }
        saveInternal(highlight.articleId, current + highlight)
    }

    suspend fun remove(id: Int, articleId: Int) = mutex.withLock {
        saveInternal(articleId, loadInternal(articleId).filterNot { it.id == id })
    }

    suspend fun clear() = mutex.withLock {
        highlightDao.clear()
    }

    private suspend fun loadInternal(articleId: Int): List<Highlight> {
        val entity = highlightDao.get(articleId) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Highlight>>(entity.json) }.getOrDefault(emptyList())
    }

    private suspend fun saveInternal(articleId: Int, highlights: List<Highlight>) {
        if (highlights.isEmpty()) {
            highlightDao.delete(articleId)
        } else {
            highlightDao.upsert(HighlightEntity(articleId = articleId, json = json.encodeToString(highlights)))
        }
    }
}
