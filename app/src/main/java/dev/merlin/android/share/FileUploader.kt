package dev.merlin.android.share

import dev.merlin.android.network.MerlinApi
import dev.merlin.android.network.MerlinUserAgent
import dev.merlin.android.network.RegisterFileRequest
import dev.merlin.android.network.RequestFileTargetRequest
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import retrofit2.HttpException

/**
 * Eine Datei aus dem geteilten Inhalt, in den Cache-Ordner der App kopiert
 * (`cacheDir/merlin-share/…`), damit sie unabhängig von der teilenden App
 * gelesen werden kann. Äquivalent zu `SharedFile` in `FileUploader.swift`.
 */
data class SharedFile(
    val file: File,
    val name: String,
    val mimeType: String,
    val size: Long,
)

/**
 * Lädt geteilte Dateien in den Nextcloud-Ordner „Merlin Dateien“ und legt ihre
 * Einträge in der Leseliste an (merlin-nextcloud ≥ 1.0.18). Portierung von
 * `FileUploader.swift` (MerlinShare):
 *
 * 1. `POST api/files/target` → Zielpfad im Unterordner der Dateiart
 * 2. WebDAV-Upload: ein `PUT` für kleine Dateien, Nextcloud Chunked Upload v2
 *    (`MKCOL` + nummerierte `PUT`s + `MOVE`) für große, damit ein Video nie ganz
 *    in den Speicher muss und ein fehlgeschlagener Teil allein wiederholt wird
 * 3. `POST api/files` → Eintrag in der Liste, mit den gewählten Tags
 *
 * Die Datei geht nie durch die Merlin-API selbst (PHP-Upload-Limits sind für
 * Videos zu klein). Die WebDAV-Anfragen laufen über einen eigenen OkHttp-Client,
 * weil der App-Client jede Anfrage per `BaseUrlInterceptor` auf den API-Pfad
 * umschreibt.
 */
