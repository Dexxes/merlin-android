package dev.merlin.android.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.merlin.android.R
import dev.merlin.android.models.CommentAnchor
import dev.merlin.android.models.Highlight
import dev.merlin.android.models.HighlightCreate
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Zeigt die PDF eines PDF-Artikels ([dev.merlin.android.models.Article.isPdf]) im Reader.
 * Äquivalent zu `PDFArticleView.swift` (PDFKit) auf iOS.
 *
 * Die App lädt die Datei über [loadPdf] von der Quelle (bzw. aus dem
 * [dev.merlin.android.data.PdfCacheService]) und zeigt sie mit dem mitgelieferten pdf.js
 * (`assets/pdfjs`, Version in `assets/pdfjs/VERSION`) in einer WebView (`assets/pdfreader`):
 * alle Seiten untereinander, Pinch-Zoom bis zum Fünffachen, Textauswahl wie im Text-Reader, Suche,
 * Links in der PDF. `PdfRenderer` (vorher) kann weder Text auswählen noch durchsuchen und erst ab
 * Android 15 Text auslesen.
 *
 * Markieren und Kommentieren laufen über dieselbe Farbleiste und dasselbe Kommentar-Sheet wie bei
 * Text-Artikeln: Markierungen werden als Seite + Rechtecke gespeichert ([Highlight.pdfPage]/
 * [Highlight.pdfRects], wie im Web-Reader und auf iOS) und über das Dokument gezeichnet; die
 * gecachte Datei selbst ändert sich nie.
 *
 * Die WebView lädt nur Dateien aus den Assets und die eine PDF; jede andere Anfrage wird
 * abgewiesen, die PDF kann also nichts nachladen. pdf.js läuft ohne `eval` und ohne XFA.
 */
