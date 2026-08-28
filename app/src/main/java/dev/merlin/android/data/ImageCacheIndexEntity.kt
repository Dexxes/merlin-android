package dev.merlin.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Merkt sich pro Artikel, welche Bild-URLs für ihn in den Coil-Disk-Cache
 * vorgewärmt wurden – Äquivalent zu `img-index.json` in `ImageCacheService.swift`.
 * Ohne diesen Index könnte [ImageCacheService.evict] beim Archivieren/Löschen
 * eines Artikels nicht gezielt nur dessen Bilder aus dem Cache entfernen.
 */
@Entity(tableName = "image_cache_index")
data class ImageCacheIndexEntity(
    @PrimaryKey val articleId: Int,
    /** JSON-codierte `List<String>` der zwischengespeicherten Bild-URLs. */
    val urlsJson: String,
)
