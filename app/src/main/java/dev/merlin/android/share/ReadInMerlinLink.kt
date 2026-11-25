package dev.merlin.android.share

/**
 * Findet den Link für „Jetzt in Merlin lesen“ (siehe [dev.merlin.android.ui.share.ReadInMerlinActivity])
 * in den Texten eines geteilten bzw. markierten Inhalts. Äquivalent zu `findURL` der iOS-Action-Extension
 * `MerlinRead`: der erste http(s)-Link gewinnt, auch mitten im Text („Schau mal: https://…“).
 */
object ReadInMerlinLink {
    private val urlPattern = Regex("https?://[^\\s<>\"]+", RegexOption.IGNORE_CASE)

    /** Satzzeichen direkt hinter einem Link im Fließtext gehören nicht dazu. */
    private const val TRAILING_PUNCTUATION = ".,;:!?)]}'»“”"

    fun firstWebUrl(vararg texts: String?): String? {
        for (text in texts) {
            if (text.isNullOrBlank()) continue
            val match = urlPattern.find(text) ?: continue
            val url = trimTrailingPunctuation(match.value)
            if (url.substringAfter("://").isNotEmpty()) return url
        }
        return null
    }

    private fun trimTrailingPunctuation(url: String): String {
        var end = url.length
        while (end > 0 && url[end - 1] in TRAILING_PUNCTUATION) {
            // Eine schließende Klammer bleibt, wenn der Link selbst eine öffnet (Wikipedia-Links).
            if (url[end - 1] == ')' && url.substring(0, end).count { it == '(' } >= url.substring(0, end).count { it == ')' }) break
            end--
        }
        return url.substring(0, end)
    }
}
