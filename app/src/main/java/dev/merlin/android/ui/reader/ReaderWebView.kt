package dev.merlin.android.ui.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.merlin.android.R
import dev.merlin.android.models.Article
import dev.merlin.android.models.Highlight
import dev.merlin.android.models.CommentAnchor
import dev.merlin.android.models.HighlightCreate
import dev.merlin.android.viewmodel.ArticleReaderViewModel

/**
 * `View.computeVerticalScrollRange()` ist `protected` – für das
 * Scroll-Fortschritts-Tracking (Fortschrittsbalken) brauchen wir den Wert
 * aber öffentlich. Eine Subklasse darf die protected Methode der Superklasse
 * aufrufen; diese dünne Hülle macht sie als `verticalScrollRange()` nach
 * außen sichtbar, ohne Reflection.
 */
private class ScrollRangeWebView(context: android.content.Context) : WebView(context) {
    fun verticalScrollRange(): Int = computeVerticalScrollRange()
}

/**
 * Compose-Wrapper um `android.webkit.WebView` – Äquivalent zum
 * `WKWebView`-Teil von `ArticleReaderView.swift`.
 *
 * **Architekturentscheidung (abweichend von iOS):** iOS deaktiviert das
 * Scrollen der `WKWebView` und lässt eine äußere SwiftUI-`ScrollView` über
 * eine `resize`-JS-Nachricht (ResizeObserver) die Höhe nachführen. Android
 * lässt die `WebView` hier bewusst ihr eigenes, natives Scrollen besitzen –
 * deutlich weniger Komplexität (kein JS-Resize-Roundtrip, kein
 * Höhen-Nachführen) und idiomatischer für `android.webkit.WebView`. Der
 * native Header (Titel/Site/Tags) liegt dafür in einer eigenen Compose-Zeile
 * *über* der WebView statt eingebettet in deren Scroll-Inhalt.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderWebView(
    article: Article,
    highlights: List<Highlight>,
    appearance: ArticleReaderViewModel.Appearance,
    // Wiederherzustellende Leseposition als Fraktion 0..1 (NICHT als Pixel-Offset:
    // Pixel sind gerätespezifisch, die Fraktion ist über Geräte hinweg portabel).
    // Der Ziel-Pixelwert wird gegen die *aktuelle* Inhaltshöhe berechnet – das ist
    // zugleich robuster gegen Reflow/Bild-Nachladen als ein fester Pixelwert.
    initialScrollProgress: Float,
    onCreateHighlight: (HighlightCreate) -> Unit,
    onHighlightTap: (Int, ReaderJsBridge.SelectionRect?) -> Unit,
    onImageTap: (index: Int, srcs: List<String>) -> Unit,
    onSelectionChanged: (ReaderJsBridge.SelectionRect?) -> Unit,
    onInfoPopover: (ReaderJsBridge.InfoPopover) -> Unit,
    onScrollPositionChanged: (progress: Float) -> Unit,
    onWebViewReady: (WebView) -> Unit,
    onScrollProgress: (Float) -> Unit = {},
    // Rohwerte (Pixel) zusätzlich zu [onScrollProgress]: für die Bottom-Bar-Sichtbarkeit
    // (Äquivalent zu iOS' `onScrollGeometryChange`-Logik in ArticleReaderView.swift) reicht der
    // normierte 0–1-Fortschritt nicht – dort wird mit absoluten pt-Distanzen (160pt Bodennähe,
    // 4pt Delta-Schwelle, 40pt Mindest-Offset) gerechnet. `scrollableRangePx` kann 0 sein, wenn
    // der Inhalt kürzer als der Viewport ist.
    onScrollMetrics: (offsetPx: Float, scrollableRangePx: Float) -> Unit = { _, _ -> },
    // Papierstruktur: die Seite bekommt keinen eigenen Hintergrund, der Aufrufer zeichnet ihn
    // dahinter (siehe `paperBackground` in PaperBackground.kt).
    transparentBackground: Boolean = false,
    // Unterkante der Akzentfläche des Plakat-Headers in CSS-px (≈ dp) ab Seitenanfang.
    onPosterBottom: (Float) -> Unit = {},
    // Kommentare (nur merlin-nextcloud): Zähler je Markierung, Tipp auf eine Kommentarstelle und
    // „Kommentieren“ an einer frischen Auswahl.
    commentCounts: Map<Int, Int> = emptyMap(),
    onOpenComments: (Int, String) -> Unit = { _, _ -> },
    onCommentAnchor: (CommentAnchor) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val webViewRef = remember { arrayOfNulls<ScrollRangeWebView>(1) }
    // Die Bridge entsteht nur einmal in `factory`; über den State erreicht sie immer den aktuellen Callback.
    val onPosterBottomState = rememberUpdatedState(onPosterBottom)
    val lastHtmlRef = remember { arrayOfNulls<String>(1) }
    // Quick-Close-Guard: erst nachdem der Restore-Loop das erste Placement gegen
    // eine echte Inhaltshöhe angewendet hat, darf onDispose speichern. Vorher
    // steht scrollY noch auf ~0 – ein Save würde die echte Position lokal UND
    // per Last-Write-Wins auf allen Geräten überschreiben (Reader geöffnet und
    // sofort wieder geschlossen). War nichts wiederherzustellen (Fraktion 0),
    // ist Speichern von Anfang an erlaubt.
    val restoreAppliedRef = remember { booleanArrayOf(initialScrollProgress <= 0f) }
    // Bei Theme AUTO muss ein System-Dark-Mode-Wechsel (z.B. Tag/Nacht-Umschaltung während der
    // Lesesitzung) das HTML neu bauen – daher als Key in `remember` mit aufgenommen.
    val isSystemDark = isSystemInDarkTheme()
    // Support-Infobox (Abo-/Spendenlink): `Article.equals` ignoriert `supportBox` bewusst (Recomposition
    // nur bei relevanten Listenänderungen), das HTML muss sie aber als eigenen Key kennen.
    val context = LocalContext.current
    val supportBox = article.supportBox
    val supportBoxTexts = remember(supportBox?.siteName) {
        supportBox?.let {
            ReaderHtmlBuilder.SupportBoxTexts(
                title = context.getString(R.string.articleReader_supportBox_title, it.siteName),
                // Platzhalter bleiben im Text stehen; das Skript setzt daran die Links ein.
                both = context.getString(R.string.articleReader_supportBox_both, "{subscribe}", "{donate}"),
                subscribeOnly = context.getString(R.string.articleReader_supportBox_subscribeOnly, "{subscribe}"),
                donateOnly = context.getString(R.string.articleReader_supportBox_donateOnly, "{donate}"),
                subscribeLabel = context.getString(R.string.articleReader_supportBox_subscribeLabel),
                donateLabel = context.getString(R.string.articleReader_supportBox_donateLabel),
            )
        }
    }
    val html = remember(article.id, appearance, isSystemDark, supportBox, supportBoxTexts, transparentBackground) {
        ReaderHtmlBuilder.build(article, appearance, isSystemDark, supportBoxTexts, transparentBackground)
    }
    // Markierungen und Kommentar-Zähler setzt ein Skript nach dem Laden bzw. bei jeder Änderung –
    // ein Neuladen der Seite (wie früher) würde die Leseposition zurücksetzen.
    val highlightsScript = remember(highlights, commentCounts) {
        ReaderHtmlBuilder.highlightsScript(highlights, commentCounts)
    }
    val highlightsScriptState = rememberUpdatedState(highlightsScript)
    // Titelbild-URLs: diese Bilder lädt der Reader selbst und schneidet durchsichtige Ränder ab
    // (siehe [HeroImageTrimmer]), damit das Titelbild wirklich randlos erscheint.
    val heroUrls = remember(html, article.url) { HeroImageTrimmer.heroUrls(html, article.url) }
    val heroUrlsState = rememberUpdatedState(heroUrls)
    val pageLoadedRef = remember { booleanArrayOf(false) }
    val lastAppliedScriptRef = remember { arrayOfNulls<String>(1) }
    // Breite des linken Streifens, den wir vom System-Edge-Swipe-Zurück ausnehmen wollen –
    // siehe Kommentar bei `update` unten.
    val gestureExclusionWidthPx = with(LocalDensity.current) { 32.dp.roundToPx() }

    // Vollbild für Videos in der WebView (Inline-Videos `<merlin-inline-player>`, YouTube-
    // `<iframe>`, siehe [ReaderMediaRewriter]): ohne eigenen `WebChromeClient` tut der
    // Vollbild-Knopf der Player in `android.webkit.WebView` schlicht nichts.
    var isVideoFullScreen by remember { mutableStateOf(false) }
    val chromeClient = remember {
        FullScreenVideoChromeClient(onFullScreenChanged = { isVideoFullScreen = it })
    }
    BackHandler(enabled = isVideoFullScreen) { chromeClient.onHideCustomView() }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            ScrollRangeWebView(ctx).apply {
                // Durchsichtig, damit die Papierstruktur dahinter durchscheint; ohne sie malt die
                // Seite ihre Theme-Farbe selbst, dahinter liegt dieselbe Farbe aus Compose.
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                // Ein Tap auf die YouTube-Karte (siehe `ReaderMediaRewriter.YOUTUBE_JS`) setzt den
                // Player erst danach per JS ein – die Nutzergeste liegt damit im Elternframe, nicht im
                // `<iframe>`. Mit dem Default (`true`) würde `autoplay=1` dort ignoriert und man müsste
                // ein zweites Mal tippen. Inline-Videos setzen kein `autoplay`, starten also weiterhin
                // erst auf Tap.
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = chromeClient
                addJavascriptInterface(
                    ReaderJsBridge(
                        onCreateHighlight = onCreateHighlight,
                        onHighlightTap = onHighlightTap,
                        onOpenComments = onOpenComments,
                        onCommentAnchor = onCommentAnchor,
                        onImageTap = onImageTap,
                        onSelectionChanged = onSelectionChanged,
                        onInfoPopover = onInfoPopover,
                        onPosterBottom = { onPosterBottomState.value(it) },
                        onOpenExternalLink = { url ->
                            // Kein Browser installiert / Intent nicht auflösbar: still ignorieren.
                            runCatching {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                    ),
                    "MerlinHighlightBridge",
                )
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        if (request.method != "GET" || request.url.toString() !in heroUrlsState.value) return null
                        return HeroImageTrimmer.load(request)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        pageLoadedRef[0] = true
                        val script = highlightsScriptState.value
                        lastAppliedScriptRef[0] = script
                        view.evaluateJavascript(script, null)
                        restoreScrollWithRetry(view as ScrollRangeWebView, initialScrollProgress, attemptsLeft = 8) {
                            restoreAppliedRef[0] = true
                        }
                    }
                }
                setOnScrollChangeListener { view, _, scrollY, _, _ ->
                    val rangeRaw = (view as ScrollRangeWebView).verticalScrollRange() - view.height
                    val range = rangeRaw.coerceAtLeast(1)
                    onScrollProgress((scrollY.toFloat() / range).coerceIn(0f, 1f))
                    onScrollMetrics(scrollY.toFloat(), rangeRaw.coerceAtLeast(0).toFloat())
                }
                // Reagiert auf jede tatsächliche Größenänderung (initiales Layout, Rotation) statt
                // sich auf den Recomposition-Zeitpunkt von `update` zu verlassen, bei dem
                // `height` beim allerersten Aufruf noch 0 sein kann.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    addOnLayoutChangeListener { view, _, top, _, bottom, _, _, _, _ ->
                        val h = bottom - top
                        if (h > 0) {
                            view.systemGestureExclusionRects = listOf(Rect(0, 0, gestureExclusionWidthPx, h))
                        }
                    }
                }
                webViewRef[0] = this
                onWebViewReady(this)
            }
        },
        update = { webView ->
            if (lastHtmlRef[0] != html) {
                lastHtmlRef[0] = html
                pageLoadedRef[0] = false
                // baseUrl = Artikel-URL, damit relative Bild-/Link-Pfade im Original-HTML aufgehen.
                webView.loadDataWithBaseURL(article.url, html, "text/html", "utf-8", null)
            } else if (pageLoadedRef[0] && lastAppliedScriptRef[0] != highlightsScript) {
                lastAppliedScriptRef[0] = highlightsScript
                webView.evaluateJavascript(highlightsScript, null)
            }
        },
    )

    // Äquivalent zu `.onDisappear` im iOS-Original: Scroll-Position wird
    // EINMAL beim Verlassen des Readers gespeichert, nicht throttled/periodisch.
    // Gemeldet wird die Fraktion (0..1) – der gerätespezifische Pixelwert wäre
    // für die geräteübergreifende Sync nutzlos.
    DisposableEffect(Unit) {
        onDispose {
            chromeClient.onHideCustomView()
            val webView = webViewRef[0] ?: return@onDispose
            // Quick-Close-Guard (siehe restoreAppliedRef oben): ohne angewendeten
            // Restore lieber gar nicht speichern – die bestehende Position bleibt gültig.
            if (!restoreAppliedRef[0]) return@onDispose
            val scrollY = webView.scrollY.toFloat()
            val range = (webView.verticalScrollRange() - webView.height).coerceAtLeast(1)
            val progress = (scrollY / range).coerceIn(0f, 1f)
            onScrollPositionChanged(progress)
        }
    }
}

/**
 * Stellt die Leseposition aus einer Fraktion (0..1) wieder her. Der Ziel-Pixel
 * wird gegen die *aktuelle* `verticalScrollRange()` berechnet und im Retry-Loop
 * neu ausgewertet: iOS pollt bis zu 8× alle 250ms, weil `WKWebView` die finale
 * Content-Höhe asynchron meldet – `android.webkit.WebView` hat dasselbe Problem
 * direkt nach `onPageFinished` (Layout/Reflow nicht garantiert abgeschlossen).
 * Da gegen die Fraktion gerechnet wird, skaliert die Zielposition automatisch
 * mit, während die Inhaltshöhe durch Bild-Nachladen noch wächst.
 */
