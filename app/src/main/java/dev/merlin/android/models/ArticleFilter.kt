package dev.merlin.android.models

/**
 * Äquivalent zum `ArticleFilter`-Enum aus `ArticlesViewModel.swift`. Hier
 * bewusst in `models/` statt `viewmodel/` abgelegt, weil [dev.merlin.android.data.ArticleCacheService]
 * (Abschnitt 4) den Filter bereits für die Cache-Queries braucht, lange bevor
 * die eigentliche ViewModel-Schicht (Abschnitt 8) existiert.
 *
 * Zwei oberste Kategorien (Seiten/Videos), je mit eigener Unread(/Unseen)-
 * /Favorites-/Archive-Unteransicht - siehe getCounts() in
 * merlin-standalone-server/src/Db/ArticleRepository.php für das serverseitige
 * Äquivalent dieser Aufteilung.
 */
enum class ArticleFilter {
    PAGES_UNREAD,
    PAGES_FAVORITES,
    PAGES_ARCHIVE,
    VIDEOS_UNREAD,
    VIDEOS_FAVORITES,
    VIDEOS_ARCHIVE,
    ;

    /** Ob dieser Filter zur Videos- oder zur Seiten-Gruppe gehört (UI-Gruppierung). */
    val isVideo: Boolean
        get() = this == VIDEOS_UNREAD || this == VIDEOS_FAVORITES || this == VIDEOS_ARCHIVE

    /** UI-Label, Äquivalent zu `ArticleFilter.label` (Swift). Icon-Zuordnung liegt in der UI-Schicht (Compose-Dependency). */
    val label: String
        get() = when (this) {
            PAGES_UNREAD -> "Ungelesen"
            PAGES_FAVORITES -> "Favoriten"
            PAGES_ARCHIVE -> "Archiv"
            VIDEOS_UNREAD -> "Ungesehen"
            VIDEOS_FAVORITES -> "Favoriten"
            VIDEOS_ARCHIVE -> "Archiv"
        }

    /**
     * Wert, den der Server für `defaultView` erwartet (Settings-Sync),
     * identisch zur Web-Oberfläche (siehe App.vue/SettingsController.php in
     * merlin-nextcloud) und zu `ArticleFilter.serverValue` in
     * `ArticlesViewModel.swift`.
     */
    val serverValue: String
        get() = when (this) {
            PAGES_UNREAD -> "pages-unread"
            PAGES_FAVORITES -> "pages-favorites"
            PAGES_ARCHIVE -> "pages-archived"
            VIDEOS_UNREAD -> "videos-unread"
            VIDEOS_FAVORITES -> "videos-favorites"
            VIDEOS_ARCHIVE -> "videos-archived"
        }

    companion object {
        fun fromServerValue(value: String): ArticleFilter = when (value) {
            "pages-unread" -> PAGES_UNREAD
            "pages-favorites" -> PAGES_FAVORITES
            "pages-archived" -> PAGES_ARCHIVE
            "videos-unread" -> VIDEOS_UNREAD
            "videos-favorites" -> VIDEOS_FAVORITES
            "videos-archived" -> VIDEOS_ARCHIVE
            // Legacy-Werte aus der Zeit vor der Pages/Videos-Aufteilung.
            "favorites" -> PAGES_FAVORITES
            "video" -> VIDEOS_UNREAD
            else -> PAGES_UNREAD
        }
    }
}
