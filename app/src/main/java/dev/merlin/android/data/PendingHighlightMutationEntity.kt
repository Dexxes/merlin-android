package dev.merlin.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Äquivalent zu `PendingHighlightMutation.Kind` aus `OfflineHighlightQueue.swift`. */
enum class PendingHighlightMutationKind {
    CREATE,
    DELETE,
}

/**
 * Room-Zeile für eine wartende Highlight-Mutation – Äquivalent zu
 * `PendingHighlightMutation` (`OfflineHighlightQueue.swift`).
 *
 * Bei [PendingHighlightMutationKind.CREATE]: [tempId] identifiziert die
 * Mutation für die Cancel-Logik (Löschen vor Drain), [localHighlightId] ist
 * die negative Platzhalter-ID, unter der das Highlight optimistisch im
 * [HighlightCacheService] liegt, [payloadJson] der JSON-codierte
 * `HighlightCreate`-Request-Body.
 *
 * Bei [PendingHighlightMutationKind.DELETE]: [highlightId] ist die echte
 * Server-ID des zu löschenden Highlights.
 */
@Entity(tableName = "pending_highlight_mutations")
data class PendingHighlightMutationEntity(
    @PrimaryKey val id: String,
    val articleId: Int,
    val kind: PendingHighlightMutationKind,
    val tempId: String? = null,
    val localHighlightId: Int? = null,
    val payloadJson: String? = null,
    val highlightId: Int? = null,
)
