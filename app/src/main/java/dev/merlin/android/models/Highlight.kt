@file:OptIn(ExperimentalSerializationApi::class)

package dev.merlin.android.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Äquivalent zu `Highlight.swift`.
 *
 * In PDFs hängt eine Markierung nicht an XPaths, sondern an [pdfPage] (ab 1) und [pdfRects]
 * (`[x, y, w, h]`, normiert auf 0..1 bezogen auf die unrotierte CropBox, Ursprung oben links, eine
 * Zeile je Rechteck – wie im Web-Reader und auf iOS). Die XPath-Felder sind dann nur Platzhalter.
 * Farbe `comment` ist eine nur kommentierte Stelle: unterstrichen statt hinterlegt.
 */
@Serializable
data class Highlight(
    val id: Int,
    val articleId: Int,
    val highlightedText: String,
    val startXpath: String = "",
    val startOffset: Int = 0,
    val endXpath: String = "",
    val endOffset: Int = 0,
    val color: String,
    val createdAt: String = "",
    /** "owner" oder "guest" (Gäste markieren hinter dem Share-Link). Fehlt bei älteren Servern. */
    val authorType: String? = null,
    val authorName: String? = null,
    /** Farbe des Verfassers (#rrggbb); färbt Unterstreichung und Zähler kommentierter Stellen. */
    val authorColor: String? = null,
    val pdfPage: Int? = null,
    val pdfRects: List<List<Double>>? = null,
) {
    val isGuest: Boolean get() = authorType == "guest"
    val isPdf: Boolean get() = pdfPage != null
    val isComment: Boolean get() = color == COMMENT_COLOR

    /** Dieselbe Markierung mit neuer Stelle bzw. Farbe – für die Anzeige, bis der Server sie bestätigt. */
    fun updated(with: HighlightUpdate): Highlight = copy(
        highlightedText = with.highlightedText,
        startXpath = with.startXpath,
        startOffset = with.startOffset,
        endXpath = with.endXpath,
        endOffset = with.endOffset,
        color = with.color ?: color,
        pdfPage = with.pdfPage ?: pdfPage,
        pdfRects = with.pdfRects ?: pdfRects,
    )

    companion object {
        /** Highlight-Farbe einer nur kommentierten Stelle (legt der Server mit dem ersten Kommentar an). */
        const val COMMENT_COLOR = "comment"
    }
}

/**
 * Payload für das Erstellen eines Highlights (POST), ohne server-generierte Felder. Die PDF-Felder
 * fehlen im JSON, wenn sie null sind (ältere Server und merlin-server kennen sie nicht).
 */
@Serializable
data class HighlightCreate(
    val highlightedText: String,
    val startXpath: String,
    val startOffset: Int,
    val endXpath: String,
    val endOffset: Int,
    val color: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val pdfPage: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val pdfRects: List<List<Double>>? = null,
)

/**
 * Änderung einer bestehenden Markierung (`PUT api/highlights/{id}`, nur merlin-nextcloud): neue
 * Farbe bzw. Stelle. Die id bleibt, die Kommentare hängen also weiter daran. Äquivalent zu
 * `HighlightUpdate` in `Highlight.swift`.
 */
@Serializable
data class HighlightUpdate(
    val highlightedText: String,
    val startXpath: String,
    val startOffset: Int,
    val endXpath: String,
    val endOffset: Int,
    /** Fehlt im JSON, wenn die Farbe bleibt. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val color: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val pdfPage: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val pdfRects: List<List<Double>>? = null,
) {
    companion object {
        /** Nur die Farbe ändern, die Stelle bleibt. */
        fun recolor(highlight: Highlight, color: String) = HighlightUpdate(
            highlightedText = highlight.highlightedText,
            startXpath = highlight.startXpath,
            startOffset = highlight.startOffset,
            endXpath = highlight.endXpath,
            endOffset = highlight.endOffset,
            color = color,
            pdfPage = highlight.pdfPage,
            pdfRects = highlight.pdfRects,
        )
    }
}