@Composable
fun PdfReaderView(
    sourceUrl: String,
    loadPdf: suspend (String) -> File,
    discardPdf: suspend (String) -> Unit,
    initialScrollProgress: Float,
    foregroundColor: Color,
    backgroundColor: Color,
    onOpenInBrowser: () -> Unit,
    onScrollPositionChanged: (Float) -> Unit,
    onScrollProgress: (Float) -> Unit,
    onScrollMetrics: (offsetPx: Float, scrollableRangePx: Float) -> Unit,
    // Freihand-Lesemodus: meldet die WebView, sobald das Dokument steht (null, wenn sie verschwindet).
    onAutoScrollTarget: (AutoScrollTarget?) -> Unit = {},
    // Markierungen (nur PDF-Markierungen werden gezeichnet) und Kommentar-Zähler je Markierung.
    highlights: List<Highlight> = emptyList(),
    commentCounts: Map<Int, Int> = emptyMap(),
    onCreateHighlight: (HighlightCreate) -> Unit = {},
    onHighlightTap: (Int, ReaderJsBridge.SelectionRect?) -> Unit = { _, _ -> },
    onOpenComments: (Int, String) -> Unit = { _, _ -> },
    onCommentAnchor: (CommentAnchor) -> Unit = {},
    onSelectionChanged: (ReaderJsBridge.SelectionRect?) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    onFindResult: (FindResult) -> Unit = {},
    // Aktionen für Farbleiste, Kommentar-Sheet und Suche (null, solange kein Dokument steht).
    onDocumentActions: (ReaderDocumentActions?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var phase by remember(sourceUrl) { mutableStateOf<PdfPhase>(PdfPhase.Loading) }
    var attempt by remember(sourceUrl) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sourceUrl, attempt) {
        phase = PdfPhase.Loading
        phase = try {
            PdfPhase.Downloaded(loadPdf(sourceUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PdfPhase.Failed
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val current = phase) {
            PdfPhase.Loading -> PdfLoading(foregroundColor, Modifier.align(Alignment.Center))

            PdfPhase.Failed -> PdfMessage(
                text = stringResource(R.string.articleReader_pdf_failed),
                color = foregroundColor,
                onRetry = { attempt++ },
                onOpenInBrowser = onOpenInBrowser,
                modifier = Modifier.align(Alignment.Center),
            )

            PdfPhase.Encrypted -> PdfMessage(
                text = stringResource(R.string.articleReader_pdf_encrypted),
                color = foregroundColor,
                onRetry = null,
                onOpenInBrowser = onOpenInBrowser,
                modifier = Modifier.align(Alignment.Center),
            )

            is PdfPhase.Downloaded -> {
                var ready by remember(current.file) { mutableStateOf(false) }
                PdfDocumentWebView(
                    file = current.file,
                    initialScrollProgress = initialScrollProgress,
                    backgroundColor = backgroundColor,
                    highlights = highlights,
                    commentCounts = commentCounts,
                    onReady = { ready = true },
                    onFailed = { encrypted ->
                        if (encrypted) {
                            phase = PdfPhase.Encrypted
                        } else {
                            // Unlesbare Datei verwerfen, damit "Erneut versuchen" eine frische Kopie lädt.
                            scope.launch { runCatching { discardPdf(sourceUrl) } }
                            phase = PdfPhase.Failed
                        }
                    },
                    onScrollPositionChanged = onScrollPositionChanged,
                    onScrollProgress = onScrollProgress,
                    onScrollMetrics = onScrollMetrics,
                    onAutoScrollTarget = onAutoScrollTarget,
                    onCreateHighlight = onCreateHighlight,
                    onHighlightTap = onHighlightTap,
                    onOpenComments = onOpenComments,
                    onCommentAnchor = onCommentAnchor,
                    onSelectionChanged = onSelectionChanged,
                    onOpenLink = onOpenLink,
                    onFindResult = onFindResult,
                    onDocumentActions = onDocumentActions,
                )
                if (!ready) PdfLoading(foregroundColor, Modifier.align(Alignment.Center))
            }
        }
    }
}

private sealed interface PdfPhase {
    data object Loading : PdfPhase
    data object Failed : PdfPhase
    data object Encrypted : PdfPhase
    class Downloaded(val file: File) : PdfPhase
}

@Composable
private fun PdfLoading(color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.articleReader_pdf_loading), color = color)
    }
}

@Composable
private fun PdfMessage(
    text: String,
    color: Color,
    onRetry: (() -> Unit)?,
    onOpenInBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, color = color)
        if (onRetry != null) {
            Button(onClick = onRetry) { Text(stringResource(R.string.articleReader_pdf_retry)) }
        }
        TextButton(onClick = onOpenInBrowser) { Text(stringResource(R.string.articleReader_pdf_openBrowser)) }
    }
}

/** `computeVerticalScrollRange()` ist `protected` – wie `ScrollRangeWebView` in ReaderWebView.kt. */
private class PdfWebView(context: Context) : WebView(context) {
    fun verticalScrollRange(): Int = computeVerticalScrollRange()
}

private const val ASSET_HOST = "appassets.androidplatform.net"
private const val READER_URL = "https://$ASSET_HOST/pdfreader/index.html"
private const val DOCUMENT_PATH = "/document.pdf"

