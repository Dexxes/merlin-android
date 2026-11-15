package dev.merlin.android.ui.reader

import android.net.Uri
import android.webkit.WebResourceResponse
import dev.merlin.android.models.InstalledFontFamily
import dev.merlin.android.models.InstalledFonts
import dev.merlin.android.models.ReaderFont
import java.io.File
import java.io.FileInputStream

/**
 * Bringt eine auf dem Gerät installierte Schrift (siehe [InstalledFonts]) in den Reader-WebView.
 *
 * Chromium im WebView findet Systemschriften nicht zuverlässig über ihren Familiennamen (nur die
 * Aliase aus `fonts.xml` wie `serif`/`sans-serif-condensed`), und `file://` ist für die per
 * `loadDataWithBaseURL(article.url, …)` geladene Seite gesperrt. Deshalb verweist ein
 * `@font-face` auf eine Pseudo-URL unter [HOST], die [intercept] aus `shouldInterceptRequest`
 * mit dem Dateiinhalt beantwortet. Ausgeliefert werden nur Dateien, die [InstalledFonts] selbst
 * gelistet hat – kein beliebiger Dateizugriff über die URL.
 */
object ReaderLocalFonts {
    private const val HOST = "merlin-local-fonts.invalid"

    /** CSS-Familienname, unter dem die gewählte Schrift im Reader-HTML registriert wird. */
    private const val CSS_FAMILY = "MerlinInstalledFont"

    /** `font-family`-Wert für den Artikeltext: installierte Schrift zuerst, Voreinstellung als Fallback. */
    fun fontFamilyCss(preset: ReaderFont, custom: InstalledFontFamily?): String =
        if (custom == null) preset.cssValue else "'$CSS_FAMILY', ${preset.cssValue}"

    /** `@font-face`-Regeln für alle Schnitte der gewählten Familie (leer ohne Auswahl). */
    fun fontFaceCss(custom: InstalledFontFamily?): String {
        if (custom == null) return ""
        return custom.faces.joinToString("\n") { face ->
            val url = Uri.Builder().scheme("https").authority(HOST).appendPath("f")
                .appendQueryParameter("p", face.path).build().toString()
            val weight = if (face.minWeight == face.maxWeight) "${face.minWeight}" else "${face.minWeight} ${face.maxWeight}"
            val format = if (face.path.endsWith(".otf", ignoreCase = true)) "opentype" else "truetype"
            "@font-face { font-family: '$CSS_FAMILY'; src: url('$url') format('$format'); " +
                "font-weight: $weight; font-style: ${if (face.italic) "italic" else "normal"}; font-display: block; }"
        }
    }

    /** Antwort für eine Schrift-URL unter [HOST]; `null` für alle anderen Requests. */
    fun intercept(url: Uri?): WebResourceResponse? {
        if (url?.host != HOST) return null
        val path = url.getQueryParameter("p")
        val allowed = path != null && runCatching {
            InstalledFonts.families().any { family -> family.faces.any { it.path == path } }
        }.getOrDefault(false)
        if (!allowed) return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), null)
        val stream = runCatching { FileInputStream(File(path!!)) }.getOrNull()
            ?: return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), null)
        val mime = if (path!!.endsWith(".otf", ignoreCase = true)) "font/otf" else "font/ttf"
        // Die Seite hat die Origin des Artikels – ohne CORS-Header verwirft Chromium die Schrift.
        return WebResourceResponse(
            mime, null, 200, "OK",
            mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "max-age=86400"),
            stream,
        )
    }
}
