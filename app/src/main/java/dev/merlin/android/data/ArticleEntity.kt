package dev.merlin.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room-Zeile für den Artikel-Cache (Abschnitt 4, Äquivalent zu
 * `ArticleCacheService.swift`'s `article-cache.json`). Bewusst minimal: das
 * komplette [dev.merlin.android.models.Article] wird als JSON-Blob
 * gespeichert statt auf Spalten normalisiert, damit Filter-/Eviction-Logik
 * 1:1 wie im iOS-Original in Kotlin (statt in SQL) lebt und beim nächsten
 * Server-Feld nicht zwingend eine Room-Migration nötig ist.
 */
@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: Int,
    val json: String,
    /**
     * Epoch-Millis des letzten lokalen Syncs (Upsert) – treibt die
     * Retention-basierte Eviction in [ArticleCacheService] (Settings →
     * "Artikel offline speichern", 0–365 Tage). Rein lokale Bookkeeping-
     * Spalte, kein Server-Feld.
     */
    val cachedAt: Long = 0L,
)