private val pdfJson = Json { encodeDefaults = false; explicitNulls = false }

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PdfDocumentWebView(
    file: File,
    initialScrollProgress: Float,
    backgroundColor: Color,
    highlights: List<Highlight>,
    commentCounts: Map<Int, Int>,
    onReady: () -> Unit,
    onFailed: (encrypted: Boolean) -> Unit,
    onScrollPositionChanged: (Float) -> Unit,
    onScrollProgress: (Float) -> Unit,
    onScrollMetrics: (Float, Float) -> Unit,
    onAutoScrollTarget: (AutoScrollTarget?) -> Unit,
    onCreateHighlight: (HighlightCreate) -> Unit,
    onHighlightTap: (Int, ReaderJsBridge.SelectionRect?) -> Unit,
    onOpenComments: (Int, String) -> Unit,
    onCommentAnchor: (CommentAnchor) -> Unit,
    onSelectionChanged: (ReaderJsBridge.SelectionRect?) -> Unit,
    onOpenLink: (String) -> Unit,
    onFindResult: (FindResult) -> Unit,
    onDocumentActions: (ReaderDocumentActions?) -> Unit,
) {
    val webViewRef = remember { arrayOfNulls<PdfWebView>(1) }
    // Quick-Close-Guard wie in ReaderWebView: erst nach der Wiederherstellung speichern.
    val restoreAppliedRef = remember { booleanArrayOf(initialScrollProgress <= 0f) }
    val documentReadyRef = remember { booleanArrayOf(false) }
    val lastStateRef = remember { arrayOfNulls<String>(1) }
    val gestureExclusionWidthPx = with(LocalDensity.current) { 32.dp.roundToPx() }

    val stateScript = remember(highlights, commentCounts) {
        val list = pdfJson.encodeToString(highlights.filter { it.isPdf })
        val counts = commentCounts.entries.joinToString(",", "{", "}") { (id, n) -> "\"$id\":$n" }
        "window.MerlinPdf && MerlinPdf.setHighlights($list, $counts, true);"
    }
    val stateScriptState = rememberUpdatedState(stateScript)
    val callbacks = rememberUpdatedState(
        PdfCallbacks(
            onReady, onFailed, onCreateHighlight, onHighlightTap, onOpenComments,
            onCommentAnchor, onSelectionChanged, onOpenLink, onFindResult,
        ),
    )
    val background = "#%06X".format(0xFFFFFF and backgroundColor.toArgb())

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PdfWebView(ctx).apply {
                setBackgroundColor(backgroundColor.toArgb())
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.textZoom = 100
                val view = this
                val main = Handler(Looper.getMainLooper())
                addJavascriptInterface(
                    PdfJsBridge(main, { callbacks.value }) { event ->
                        when (event) {
                            PdfJsBridge.Event.ScriptReady -> {
                                view.evaluateJavascript(
                                    "MerlinPdf.open(${jsQuote(DOCUMENT_PATH + "?v=" + file.lastModified())}, {background: ${jsQuote(background)}});",
                                    null,
                                )
                            }
                            PdfJsBridge.Event.DocumentReady -> {
                                documentReadyRef[0] = true
                                lastStateRef[0] = stateScriptState.value
                                view.evaluateJavascript(stateScriptState.value, null)
                                callbacks.value.onReady()
                                onAutoScrollTarget(WebViewAutoScrollTarget(view))
                                onDocumentActions(PdfDocumentActions(view))
                            }
                            PdfJsBridge.Event.Layout -> {
                                restorePdfScroll(view, initialScrollProgress, attemptsLeft = 8) {
                                    restoreAppliedRef[0] = true
                                }
                            }
                        }
                    },
                    "MerlinPdfBridge",
                )
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                        serve(ctx, request, file)

                    // Keine Navigation weg vom Reader (Links laufen über die Bridge).
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                }
                setOnScrollChangeListener { v, _, scrollY, _, _ ->
                    val rangeRaw = (v as PdfWebView).verticalScrollRange() - v.height
                    val range = rangeRaw.coerceAtLeast(1)
                    onScrollProgress((scrollY.toFloat() / range).coerceIn(0f, 1f))
                    onScrollMetrics(scrollY.toFloat(), rangeRaw.coerceAtLeast(0).toFloat())
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    addOnLayoutChangeListener { v, _, top, _, bottom, _, _, _, _ ->
                        val h = bottom - top
                        if (h > 0) v.systemGestureExclusionRects = listOf(Rect(0, 0, gestureExclusionWidthPx, h))
                    }
                }
                webViewRef[0] = this
                loadUrl(READER_URL)
            }
        },
        update = { view ->
            if (documentReadyRef[0] && lastStateRef[0] != stateScript) {
                lastStateRef[0] = stateScript
                view.evaluateJavascript(stateScript, null)
            }
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            onAutoScrollTarget(null)
            onDocumentActions(null)
            val view = webViewRef[0] ?: return@onDispose
            if (restoreAppliedRef[0]) {
                val range = (view.verticalScrollRange() - view.height).coerceAtLeast(1)
                onScrollPositionChanged((view.scrollY.toFloat() / range).coerceIn(0f, 1f))
            }
            view.stopLoading()
            view.destroy()
        }
    }
}

