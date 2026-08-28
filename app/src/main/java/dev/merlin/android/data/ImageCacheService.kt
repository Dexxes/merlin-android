package dev.merlin.android.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.merlin.android.models.Article
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Äquivalent zu `ImageCacheService.swift`. Anders als das iOS-Original (eigene
 * Hash-Dateinamen + manuelles Disk-Management) wird hier bewusst Coils
 * eingebauter Disk-Cache genutzt (siehe [ImageModule]) — Android hat mit Coil
 * bereits eine ausgereifte, von der UI (Abschnitt 9) ohnehin benötigte
 * Bildladelösung; ein zweites manuelles Cache-System wäre doppelte Arbeit.
 * Was on top kommt, weil Coils Cache es nicht von sich aus kann:
 * - proaktives Vorwärmen aller Artikel-Bilder (WLAN-only-Option)
 * - gezielte Eviction aller Bilder EINES Artikels (Coil kennt nur globale
 *   Clear/Get-Operationen über den URL-Key, daher der eigene [ImageCacheIndexDao]-Index)
 * - der `Referer`-Header gegen Hotlink-Schutz (siehe [RefererInterceptor])
 */
@Singleton
class ImageCacheService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
    private val imageCacheIndexDao: ImageCacheIndexDao,
    private val preferencesStore: PreferencesStore,
    private val json: Json,
) {

    /**
     * Lädt alle Bilder unarchivierter Artikel proaktiv in den Disk-Cache.
     * Respektiert die WLAN-only-Einstellung; max. 4 parallele Downloads
     * (gleiches Limit wie im iOS-Original).
     */
    suspend fun prefetch(articles: List<Article>) = coroutineScope {
        if (preferencesStore.isPrefetchImagesOnWifiOnly() && !isOnWifi()) return@coroutineScope

        val semaphore = Semaphore(4)
        for (article in articles) {
            if (article.isArchived) continue
            val urls = extractImageUrls(article)
            if (urls.isEmpty()) continue
            imageCacheIndexDao.upsert(ImageCacheIndexEntity(article.id, json.encodeToString(urls)))
            for (url in urls) {
                launch {
                    semaphore.withPermit {
                        runCatching {
                            imageLoader.execute(
                                ImageRequest.Builder(context)
                                    .data(url)
                                    .memoryCachePolicy(CachePolicy.DISABLED) // nur Disk vorwärmen
                                    .build(),
                            )
                        }
                    }
                }
            }
        }
    }

    /** Lädt ein einzelnes Bild nach, falls es noch nicht im Cache liegt. */
    suspend fun fetchSingle(url: String, respectWifiOnly: Boolean = false): Boolean {
        if (respectWifiOnly && preferencesStore.isPrefetchImagesOnWifiOnly() && !isOnWifi()) return false
        val result = imageLoader.execute(ImageRequest.Builder(context).data(url).build())
        return result !is coil.request.ErrorResult
    }

    /** Entfernt alle für [articleId] indizierten Bilder aus dem Disk-Cache. */
    suspend fun evict(articleId: Int) = withContext(Dispatchers.IO) {
        val entry = imageCacheIndexDao.get(articleId) ?: return@withContext
        val urls = runCatching { json.decodeFromString<List<String>>(entry.urlsJson) }.getOrDefault(emptyList())
        val diskCache = imageLoader.diskCache
        urls.forEach { diskCache?.remove(it) }
        imageCacheIndexDao.delete(articleId)
    }

    /** Leert den gesamten Bild-Cache (Logout/Account-Wechsel). */
    suspend fun clear() = withContext(Dispatchers.IO) {
        imageLoader.diskCache?.clear()
        imageCacheIndexDao.clear()
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** Hero-Bild, Favicon und alle `<img src="">` aus dem Artikel-HTML. */
    private fun extractImageUrls(article: Article): List<String> {
        val urls = LinkedHashSet<String>()
        article.imageUrl?.let { urls += it }
        article.faviconUrl?.let { urls += it }
        article.content?.let { html ->
            IMG_SRC_REGEX.findAll(html).forEach { match -> urls += decodeHtmlEntities(match.groupValues[1]) }
        }
        return urls.toList()
    }

    private fun decodeHtmlEntities(value: String): String = value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")

    private companion object {
        val IMG_SRC_REGEX = Regex("""<img[^>]+src=["']([^"'>]+)["']""", RegexOption.IGNORE_CASE)
    }
}