class FileUploader(
    private val api: MerlinApi,
    private val baseUrl: String,
    private val authorization: String,
    private val json: Json,
) {
    sealed class UploadError : Exception() {
        data class Http(val code: Int, val serverMessage: String?) : UploadError()
        data object InvalidResponse : UploadError()
        data object UnreadableFile : UploadError()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    /** Nextcloud setzt große Dateien beim `MOVE` zusammen, das kann dauern. */
    private val assembleClient = client.newBuilder()
        .readTimeout(600, TimeUnit.SECONDS)
        .build()

    /**
     * Lädt [file] hoch und meldet sie an. [progress] bekommt den hochgeladenen
     * Anteil dieser Datei (0…1).
     */
    suspend fun upload(file: SharedFile, tagIds: List<Int>, progress: (Double) -> Unit) {
        val target = requestTarget(file)
        val destination = (baseUrl + target.davPath).toHttpUrlOrNull() ?: throw UploadError.InvalidResponse

        if (file.size <= CHUNK_SIZE) {
            retrying { putFile(file, destination, progress) }
        } else {
            val uploadsRoot = (baseUrl + target.uploadsPath).toHttpUrlOrNull() ?: throw UploadError.InvalidResponse
            chunkedUpload(file, destination, uploadsRoot, progress)
        }
        progress(1.0)
        retrying { register(target.path, tagIds) }
    }

    // ── Merlin-API ──────────────────────────────────────────────────────────

    private suspend fun requestTarget(file: SharedFile) = apiCall {
        api.requestFileTarget(RequestFileTargetRequest(name = file.name, mimeType = file.mimeType))
    }

    private suspend fun register(path: String, tagIds: List<Int>) {
        apiCall { api.registerFile(tagIds, RegisterFileRequest(path = path)).close() }
    }

    /** Wandelt Retrofit-Fehler in [UploadError.Http] (mit der `error`-Meldung des Servers) um. */
    private suspend fun <T> apiCall(block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        throw UploadError.Http(e.code(), serverMessage(body))
    } catch (e: kotlinx.serialization.SerializationException) {
        throw UploadError.InvalidResponse
    }

    // ── WebDAV ──────────────────────────────────────────────────────────────

    private suspend fun putFile(file: SharedFile, destination: HttpUrl, progress: (Double) -> Unit) {
        val body = ProgressFileBody(file.file, file.mimeType.toMediaTypeOrNull()) { sent ->
            progress(if (file.size > 0) minOf(1.0, sent.toDouble() / file.size) else 1.0)
        }
        perform(client, request(destination).put(body).build())
    }

    private suspend fun chunkedUpload(
        file: SharedFile,
        destination: HttpUrl,
        uploadsRoot: HttpUrl,
        progress: (Double) -> Unit,
    ) {
        val folder = uploadsRoot.newBuilder().addPathSegment("merlin-" + UUID.randomUUID()).build()
        val destinationHeader = destination.toString()
        val totalLength = file.size.toString()

        retrying {
            perform(client, request(folder).method("MKCOL", null).header("Destination", destinationHeader).build())
        }

        val handle = try {
            RandomAccessFile(file.file, "r")
        } catch (e: IOException) {
            throw UploadError.UnreadableFile
        }
        handle.use { raf ->
            var offset = 0L
            var index = 1
            while (offset < file.size) {
                val length = minOf(CHUNK_SIZE, file.size - offset).toInt()
                val chunk = ByteArray(length)
                withContext(Dispatchers.IO) {
                    raf.seek(offset)
                    raf.readFully(chunk)
                }
                val chunkUrl = folder.newBuilder().addPathSegment(index.toString()).build()
                retrying {
                    perform(
                        client,
                        request(chunkUrl)
                            .put(chunk.toRequestBody())
                            .header("Destination", destinationHeader)
                            .header("OC-Total-Length", totalLength)
                            .build(),
                    )
                }
                offset += length
                index += 1
                progress(offset.toDouble() / file.size)
            }
        }

        perform(
            assembleClient,
            request(folder.newBuilder().addPathSegment(".file").build())
                .method("MOVE", null)
                .header("Destination", destinationHeader)
                .header("OC-Total-Length", totalLength)
                .build(),
        )
    }

    // ── HTTP-Helfer ─────────────────────────────────────────────────────────

    private fun request(url: HttpUrl) = Request.Builder().url(url)
        .header("Authorization", authorization)
        .header("User-Agent", MerlinUserAgent.value)

    private suspend fun perform(client: OkHttpClient, request: Request) = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UploadError.Http(response.code, serverMessage(runCatching { response.body.string() }.getOrNull()))
            }
        }
    }

    private fun serverMessage(body: String?): String? = body?.let {
        runCatching { (json.parseToJsonElement(it) as? JsonObject)?.get("error")?.jsonPrimitive?.content }.getOrNull()
    }?.takeIf { it.isNotEmpty() }

    /**
     * Wiederholt Netzwerkfehler und Serverfehler (5xx, 429); Client-Fehler
     * (falsches Passwort, fehlender Endpunkt) schlagen sofort fehl.
     */
    private suspend fun <T> retrying(operation: suspend () -> T): T {
        var attempt = 1
        while (true) {
            try {
                return operation()
            } catch (e: UploadError.Http) {
                if (e.code < 500 && e.code != 429) throw e
                if (attempt >= ATTEMPTS) throw e
            } catch (e: IOException) {
                if (attempt >= ATTEMPTS) throw e
            }
            attempt += 1
            delay(attempt * 1000L)
        }
    }

    /** Streamt die Datei in Blöcken und meldet die gesendeten Bytes. */
    private class ProgressFileBody(
        private val file: File,
        private val contentType: MediaType?,
        private val onProgress: (Long) -> Unit,
    ) : RequestBody() {
        override fun contentType() = contentType
        override fun contentLength() = file.length()
        override fun writeTo(sink: BufferedSink) {
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var sent = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    sink.write(buffer, 0, read)
                    sent += read
                    onProgress(sent)
                }
            }
        }
    }

    companion object {
        /**
         * Dateien bis zu dieser Größe gehen in einer Anfrage hoch, größere in
         * Teilen dieser Größe (Nextcloud braucht ≥ 5 MB je Teil außer dem letzten).
         */
        const val CHUNK_SIZE: Long = 10L * 1024 * 1024
        private const val ATTEMPTS = 3
    }
}
