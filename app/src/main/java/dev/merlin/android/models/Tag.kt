package dev.merlin.android.models

import kotlinx.serialization.Serializable

/** Äquivalent zu `Tag.swift`. */
@Serializable
data class Tag(
    val id: Int,
    val name: String,
    val color: String? = null,
)
