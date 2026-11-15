package dev.merlin.android.models

/**
 * Oberste Inhaltskategorie eines Artikels, Äquivalent zu `ContentGroup` in
 * `ArticlesViewModel.swift`: "Video" und "Audio" sind eigene Kategorien, alles
 * andere (inkl. "Mixed") zählt zu den Seiten. Einträge aus „Merlin Dateien“
 * (`fileId` gesetzt) gehören zu keiner dieser drei Gruppen – den Dateien-Tab
 * gibt es auf Android noch nicht, daher hier kein `FILES`.
 */
enum class ContentGroup {
    PAGES,
    VIDEOS,
    AUDIO,
    ;

    /** Wert für den `contentType`-Parameter von `GET /api/articles`. */
    val contentType: String
        get() = when (this) {
            PAGES -> "page"
            VIDEOS -> "video"
            AUDIO -> "audio"
        }

    /** Tab-Beschriftung im Seitenmenü (siehe `ListFlyoutModifier.swift`). */
    val label: String
        get() = when (this) {
            PAGES -> "Text"
            VIDEOS -> "Video"
            AUDIO -> "Audio"
        }

    companion object {
        /** `null` für Datei-Einträge, die in keine der drei Gruppen fallen. */
        fun of(article: Article): ContentGroup? = when {
            article.fileId != null -> null
            article.category == "Video" -> VIDEOS
            article.category == "Audio" -> AUDIO
            else -> PAGES
        }
    }
}

/**
 * Äquivalent zum `ArticleFilter`-Enum aus `ArticlesViewModel.swift`: drei Gruppen
 * (Text/Video/Audio), je mit Weiterlesen/Ungelesen/Favoriten/Archiv. Liegt in
 * `models/`, weil [dev.merlin.android.data.ArticleCacheService] den Filter für die
 * Offline-Abfragen braucht. Die Enum-Namen werden in DataStore gespeichert; alte
 * Werte (`ALL`, `FAVORITES`, …) übersetzt [fromStoredName].
 */
enum class ArticleFilter(val group: ContentGroup, val kind: Kind) {
    PAGES_CONTINUE(ContentGroup.PAGES, Kind.CONTINUE),
    PAGES_UNREAD(ContentGroup.PAGES, Kind.UNREAD),
    PAGES_FAVORITES(ContentGroup.PAGES, Kind.FAVORITES),
    PAGES_ARCHIVE(ContentGroup.PAGES, Kind.ARCHIVE),
    VIDEOS_CONTINUE(ContentGroup.VIDEOS, Kind.CONTINUE),
    VIDEOS_UNREAD(ContentGroup.VIDEOS, Kind.UNREAD),
    VIDEOS_FAVORITES(ContentGroup.VIDEOS, Kind.FAVORITES),
    VIDEOS_ARCHIVE(ContentGroup.VIDEOS, Kind.ARCHIVE),
    AUDIO_CONTINUE(ContentGroup.AUDIO, Kind.CONTINUE),
    AUDIO_UNREAD(ContentGroup.AUDIO, Kind.UNREAD),
    AUDIO_FAVORITES(ContentGroup.AUDIO, Kind.FAVORITES),
    AUDIO_ARCHIVE(ContentGroup.AUDIO, Kind.ARCHIVE),
    ;

    /** Unteransicht innerhalb einer Gruppe, unabhängig von Text/Video/Audio. */
    enum class Kind { CONTINUE, UNREAD, FAVORITES, ARCHIVE }

    /** Angefangene, aber nicht fertig gelesene/geschaute/gehörte Inhalte (rein client-seitig gefiltert). */
    val isContinue: Boolean get() = kind == Kind.CONTINUE

    /** UI-Label, Äquivalent zu `ArticleFilter.label` (Swift). Icons liegen in der UI-Schicht. */
    val label: String
        get() = when (this) {
            PAGES_CONTINUE -> "Weiterlesen"
            PAGES_UNREAD -> "Ungelesen"
            VIDEOS_CONTINUE -> "Weiterschauen"
            VIDEOS_UNREAD -> "Ungesehen"
            AUDIO_CONTINUE -> "Weiterhören"
            AUDIO_UNREAD -> "Ungehört"
            PAGES_FAVORITES, VIDEOS_FAVORITES, AUDIO_FAVORITES -> "Favoriten"
            PAGES_ARCHIVE, VIDEOS_ARCHIVE, AUDIO_ARCHIVE -> "Archiv"
        }

    /**
     * Wert für das `defaultView`-Setting, identisch zu `ArticleFilter.serverValue`
     * (Swift) und der Web-Oberfläche, damit die Standardansicht geräteübergreifend passt.
     */
    val serverValue: String
        get() {
            val prefix = when (group) {
                ContentGroup.PAGES -> "pages"
                ContentGroup.VIDEOS -> "videos"
                ContentGroup.AUDIO -> "audio"
            }
            val suffix = when (kind) {
                Kind.CONTINUE -> "continue"
                Kind.UNREAD -> "unread"
                Kind.FAVORITES -> "favorites"
                Kind.ARCHIVE -> "archived"
            }
            return "$prefix-$suffix"
        }

    /** Ob [article] in diesem Filter erscheint (ohne Tag-Ansicht), spiegelt `fetchForFilter`. */
    fun matches(article: Article): Boolean {
        if (ContentGroup.of(article) != group) return false
        return when (kind) {
            Kind.CONTINUE -> !article.isArchived && article.scrollProgress > 0f && article.scrollProgress < 1f
            Kind.UNREAD -> !article.isArchived
            // Bewusst OHNE isArchived-Bedingung: Favoriten unabhängig vom Archiv-Status.
            Kind.FAVORITES -> article.isFavorite
            Kind.ARCHIVE -> article.isArchived
        }
    }

    /**
     * Wie [matches], berücksichtigt aber die Einzel-Tag-Ansicht: ist [tagId] gesetzt,
     * zählt der Filter selbst nicht, sondern nur der Tag und ob archivierte Artikel
     * eingeblendet sind (Äquivalent zu `matches(_:tagIds:showArchivedForTag:)`).
     */
    fun matches(article: Article, tagId: Int?, showArchivedForTag: Boolean): Boolean {
        if (tagId == null) return matches(article)
        if (article.tags.none { it.id == tagId }) return false
        return showArchivedForTag || !article.isArchived
    }

    companion object {
        val DEFAULT = PAGES_UNREAD

        /** Aus Server-Wert (z. B. "pages-unread") konvertieren, wie `fromServerValue` (Swift). */
        fun fromServerValue(value: String): ArticleFilter {
            entries.firstOrNull { it.serverValue == value }?.let { return it }
            return when (value) {
                // Legacy-Werte aus der Zeit vor der Text/Video-Aufteilung.
                "favorites" -> PAGES_FAVORITES
                "video" -> VIDEOS_UNREAD
                // Dateien gibt es auf Android (noch) nicht – passende Ansicht der Seiten nehmen.
                "files-favorites" -> PAGES_FAVORITES
                "files-archived" -> PAGES_ARCHIVE
                else -> DEFAULT
            }
        }

        /** Aus dem in DataStore gespeicherten Namen, inkl. der alten Werte vor dieser Umstellung. */
        fun fromStoredName(name: String?): ArticleFilter = when (name) {
            null -> DEFAULT
            "ALL" -> PAGES_UNREAD
            "FAVORITES" -> PAGES_FAVORITES
            "ARCHIVE" -> PAGES_ARCHIVE
            "VIDEOS" -> VIDEOS_UNREAD
            else -> entries.firstOrNull { it.name == name } ?: DEFAULT
        }
    }
}
