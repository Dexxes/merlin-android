package dev.merlin.android.models

/**
 * Äquivalent zum `ArticleFilter`-Enum aus `ArticlesViewModel.swift`. Hier
 * bewusst in `models/` statt `viewmodel/` abgelegt, weil [dev.merlin.android.data.ArticleCacheService]
 * (Abschnitt 4) den Filter bereits für die Cache-Queries braucht, lange bevor
 * die eigentliche ViewModel-Schicht (Abschnitt 8) existiert.
 */
enum class ArticleFilter {
    ALL,
    FAVORITES,
    ARCHIVE,
    VIDEOS,
    ;

    /** UI-Label, Äquivalent zu `ArticleFilter.label` (Swift). Icon-Zuordnung liegt in der UI-Schicht (Compose-Dependency). */
    val label: String
        get() = when (this) {
            ALL -> "Ungelesen"
            FAVORITES -> "Favoriten"
            ARCHIVE -> "Archiv"
            VIDEOS -> "Videos"
        }

    /**
     * Wert, den der Server für `defaultView` erwartet (Settings-Sync). Der
     * Server kennt nur "all"/"favorites" – `archive`/`videos` sind App-lokale
     * Filter ohne Server-Äquivalent und fallen beim Sync auf "all" zurück
     * (1:1 wie `ArticleFilter.serverValue` in `ArticlesViewModel.swift`).
     */
    val serverValue: String
        get() = when (this) {
            ALL -> "all"
            FAVORITES -> "favorites"
            ARCHIVE -> "all"
            VIDEOS -> "all"
        }

    companion object {
        fun fromServerValue(value: String): ArticleFilter = when (value) {
            "favorites" -> FAVORITES
            else -> ALL
        }
    }
}
