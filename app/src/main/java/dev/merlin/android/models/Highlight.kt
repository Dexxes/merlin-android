package dev.merlin.android.models

import kotlinx.serialization.Serializable

/** Äquivalent zu `Highlight.swift`. */
@Serializable
data class Highlight(
    val id: Int,
    val articleId: Int,
    val highlightedText: String,
    val startXpath: String,
    val startOffset: Int,
    val endXpath: String,
    val endOffset: Int,
    val color: String,
    val createdAt: String,
)

/** Payload für das Erstellen eines Highlights (POST), ohne server-generierte Felder. */
@Serializable
data class HighlightCreate(
    val highlightedText: String,
    val startXpath: String,
    val startOffset: Int,
    val endXpath: String,
    val endOffset: Int,
    val color: String,
)
