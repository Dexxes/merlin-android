package dev.merlin.android.network

/**
 * Hilfsfunktionen, die Server-URLs auf `https://` festlegen.
 *
 * Die App erlaubt (bewusst) keinen Cleartext-Traffic – Android blockt `http://` ab
 * targetSdk 28 per Default mit "CLEARTEXT communication … not permitted by network
 * security policy". Server hinter einem TLS-terminierenden Reverse-Proxy liefern im
 * Login-Flow v2 aber oft `http://`-URLs (Poll-Endpoint, Login-Seite, `server`), wenn
 * Nextcloud `overwriteprotocol` bzw. merlin-server den Proxy-Header nicht auswertet.
 * Deshalb werden solche URLs clientseitig auf https angehoben, statt Cleartext zu
 * erlauben.
 */
object HttpsUrls {
    private val schemePrefix = Regex("^(?:https?:/*)+", RegexOption.IGNORE_CASE)

    /**
     * Ersetzt ein (auch doppelt/fehlerhaft eingegebenes) Schema wie `http://`,
     * `https://http://` oder `https://https://` durch genau ein `https://`.
     * URLs ohne Schema bleiben unverändert.
     */
    fun upgrade(url: String): String {
        val trimmed = url.trim()
        val match = schemePrefix.find(trimmed) ?: return trimmed
        val rest = trimmed.substring(match.range.last + 1)
        // "https:" allein bzw. ohne Host bleibt leer – Validierung macht der Aufrufer.
        return if (rest.isEmpty()) trimmed else "https://$rest"
    }
}
