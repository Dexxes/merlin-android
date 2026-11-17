package dev.merlin.android.models

import androidx.annotation.StringRes
import dev.merlin.android.R

/**
 * Oberste Inhaltskategorie eines Artikels, Äquivalent zu `ContentGroup` in
 * `ArticlesViewModel.swift`: "Video" und "Audio" sind eigene Kategorien, alles
 * andere (inkl. "Mixed") zählt zu den Seiten. Einträge aus „Merlin Dateien“
 * (`fileId` gesetzt, nur merlin-nextcloud) stehen nur unter [FILES], auch ein
 * hochgeladenes Video.
 */
enum class ContentGroup {
    PAGES,
    VIDEOS,
    AUDIO,
    FILES,
    ;

    /** Wert für den `contentType`-Parameter von `GET /api/articles`. */
    val contentType: String
        get() = when (this) {
            PAGES -> "page"
            VIDEOS -> "video"
            AUDIO -> "audio"
            FILES -> "file"
        }

    /** Tab-Beschriftung im Seitenmenü (siehe `ListFlyoutModifier.swift`), als String-Ressource. */
    @get:StringRes
    val label: Int
        get() = when (this) {
            PAGES -> R.string.navigationMenu_tab_text
            VIDEOS -> R.string.navigationMenu_tab_video
            AUDIO -> R.string.navigationMenu_tab_audio
            FILES -> R.string.navigationMenu_tab_files
        }

    companion object {
        fun of(article: Article): ContentGroup = when {
            article.fileId != null -> FILES
            article.category == "Video" -> VIDEOS
            article.category == "Audio" -> AUDIO
            else -> PAGES
        }
    }
}

/**
 * Äquivalent zum `ArticleFilter`-Enum aus `ArticlesViewModel.swift`: Text/Video/Audio
 * je mit Weiterlesen/Ungelesen/Favoriten/Archiv, Dateien ohne „Weiter…“ (Bilder und
 * Dateien haben keinen Lesefortschritt). Liegt in
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
    FILES_UNREAD(ContentGroup.FILES, Kind.UNREAD),
    FILES_FAVORITES(ContentGroup.FILES, Kind.FAVORITES),
    FILES_ARCHIVE(ContentGroup.FILES, Kind.ARCHIVE),
    ;

    /** Unteransicht innerhalb einer Gruppe, unabhängig von Text/Video/Audio. */
    enum class Kind { CONTINUE, UNREAD, FAVORITES, ARCHIVE }

    /** Angefangene, aber nicht fertig gelesene/geschaute/gehörte Inhalte (rein client-seitig gefiltert). */
    val isContinue: Boolean get() = kind == Kind.CONTINUE

    /**
     * UI-Label als String-Ressource, Äquivalent zu `ArticleFilter.label` (Swift). Icons liegen
     * in der UI-Schicht.
     */
    @get:StringRes
    val label: Int
        get() = when (this) {
            PAGES_CONTINUE -> R.string.articleList_filter_continueReading
            PAGES_UNREAD -> R.string.articleList_filter_unread
            VIDEOS_CONTINUE -> R.string.articleList_filter_continueWatching
            VIDEOS_UNREAD -> R.string.articleList_filter_unseen
            AUDIO_CONTINUE -> R.string.articleList_filter_continueListening
            AUDIO_UNREAD -> R.string.articleList_filter_unheard
            FILES_UNREAD -> R.string.articleList_filter_unopened
            PAGES_FAVORITES, VIDEOS_FAVORITES, AUDIO_FAVORITES, FILES_FAVORITES -> R.string.articleList_filter_favorites
            PAGES_ARCHIVE, VIDEOS_ARCHIVE, AUDIO_ARCHIVE, FILES_ARCHIVE -> R.string.articleList_filter_archive
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
                ContentGroup.FILES -> "files"
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
     * Wie [matches], berücksichtigt aber die Einzel-Tag-Ansicht: ist [tagScope] gesetzt
     * (der gewählte Tag plus seine Unter-Tags, siehe [TagTree.scope]), zählt der Filter
     * selbst nicht, sondern nur, ob der Artikel einen Tag daraus trägt und ob archivierte
     * Artikel eingeblendet sind (Äquivalent zu `matches(_:tagIds:showArchivedForTag:)`).
     */
    fun matches(article: Article, tagScope: Set<Int>?, showArchivedForTag: Boolean): Boolean {
        if (tagScope == null) return matches(article)
        if (article.tags.none { it.id in tagScope }) return false
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
