package dev.merlin.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room-Zeile für den Highlight-Cache, gruppiert pro Artikel – Äquivalent zu
 * `HighlightCacheService.swift`'s JSON-Datei (dort flach, hier eine Zeile pro
 * Artikel mit allen seinen Highlights als JSON-Blob, analog zu
 * [ImageCacheIndexEntity], damit [HighlightDao.get]/[HighlightDao.upsert]
 * ohne Normalisierung auskommen).
 */
@Entity(tableName = "highlights")
data class HighlightEntity(
    @PrimaryKey val articleId: Int,
    /** JSON-codierte `List<dev.merlin.android.models.Highlight>`. */
    val json: String,
)
