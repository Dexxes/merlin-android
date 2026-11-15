package dev.merlin.android.ui.reader

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import dev.merlin.android.models.CommentAnchor
import dev.merlin.android.models.HighlightCreate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Äquivalent zum `WKScriptMessageHandler`-Bridge-Empfänger aus
 * `ArticleReaderView.swift`. Android ruft `@JavascriptInterface`-Methoden auf
 * einem Binder-Thread auf (nicht dem Main-Thread) – jede Callback-Weiterleitung
 * springt daher explizit über `Handler(Looper.getMainLooper())` zurück, damit
 * Compose-State-Updates sicher sind.
 *
 * `onImageTap` öffnet [ImageLightboxScreen] mit dem getappten Bildindex und
 * allen Bild-URLs des Artikels (siehe `imageTap`-Event in `ReaderHtmlBuilder`).
 */
class ReaderJsBridge(
    private val onCreateHighlight: (HighlightCreate) -> Unit,
    /** Farbige Markierung angetippt: id und ihr Rechteck (CSS-px, wie [SelectionRect]). */
    private val onHighlightTap: (Int, SelectionRect?) -> Unit,
    private val onImageTap: (index: Int, srcs: List<String>) -> Unit,
    private val onSelectionChanged: (SelectionRect?) -> Unit,
    private val onInfoPopover: (InfoPopover) -> Unit,
    /** Unterstrichene Kommentarstelle angetippt: Kommentare dieser Markierung (id, zitierter Text). */
    private val onOpenComments: (Int, String) -> Unit = { _, _ -> },
    /** „Kommentieren“ an einer frischen Auswahl: Stelle für den ersten Kommentar. */
    private val onCommentAnchor: (CommentAnchor) -> Unit = {},
    /** Öffnet einen Link der Support-Infobox im externen Browser (siehe `ReaderHtmlBuilder.buildSupportBoxScript`). */
    private val onOpenExternalLink: (String) -> Unit = {},
    /** Unterkante der Akzentfläche des Plakat-Headers in CSS-px ab Seitenanfang (siehe `layoutPoster` in `READER_JS`). */
    private val onPosterBottom: (Float) -> Unit = {},
) {
    @Serializable
    data class SelectionRect(val x: Float, val y: Float, val width: Float, val height: Float)

    /**
     * Äquivalent zum Inhalt eines iOS-`.popover`-Flyouts der Info-Card (siehe `ReaderHtmlBuilder`).
     * `x`/`y`/`width`/`height` sind die CSS-px-Bounding-Box der angetippten Zelle
     * (`getBoundingClientRect()`, bereits scroll-relativ) – damit kann `InfoPopoverOverlay` in
     * `ArticleReaderScreen.kt` direkt unter der Zelle andocken statt zentriert zu erscheinen.
     */
    @Serializable
    data class InfoPopover(val label: String, val value: String, val x: Float, val y: Float, val width: Float, val height: Float)

    @Serializable
    private data class ImageTapPayload(val index: Int, val srcs: List<String>)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true }

    @JavascriptInterface
    fun onCreateHighlight(payloadJson: String) {
        val payload = runCatching { json.decodeFromString<HighlightCreate>(payloadJson) }.getOrNull() ?: return
        mainHandler.post { onCreateHighlight.invoke(payload) }
    }

    @JavascriptInterface
    fun onHighlightTap(id: Int, rectJson: String) {
        val rect = runCatching { json.decodeFromString<SelectionRect>(rectJson) }.getOrNull()
        mainHandler.post { onHighlightTap.invoke(id, rect) }
    }

    @JavascriptInterface
    fun onOpenComments(id: Int, text: String) {
        mainHandler.post { onOpenComments.invoke(id, text) }
    }

    @JavascriptInterface
    fun onCommentAnchor(payloadJson: String) {
        val anchor = runCatching { json.decodeFromString<CommentAnchor>(payloadJson) }.getOrNull() ?: return
        mainHandler.post { onCommentAnchor.invoke(anchor) }
    }

    @JavascriptInterface
    fun onImageTap(payloadJson: String) {
        val payload = runCatching { json.decodeFromString<ImageTapPayload>(payloadJson) }.getOrNull() ?: return
        mainHandler.post { onImageTap.invoke(payload.index, payload.srcs) }
    }

    @JavascriptInterface
    fun onSelectionChanged(payloadJson: String) {
        val rect = runCatching { json.decodeFromString<SelectionRect>(payloadJson) }.getOrNull()
        mainHandler.post { onSelectionChanged.invoke(rect) }
    }

    @JavascriptInterface
    fun onSelectionCleared() {
        mainHandler.post { onSelectionChanged.invoke(null) }
    }

    @JavascriptInterface
    fun onOpenExternalLink(url: String) {
        // Nur http(s): der Wert kommt zwar aus einer vom Server gelieferten, in Kotlin bereits
        // geprüften URL, die Bridge ist aber für jede Seite in der WebView erreichbar (auch für
        // JS aus Artikelinhalt, das der Sanitizer nicht entfernt hätte) - hier deshalb nochmals prüfen.
        val scheme = runCatching { java.net.URI(url).scheme?.lowercase() }.getOrNull()
        if (scheme != "http" && scheme != "https") return
        mainHandler.post { onOpenExternalLink.invoke(url) }
    }

    @JavascriptInterface
    fun onInfoPopover(payloadJson: String) {
        val payload = runCatching { json.decodeFromString<InfoPopover>(payloadJson) }.getOrNull() ?: return
        mainHandler.post { onInfoPopover.invoke(payload) }
    }

    @JavascriptInterface
    fun onPosterBottom(cssPx: Float) {
        if (cssPx.isNaN() || cssPx < 0f) return
        mainHandler.post { onPosterBottom.invoke(cssPx) }
    }
}
