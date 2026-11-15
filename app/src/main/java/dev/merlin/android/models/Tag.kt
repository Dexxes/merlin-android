package dev.merlin.android.models

import kotlinx.serialization.Serializable

/** Äquivalent zu `Tag.swift`. */
@Serializable
data class Tag(
    val id: Int,
    val name: String,
    val color: String? = null,
    /**
     * Eltern-Tag bei verschachtelten Tags; `null` = oberste Ebene. Nur merlin-nextcloud liefert
     * das Feld, beim Standalone-Server sind daher alle Tags oberste Ebene (siehe [TagTree]).
     */
    val parentId: Int? = null,
)
