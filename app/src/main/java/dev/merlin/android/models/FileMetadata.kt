package dev.merlin.android.models

/**
 * Eine Gruppe des Abschnitts „Metadaten“, den der Server unter den Inhalt eines Datei-Eintrags
 * setzt (merlin-nextcloud `MerlinFileService::metadataHtml`): Datei, EXIF, GPS, IPTC, XMP, ID3,
 * QuickTime/MP4 oder PDF. Äquivalent zu `FileMetadataGroup` in `FileMetadata.swift` (merlin-ios).
 */
data class FileMetadataGroup(
    val title: String,
    val entries: List<Entry>,
) {
    data class Entry(val label: String, val value: String)
}

/** Überschrift und Gruppen des Metadaten-Abschnitts. */
data class FileMetadata(
    val title: String,
    val groups: List<FileMetadataGroup>,
)

/**
 * Liest den Metadaten-Abschnitt aus dem Inhalt eines Datei-Eintrags – Port von
 * `FileMetadataParser` (merlin-ios). Die Reader-WebView zeigt den Abschnitt selbst (Styling in
 * `ReaderHtmlBuilder`); der Parser ist für Ansichten, die den Inhalt nicht rendern (PDF-Einträge),
 * und für den signierten Download-Link zum Teilen der Datei.
 */
object FileMetadataParser {
    private val SECTION = Regex("""<section class="merlin-file-metadata"[^>]*>""")
    private val HEADING = Regex("""<h2>(.*?)</h2>""", RegexOption.DOT_MATCHES_ALL)
    private val GROUP = Regex("""<summary>(.*?)</summary>(.*?)</details>""", RegexOption.DOT_MATCHES_ALL)
    private val ROW = Regex("""<tr><th>(.*?)</th><td>(.*?)</td></tr>""", RegexOption.DOT_MATCHES_ALL)
    private val DOWNLOAD_SRC = Regex("""<section class="merlin-file-metadata" data-download-src="([^"]+)"""")
    private val TAG = Regex("""<[^>]+>""")
    private val NUMERIC_ENTITY = Regex("""&#(x[0-9A-Fa-f]+|[0-9]+);""")

    /** Überschrift und Gruppen des Metadaten-Abschnitts in [html], `null` ohne einen. */
    fun parse(html: String): FileMetadata? {
        val start = SECTION.find(html) ?: return null
        val section = html.substring(start.range.last + 1)
        val title = HEADING.find(section)?.groupValues?.get(1)?.let(::plainText).orEmpty()
        val groups = GROUP.findAll(section).map { group ->
            FileMetadataGroup(
                title = plainText(group.groupValues[1]),
                entries = ROW.findAll(group.groupValues[2]).map { row ->
                    FileMetadataGroup.Entry(plainText(row.groupValues[1]), plainText(row.groupValues[2]))
                }.toList(),
            )
        }.toList()
        return if (groups.isEmpty()) null else FileMetadata(title, groups)
    }

    /**
     * Signierter Download-Link der Originaldatei (`data-download-src` am Metadaten-Abschnitt,
     * merlin-nextcloud ≥ 1.0.18), zum Teilen der Datei selbst. Braucht keine Zugangsdaten.
     */
    fun downloadUrl(html: String): String? =
        DOWNLOAD_SRC.find(html)?.groupValues?.get(1)?.let(::plainText)?.takeIf { it.isNotEmpty() }

    /**
     * Entfernt Tags (z. B. den Kartenlink um die GPS-Position) und dekodiert die Entities, die
     * `htmlspecialchars` schreibt, dazu numerische.
     */
    fun plainText(html: String): String {
        var text = TAG.replace(html, "")
        for ((entity, character) in listOf(
            "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'", "&#039;" to "'", "&nbsp;" to " ",
        )) {
            text = text.replace(entity, character)
        }
        text = NUMERIC_ENTITY.replace(text) { match ->
            val code = match.groupValues[1]
            val value = if (code.startsWith("x")) code.drop(1).toIntOrNull(16) else code.toIntOrNull()
            value?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: match.value
        }
        // &amp; zuletzt, damit "&amp;lt;" als "&lt;" stehen bleibt.
        return text.replace("&amp;", "&").trim()
    }
}
