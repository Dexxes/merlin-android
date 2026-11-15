package dev.merlin.android.ui.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Schneidet beim Titelbild durchsichtige Ränder ab, bevor die WebView es anzeigt.
 *
 * Manche Titelbilder sind PNG-Screenshots mit transparentem Rand und eingebranntem Schlagschatten
 * (typisch: Fenster-Screenshots von macOS). Randlos auf volle Breite skaliert scheint durch den
 * Rand die Akzentfläche, das Bild wirkt dann wie eine schmale Karte mit Rand und Schatten statt
 * randlos. Deshalb lädt der Reader nur das Titelbild selbst (über `shouldInterceptRequest`),
 * schneidet Zeilen und Spalten ohne deckende Pixel ab und reicht das Ergebnis als PNG weiter.
 *
 * Alle anderen Bilder lädt die WebView unverändert. Schlägt etwas fehl, gibt [load] `null`
 * zurück und die WebView lädt das Bild ganz normal selbst.
 */
object HeroImageTrimmer {

    /** Ab diesem Alpha gilt ein Pixel als deckend; Schatten bleiben deutlich darunter. */
    private const val OPAQUE_ALPHA = 250

    /**
     * Höchstens so viel (Anteil je Seite) wird abgeschnitten. Liegt der deckende Inhalt weiter
     * innen, ist es eher ein freigestelltes Motiv (Logo, Grafik) als ein Rahmen – das bleibt so.
     */
    private const val MAX_TRIM_FRACTION = 0.15

    /** Größere Bilder werden nicht dekodiert (Speicher), sondern unverändert durchgereicht. */
    private const val MAX_PIXELS = 24_000_000L

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Fertige Antworten je URL, damit ein Neuaufbau der Seite (Theme, Schriftgröße) nicht neu lädt. */
    private val cache = object : LruCache<String, Pair<String, ByteArray>>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Pair<String, ByteArray>) = value.second.size
    }

    private val figureOrImg = Regex("""<(figure|img)\b""", RegexOption.IGNORE_CASE)
    private val mediaTag = Regex("""<(?:img|source)\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val srcAttr = Regex("""\ssrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val srcsetAttr = Regex("""\ssrcset\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    /**
     * Alle URLs, unter denen die WebView das Titelbild anfordern kann (`src`, `srcset`,
     * `<source>` in einem `<picture>`), absolut gegen [baseUrl] aufgelöst. Leer, wenn der Artikel
     * nicht mit einem Titelbild beginnt (`merlin-leads-hero`, siehe [ReaderHtmlBuilder]).
     */
    fun heroUrls(html: String, baseUrl: String): Set<String> {
        if (!html.contains("class=\"merlin-leads-hero\"")) return emptySet()
        val contentStart = html.indexOf("<div id=\"merlin-content\">")
        if (contentStart < 0) return emptySet()
        val bodyStart = contentStart + "<div id=\"merlin-content\">".length
        val first = figureOrImg.find(html, bodyStart) ?: return emptySet()
        if (html.substring(bodyStart, first.range.first).isNotBlank()) return emptySet()
        val heroHtml = if (first.groupValues[1].equals("figure", ignoreCase = true)) {
            val end = html.indexOf("</figure>", first.range.last, ignoreCase = true)
            if (end < 0) return emptySet()
            html.substring(first.range.first, end)
        } else {
            html.substring(first.range.first, (html.indexOf('>', first.range.last) + 1).coerceAtLeast(first.range.last + 1))
        }
        val raw = mutableListOf<String>()
        for (tag in mediaTag.findAll(heroHtml)) {
            srcAttr.find(tag.value)?.let { raw += it.groupValues[1] }
            srcsetAttr.find(tag.value)?.let { set ->
                set.groupValues[1].split(',').mapNotNullTo(raw) { it.trim().split(Regex("\\s+")).firstOrNull()?.takeIf(String::isNotEmpty) }
            }
        }
        return raw.mapNotNullTo(mutableSetOf()) { resolve(baseUrl, decodeEntities(it)) }
    }

    /** Lädt [request] selbst und liefert das Bild ohne durchsichtigen Rand; `null` = WebView lädt selbst. */
    fun load(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        val result = cache.get(url) ?: runCatching { fetchAndTrim(request) }.getOrNull()?.also { cache.put(url, it) }
        val (mime, bytes) = result ?: return null
        return WebResourceResponse(mime, null, ByteArrayInputStream(bytes))
    }

    private fun fetchAndTrim(request: WebResourceRequest): Pair<String, ByteArray>? {
        val builder = Request.Builder().url(request.url.toString())
        request.requestHeaders.forEach { (name, value) -> builder.header(name, value) }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val mime = response.body.contentType()?.let { "${it.type}/${it.subtype}" } ?: "image/*"
            val bytes = response.body.bytes()
            return trim(bytes)?.let { "image/png" to it } ?: (mime to bytes)
        }
    }

    /** PNG ohne durchsichtigen Rand, oder `null`, wenn es nichts (Sinnvolles) abzuschneiden gibt. */
    internal fun trim(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        if (bounds.outWidth.toLong() * bounds.outHeight > MAX_PIXELS) return null
        // JPEG kennt keine Transparenz – nicht erst dekodieren.
        if (bounds.outMimeType == "image/jpeg") return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        try {
            if (!bitmap.hasAlpha()) return null
            val box = opaqueBounds(bitmap) ?: return null
            val (left, top, right, bottom) = box
            val w = bitmap.width
            val h = bitmap.height
            if (left == 0 && top == 0 && right == w && bottom == h) return null
            val maxX = (w * MAX_TRIM_FRACTION).toInt()
            val maxY = (h * MAX_TRIM_FRACTION).toInt()
            if (left > maxX || w - right > maxX || top > maxY || h - bottom > maxY) return null
            val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            return try {
                ByteArrayOutputStream().use { out ->
                    cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.toByteArray()
                }
            } finally {
                if (cropped !== bitmap) cropped.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** Kleinstes Rechteck (links, oben, rechts exkl., unten exkl.) mit allen deckenden Pixeln. */
    private fun opaqueBounds(bitmap: Bitmap): List<Int>? {
        val w = bitmap.width
        val h = bitmap.height
        val row = IntArray(w)
        var left = w
        var right = -1
        var top = -1
        var bottom = -1
        for (y in 0 until h) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            var rowLeft = -1
            var rowRight = -1
            for (x in 0 until w) {
                if ((row[x] ushr 24) >= OPAQUE_ALPHA) {
                    if (rowLeft < 0) rowLeft = x
                    rowRight = x
                }
            }
            if (rowLeft < 0) continue
            if (top < 0) top = y
            bottom = y
            if (rowLeft < left) left = rowLeft
            if (rowRight > right) right = rowRight
        }
        if (top < 0) return null
        return listOf(left, top, right + 1, bottom + 1)
    }

    private fun resolve(baseUrl: String, src: String): String? = runCatching {
        val resolved = URI(baseUrl).resolve(src.trim())
        resolved.toString().takeIf { resolved.scheme == "https" || resolved.scheme == "http" }
    }.getOrNull()

    private fun decodeEntities(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x2F;", "/")
}
