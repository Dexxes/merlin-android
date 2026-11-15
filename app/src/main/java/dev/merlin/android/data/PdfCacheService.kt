package dev.merlin.android.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Datei-Cache für die PDFs von PDF-Artikeln (`Article.isPdf`), Äquivalent zu
 * `PDFCacheService.swift`.
 *
 * Der Server speichert keine PDF, nur die Quell-URL. Der Reader lädt das Dokument beim Öffnen
 * von dort; dieser Cache hält die Datei für erneutes/Offline-Lesen vor. Er liegt in
 * `cacheDir/pdf/<sha256(url)>.pdf` und darf jederzeit verschwinden (Android räumt `cacheDir`
 * bei Speicherknappheit selbst auf – dann wird einfach neu geladen).
 *
 * Eigener `OkHttpClient` OHNE die Merlin-Interceptors: der Retrofit-Client aus [NetworkModule]
 * schreibt jede Anfrage auf die Merlin-Server-URL um und hängt Basic-Auth an – die Quelle ist
 * aber ein beliebiger Drittserver und darf die Zugangsdaten nie sehen.
 */
@Singleton
class PdfCacheService @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Fehlerklasse für "Antwort ist keine (brauchbare) PDF" – Netzwerkfehler bleiben [IOException]. */
    class PdfException(message: String) : IOException(message)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(RefererInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()
    }

    private val dir: File
        get() = File(context.cacheDir, "pdf").also { it.mkdirs() }

    private fun fileFor(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + ".pdf")
    }

    /**
     * Liefert die lokale Datei für [url] und lädt sie bei Bedarf zuerst herunter.
     *
     * @throws PdfException bei ungültiger URL, HTTP-Fehler, Nicht-PDF-Antwort oder zu großer Datei
     * @throws IOException bei Netzwerkfehlern
     */
    suspend fun fetch(url: String): File = withContext(Dispatchers.IO) {
        val destination = fileFor(url)
        if (destination.exists()) {
            // Änderungsdatum auffrischen, damit [prune] wie "zuletzt geöffnet" wirkt.
            destination.setLastModified(System.currentTimeMillis())
            return@withContext destination
        }

        val httpUrl = url.toHttpUrlOrNull()
            ?.takeIf { it.scheme == "http" || it.scheme == "https" }
            ?: throw PdfException("Ungültige PDF-URL")

        val request = Request.Builder()
            .url(httpUrl)
            .header("Accept", "application/pdf,*/*;q=0.8")
            .build()

        val temp = File(dir, "${destination.name}.part")
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw PdfException("HTTP ${response.code}")
                val body = response.body ?: throw PdfException("Leere Antwort")
                if (body.contentLength() > MAX_BYTES) throw PdfException("PDF zu groß")

                var total = 0L
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_BYTES) throw PdfException("PDF zu groß")
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            if (!looksLikePdf(temp)) throw PdfException("Antwort ist keine PDF")
            if (!temp.renameTo(destination)) throw PdfException("PDF konnte nicht gespeichert werden")
            destination
        } finally {
            temp.delete()
        }
    }

    /** PDFs beginnen mit `%PDF-`, die Spezifikation erlaubt aber Müll in den ersten 1024 Bytes. */
    private fun looksLikePdf(file: File): Boolean {
        val head = file.inputStream().use { input ->
            val buffer = ByteArray(1024)
            val read = input.read(buffer)
            if (read <= 0) return false
            String(buffer, 0, read, Charsets.ISO_8859_1)
        }
        return head.contains("%PDF-")
    }

    /** Löscht die gecachte Datei für [url] (Artikel gelöscht oder Datei unbrauchbar). */
    suspend fun remove(url: String) = withContext(Dispatchers.IO) {
        fileFor(url).delete()
        Unit
    }

    /** Löscht PDFs, die seit mehr als [days] Tagen nicht geöffnet wurden. */
    suspend fun prune(days: Int) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        Unit
    }

    /** Leert den gesamten PDF-Cache ("Cache leeren" in den Einstellungen). */
    suspend fun clear() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        Unit
    }

    private companion object {
        /** Obergrenze für eine einzelne PDF – größere Dateien werden abgelehnt statt den Speicher zu füllen. */
        const val MAX_BYTES = 100L * 1024 * 1024
        const val BUFFER_SIZE = 64 * 1024
    }
}
