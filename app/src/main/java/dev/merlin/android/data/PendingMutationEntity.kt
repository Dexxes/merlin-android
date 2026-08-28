package dev.merlin.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Äquivalent zu `PendingMutationKind` aus `OfflineMutationQueue.swift`. */
enum class PendingMutationKind {
    TOGGLE_ARCHIVE,
    TOGGLE_FAVORITE,
    SET_TAGS,
    DELETE,
}

/**
 * Room-Zeile für eine fehlgeschlagene Mutation, die bei nächster
 * Netzwerkverbindung wiederholt wird – Äquivalent zu `PendingMutation`
 * (`OfflineMutationQueue.swift`). [addTagIdsJson]/[removeTagIdsJson] sind nur
 * für [PendingMutationKind.SET_TAGS] gesetzt (JSON-codierte `List<Int>`, da
 * Room keine nativen Listen-Spalten kennt).
 *
 * Bewusst ein Delta (Add-/Remove-IDs) statt eines vollständigen Ziel-Tag-Sets:
 * ein Voll-Set, das beim Drain gegen den dann aktuellen Server-Stand gedifft
 * wird, würde jedes Tag löschen, das während der Offline-Phase von einem
 * anderen Client hinzugefügt wurde (das Tag steht ja nicht im alten Voll-Set).
 * Mit einem Delta werden beim Replay nur die tatsächlich vom Nutzer
 * angefassten Tag-IDs berührt.
 */
@Entity(tableName = "pending_mutations")
data class PendingMutationEntity(
    @PrimaryKey val id: String,
    val articleId: Int,
    val kind: PendingMutationKind,
    val addTagIdsJson: String? = null,
    val removeTagIdsJson: String? = null,
)