private fun restoreScrollWithRetry(webView: ScrollRangeWebView, fraction: Float, attemptsLeft: Int, onApplied: () -> Unit) {
    if (fraction <= 0f || attemptsLeft <= 0) return
    val range = (webView.verticalScrollRange() - webView.height).coerceAtLeast(0)
    val targetPx = (fraction * range).toInt()
    webView.scrollTo(0, targetPx)
    // Erstes Placement gegen eine echte Inhaltshöhe → Save beim Schließen ist
    // ab jetzt verlustfrei (Quick-Close-Guard, siehe Aufrufer). Bei range == 0
    // (Layout noch nicht fertig) noch nicht melden – der Retry übernimmt das.
    if (range > 0) onApplied()
    // Sobald die Zielposition erreicht ist (innerhalb 4px), ist die Inhaltshöhe
    // stabil genug – weitere Versuche würden nichts verbessern.
    if (range > 0 && webView.scrollY >= targetPx - 4) return
    Handler(Looper.getMainLooper()).postDelayed({
        restoreScrollWithRetry(webView, fraction, attemptsLeft - 1, onApplied)
    }, 250)
}

/**
 * Zeigt die Vollbild-Ansicht eines HTML5-Videos (`onShowCustomView`) als schwarze, bildschirm-
 * füllende View über dem Fenster der Activity – Äquivalent zum nativen Vollbild von `WKWebView`
 * mit `allowsInlineMediaPlayback` im iOS-Reader. Zurück (über den [BackHandler] im Aufrufer) oder
 * der Vollbild-Knopf des Players beenden sie wieder.
 */
private class FullScreenVideoChromeClient(
    private val onFullScreenChanged: (Boolean) -> Unit,
) : WebChromeClient() {
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        val decor = view.context.findActivity()?.window?.decorView as? ViewGroup
        if (decor == null) {
            callback.onCustomViewHidden()
            return
        }
        view.setBackgroundColor(android.graphics.Color.BLACK)
        decor.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        customView = view
        customViewCallback = callback
        onFullScreenChanged(true)
    }

    override fun onHideCustomView() {
        val view = customView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        onFullScreenChanged(false)
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
