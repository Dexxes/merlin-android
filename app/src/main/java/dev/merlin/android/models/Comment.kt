@file:OptIn(ExperimentalSerializationApi::class)

package dev.merlin.android.models

import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Kommentar zu einem Artikel – vom Besitzer oder von einem Gast hinter dem öffentlichen Share-Link
 * (nur merlin-nextcloud). Äquivalent zu `Comment.swift`.
 *
 * Threads sind eine Ebene tief: Wurzeln hängen an einer Markierung ([highlightId]) oder am ganzen
 * Artikel, alle Antworten liegen in [replies] der Wurzel; [replyToId] nennt die Antwort, auf die
 * geantwortet wurde. Ein gelöschter Kommentar mit Antworten bleibt als Platzhalter stehen
 * ([deleted], leerer Name/Text). [body] ist reiner Text und wird nie als HTML dargestellt.
 */
@Serializable
data class Comment(
    val id: Int,
    val articleId: Int,
    val highlightId: Int? = null,
    /** Text der Markierung beim Anlegen – bleibt erhalten, wenn die Markierung später entfernt wird. */
    val quotedText: String? = null,
    val parentId: Int? = null,
    val replyToId: Int? = null,
    /** "owner" oder "guest". */
    val authorType: String = "owner",
    val authorName: String = "",
    val body: String = "",
    val deleted: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
    val edited: Boolean = false,
    /** Nur bei Thread-Wurzeln gefüllt. */
    val replies: List<Comment>? = null,
    /** Farbe des Verfassers (#rrggbb), siehe [Highlight.authorColor]. */
    val authorColor: String? = null,
) {
    val isOwner: Boolean get() = authorType == "owner"

    /** Sichtbare Kommentare dieses Threads (gelöschte Platzhalter zählen nicht). */
    val visibleCount: Int get() = (if (deleted) 0 else 1) + (replies?.size ?: 0)

    val createdMillis: Long? get() = parseMillis(createdAt)

    companion object {
        fun parseMillis(iso: String): Long? =
            runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
    }
}

/**
 * Antwort von `GET api/articles/{id}/comments` und Inhalt jedes Push-Ereignisses: alle Threads
 * und Markierungen des Artikels plus Änderungsmarke.
 */
@Serializable
data class CommentsPayload(
    val signature: String,
    /** Zeitpunkt des Stands auf dem Server (Mikrosekunden); ordnet Antworten und Push-Ereignisse. */
    val generatedAt: Long? = null,
    val comments: List<Comment> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    /** Server kann Markierungen ändern (`PUT api/highlights/{id}`). Fehlt bei älteren Servern. */
    val highlightUpdate: Boolean? = null,
)

/**
 * Textstelle für den ersten Kommentar an einer Auswahl. Wird mit dem Kommentar mitgeschickt
 * (`anchor`); der Server legt daraus eine unterstrichene Stelle (Farbe `comment`) an und entfernt
 * sie wieder, wenn ihr letzter Kommentar gelöscht wird. In PDFs mit [pdfPage]/[pdfRects] statt
 * der XPaths (die dann im JSON fehlen).
 */
@Serializable
data class CommentAnchor(
    val highlightedText: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val startXpath: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val startOffset: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val endXpath: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val endOffset: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val pdfPage: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val pdfRects: List<List<Double>>? = null,
)

@Serializable
data class CommentCreateRequest(
    val body: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val highlightId: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val parentId: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val anchor: CommentAnchor? = null,
)

@Serializable
data class CommentUpdateRequest(val body: String)

/** Fehler-Body der Kommentar-Endpunkte (`{ "error": "body_too_long" }`). */
@Serializable
data class CommentErrorBody(val error: String = "")

/**
 * Fehler mit Code aus `{error: code}` der Kommentar-Endpunkte, z. B. `body_too_long`,
 * `comment_deleted`, `not_found`. Äquivalent zu `CommentAPIError` in `MerlinAPI.swift`.
 */
class CommentApiException(val code: String, val status: Int) : Exception(code) {
    val userMessage: String
        get() = when (code) {
            "body_too_long" -> "Der Kommentar darf höchstens 5000 Zeichen lang sein."
            "body_empty" -> "Der Kommentar ist leer."
            else -> "Der Kommentar konnte nicht gespeichert werden."
        }
}
