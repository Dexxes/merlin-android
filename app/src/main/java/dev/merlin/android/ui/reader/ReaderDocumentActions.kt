package dev.merlin.android.ui.reader

import android.webkit.WebView

/**
 * Was die native Reader-Oberfläche (Farbleiste, Kommentar-Sheet, Suchleiste) mit dem gezeigten
 * Dokument tun kann – egal ob Text-Artikel ([ReaderWebView]) oder PDF ([PdfReaderView]).
 * Äquivalent zu `HighlightActionHandler` in `ArticleReaderView.swift`, das je nach Artikel an den
 * WKWebView oder an PDFKit weiterreicht.
 */
interface ReaderDocumentActions {
    /** Aktuelle Auswahl in [color] markieren. */
    fun mark(color: String)

    /** „Kommentieren“ an der aktuellen Auswahl (die Stelle kommt über `onCommentAnchor` zurück). */
    fun comment()

    fun clearSelection()

    /**
     * Zitat-Tafel: Text der aktuellen Auswahl (mit Absätzen) an [onResult], danach ist die Auswahl
     * aufgehoben. `null`, wenn es keine gibt – und immer bei PDFs (wie auf iOS nur Text-Artikel).
     */
    fun quoteSelection(onResult: (String?) -> Unit) = onResult(null)

    /** „Im Text zeigen“: zur Markierung springen. */
    fun reveal(highlightId: Int)

    /** Suche im Dokument; Treffer meldet der Callback des jeweiligen Readers (Index, Anzahl). */
    fun find(query: String)

    fun findStep(forward: Boolean)

    fun clearFind()
}

/** Trefferstand der Suche im Artikel: [index] ab 0 (-1 = keiner), [count] Treffer insgesamt. */
data class FindResult(val index: Int, val count: Int)

/**
 * Text-Artikel: Markieren/Kommentieren über `MerlinReader` (READER_JS), Suche über die eingebaute
 * Suche der WebView (`findAllAsync`), die die Treffer selbst einfärbt und anscrollt.
 */
class WebViewDocumentActions(
    private val webView: WebView,
    onFindResult: (FindResult) -> Unit,
) : ReaderDocumentActions {
    init {
        webView.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            if (isDoneCounting) {
                onFindResult(FindResult(if (numberOfMatches > 0) activeMatchOrdinal else -1, numberOfMatches))
            }
        }
    }

    override fun mark(color: String) {
        webView.evaluateJavascript("MerlinReader.createHighlightFromSelection(${jsQuote(color)});", null)
    }

    override fun comment() {
        webView.evaluateJavascript("MerlinReader.commentFromSelection();", null)
    }

    override fun clearSelection() {
        webView.evaluateJavascript("window.getSelection() && window.getSelection().removeAllRanges();", null)
    }

    override fun quoteSelection(onResult: (String?) -> Unit) {
        webView.evaluateJavascript("MerlinReader.quoteSelection ? MerlinReader.quoteSelection() : ''") { result ->
            // Ergebnis kommt als JSON-Literal (String in Anführungszeichen bzw. `null`).
            val text = runCatching { org.json.JSONTokener(result).nextValue() as? String }.getOrNull()
            onResult(text?.takeIf { it.isNotBlank() })
        }
    }

    override fun reveal(highlightId: Int) {
        webView.evaluateJavascript("MerlinReader.reveal($highlightId);", null)
    }

    override fun find(query: String) {
        if (query.isBlank()) clearFind() else webView.findAllAsync(query.trim())
    }

    override fun findStep(forward: Boolean) = webView.findNext(forward)

    override fun clearFind() = webView.clearMatches()
}

/** JS-String-Literal für einen Wert aus der App (Farbnamen, Suchtext). */
internal fun jsQuote(value: String): String = buildString {
    append('"')
    for (c in value) {
        when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            ' ' -> append("\\u2028")
            ' ' -> append("\\u2029")
            '<' -> append("\\u003c")
            else -> append(c)
        }
    }
    append('"')
}
