package dev.merlin.android.models

/**
 * Äquivalent zum `ArticleFilter`-Enum aus `ArticlesViewModel.swift`. Hier
 * bewusst in `models/` statt `viewmodel/` abgelegt, weil [dev.merlin.android.data.ArticleCacheService]
 * (Abschnitt 4) den Filter bereits für die Cache-Queries braucht, lange bevor
 * die eigentliche ViewModel-Schicht (Abschnitt 8) existiert.
 */
enum class ArticleFilter {
    CONTINUE_READING,
    CONTINUE_WATCHING,
    ALL,
    FAVORITES,
    ARCHIVE,
    VIDEOS,
    ;

    /**
     * Ob dieser Filter angefangene, aber nicht fertig gelesene/geschaute
     * Inhalte listet (Weiterlesen/Weiterschauen) – rein client-seitig aus
     * `Article.scrollProgress` gefiltert, siehe `ArticlesViewModel.fetchForFilter`.
     */
    val isContinue: Boolean
        get() = this == CONTINUE_READING || this == CONTINUE_WATCHING

    /** UI-Label, Äquivalent zu `ArticleFilter.label` (Swift). Icon-Zuordnung liegt in der UI-Schicht (Compose-Dependency). */
    val label: String
        get() = when (this) {
            CONTINUE_READING -> "Weiterlesen"
            CONTINUE_WATCHING -> "Weiterschauen"
            ALL -> "Ungelesen"
            FAVORITES -> "Favoriten"
            ARCHIVE -> "Archiv"
            VIDEOS -> "Videos"
        }

    /**
     * Wert, den der Server für `defaultView` erwartet (Settings-Sync). Der
     * Server kennt nur "all"/"favorites" – `archive`/`videos`/Weiterlesen-
     * Filter sind App-lokal ohne Server-Äquivalent und fallen beim Sync auf
     * "all" zurück (1:1 wie `ArticleFilter.serverValue` in `ArticlesViewModel.swift`).
     */
    val serverValue: String
        get() = when (this) {
            ALL -> "all"
            FAVORITES -> "favorites"
            ARCHIVE, VIDEOS, CONTINUE_READING, CONTINUE_WATCHING -> "all"
        }

    companion object {
        fun fromServerValue(value: String): ArticleFilter = when (value) {
            "favorites" -> FAVORITES
            else -> ALL
        }
    }
}