/** Aktuelle Callbacks des Composables – die Bridge entsteht nur einmal in `factory`. */
private class PdfCallbacks(
    val onReady: () -> Unit,
    val onFailed: (Boolean) -> Unit,
    val onCreateHighlight: (HighlightCreate) -> Unit,
    val onHighlightTap: (Int, ReaderJsBridge.SelectionRect?) -> Unit,
    val onOpenComments: (Int, String) -> Unit,
    val onCommentAnchor: (CommentAnchor) -> Unit,
    val onSelectionChanged: (ReaderJsBridge.SelectionRect?) -> Unit,
    val onOpenLink: (String) -> Unit,
    val onFindResult: (FindResult) -> Unit,
)

/**
 * Empfänger der `MerlinPdfBridge`-Aufrufe aus `assets/pdfreader/reader.js`. Wie [ReaderJsBridge]
 * kommen die Aufrufe auf einem Binder-Thread an und springen auf den Main-Thread zurück.
 */
private class PdfJsBridge(
    private val main: Handler,
    private val callbacks: () -> PdfCallbacks,
    private val onEvent: (Event) -> Unit,
) {
    enum class Event { ScriptReady, DocumentReady, Layout }

    private val json = Json { ignoreUnknownKeys = true }

    @JavascriptInterface
    fun onScriptReady() {
        main.post { onEvent(Event.ScriptReady) }
    }

    @JavascriptInterface
    fun onDocumentReady(@Suppress("UNUSED_PARAMETER") pageCount: Int) {
        main.post { onEvent(Event.DocumentReady) }
    }

    @JavascriptInterface
    fun onLayout() {
        main.post { onEvent(Event.Layout) }
    }

    @JavascriptInterface
    fun onLoadFailed(kind: String) {
        main.post { callbacks().onFailed(kind == "encrypted") }
    }

    @JavascriptInterface
    fun onSelectionChanged(rectJson: String) {
        val rect = runCatching { json.decodeFromString<ReaderJsBridge.SelectionRect>(rectJson) }.getOrNull()
        main.post { callbacks().onSelectionChanged(rect) }
    }

    @JavascriptInterface
    fun onSelectionCleared() {
        main.post { callbacks().onSelectionChanged(null) }
    }

    @JavascriptInterface
    fun onCreateHighlight(payloadJson: String) {
        val payload = runCatching { json.decodeFromString<HighlightCreate>(payloadJson) }.getOrNull() ?: return
        if (payload.pdfPage == null || payload.pdfRects.isNullOrEmpty()) return
        main.post { callbacks().onCreateHighlight(payload) }
    }

    @JavascriptInterface
    fun onCommentAnchor(payloadJson: String) {
        val anchor = runCatching { json.decodeFromString<CommentAnchor>(payloadJson) }.getOrNull() ?: return
        if (anchor.pdfPage == null || anchor.pdfRects.isNullOrEmpty()) return
        main.post { callbacks().onCommentAnchor(anchor) }
    }

    @JavascriptInterface
    fun onHighlightTap(id: Int, rectJson: String) {
        val rect = runCatching { json.decodeFromString<ReaderJsBridge.SelectionRect>(rectJson) }.getOrNull()
        main.post { callbacks().onHighlightTap(id, rect) }
    }

    @JavascriptInterface
    fun onOpenComments(id: Int, text: String) {
        main.post { callbacks().onOpenComments(id, text) }
    }

    @JavascriptInterface
    fun onFindResult(index: Int, count: Int) {
        main.post { callbacks().onFindResult(FindResult(index, count)) }
    }

    @JavascriptInterface
    fun onOpenLink(url: String) {
        // Nur http(s) – der Wert stammt aus der PDF, also von einem fremden Server.
        val scheme = runCatching { java.net.URI(url).scheme?.lowercase() }.getOrNull()
        if (scheme != "http" && scheme != "https") return
        main.post { callbacks().onOpenLink(url) }
    }

    @JavascriptInterface
    fun onTap() = Unit
}

/** Kotlin → `MerlinPdf` in reader.js. */
private class PdfDocumentActions(private val webView: WebView) : ReaderDocumentActions {
    override fun mark(color: String) = run("MerlinPdf.mark(${jsQuote(color)});")
    override fun comment() = run("MerlinPdf.comment();")
    override fun clearSelection() = run("MerlinPdf.clearSelection();")
    override fun reveal(highlightId: Int) = run("MerlinPdf.reveal($highlightId);")
    override fun find(query: String) = run("MerlinPdf.find(${jsQuote(query)});")
    override fun findStep(forward: Boolean) = run("MerlinPdf.findStep($forward);")
    override fun clearFind() = run("MerlinPdf.clearFind();")

    private fun run(script: String) = webView.evaluateJavascript(script, null)
}

/**
 * Liefert die Antworten für die WebView: `pdfreader/` und `pdfjs/` aus den Assets, die PDF unter
 * [DOCUMENT_PATH] aus dem Cache. Alles andere (andere Hosts, andere Pfade) wird abgewiesen.
 */
private fun serve(context: Context, request: WebResourceRequest, file: File): WebResourceResponse {
    val url = request.url
    val path = url.path.orEmpty()
    if (url.scheme != "https" || url.host != ASSET_HOST || path.contains("..")) return blocked()
    if (path == DOCUMENT_PATH) {
        return runCatching {
            WebResourceResponse("application/pdf", null, FileInputStream(file)).withHeaders()
        }.getOrElse { notFound() }
    }
    val asset = path.removePrefix("/")
    if (!asset.startsWith("pdfreader/") && !asset.startsWith("pdfjs/")) return blocked()
    return runCatching {
        WebResourceResponse(mimeTypeFor(asset), if (isText(asset)) "utf-8" else null, context.assets.open(asset)).withHeaders()
    }.getOrElse { notFound() }
}

private fun WebResourceResponse.withHeaders(): WebResourceResponse = apply {
    responseHeaders = mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff")
}

private fun mimeTypeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "html" -> "text/html"
    "css" -> "text/css"
    "js", "mjs" -> "text/javascript"
    "json" -> "application/json"
    "ttf" -> "font/ttf"
    "pfb", "bcmap" -> "application/octet-stream"
    else -> "application/octet-stream"
}

private fun isText(path: String): Boolean = mimeTypeFor(path).startsWith("text/") || path.endsWith(".json")

private fun blocked() = WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))

private fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

/** Leseposition aus einer Fraktion wiederherstellen, mit Wiederholung (wie `restoreScrollWithRetry`). */
private fun restorePdfScroll(webView: PdfWebView, fraction: Float, attemptsLeft: Int, onApplied: () -> Unit) {
    if (fraction <= 0f) {
        onApplied()
        return
    }
    if (attemptsLeft <= 0) return
    val range = (webView.verticalScrollRange() - webView.height).coerceAtLeast(0)
    val target = (fraction * range).toInt()
    webView.scrollTo(0, target)
    if (range > 0) onApplied()
    if (range > 0 && webView.scrollY >= target - 4) return
    Handler(Looper.getMainLooper()).postDelayed({
        restorePdfScroll(webView, fraction, attemptsLeft - 1, onApplied)
    }, 250)
}
