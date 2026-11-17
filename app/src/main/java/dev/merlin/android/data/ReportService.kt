package dev.merlin.android.data

import dev.merlin.android.R
import dev.merlin.android.network.MerlinApi
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Äquivalent zu `ReportService.swift`. Sendet Artikel-Meldungen ans
 * merlin-reports-Backend.
 *
 * Die Backend-URL wird aus den Merlin-Nextcloud-Settings gelesen (Schlüssel
 * `reportBackendUrl`, siehe additives Feld in [dev.merlin.android.network.Settings])
 * und nach dem ersten erfolgreichen Abruf gecacht; [invalidateCache] erzwingt
 * beim nächsten Aufruf einen erneuten Settings-Fetch.
 *
 * **Architekturentscheidung (abweichend vom App-Standard):** Der Hilt-weite
 * [OkHttpClient] (siehe `NetworkModule`) läuft durch `BaseUrlInterceptor`
 * (schreibt jede Anfrage auf den Nextcloud-Host um) und `AuthInterceptor`
 * (Nextcloud Basic Auth) – beides falsch für das externe, unauthentifizierte
 * merlin-reports-Backend. Deshalb hier ein eigener, schlanker [OkHttpClient]
 * ohne diese Interceptoren, als Äquivalent zu iOS' `URLSession.shared`.
 *
 * Mutex-isoliert als Kotlin-Äquivalent zu Swifts `actor`.
 */
@Singleton
class ReportService @Inject constructor(
    private val api: MerlinApi,
    private val json: Json,
) {
    private val mutex = Mutex()
    private var cachedBackendUrl: String? = null

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    // MARK: – API

    /**
     * Meldet einen Artikel mit optionalem Kommentar ans merlin-reports-Backend.
     * Wirft [ReportError], wenn die URL nicht konfiguriert ist oder der Server
     * non-2xx antwortet.
     */
    suspend fun report(url: String, comment: String = "") {
        val backendUrl = resolveBackendUrl()
        val endpoint = "$backendUrl?action=report".toHttpUrlOrNull()
            ?: throw ReportError.BackendUrlInvalid(backendUrl)

        val payload = json.encodeToString(ReportPayload.serializer(), ReportPayload(url = url, comment = comment))
        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ReportError.ServerError
            }
        }
    }

    /**
     * Leert den URL-Cache, damit beim nächsten [report]-Aufruf die Settings
     * erneut vom Server geladen werden (z. B. nach einer Einstellungsänderung).
     */
    suspend fun invalidateCache() = mutex.withLock {
        cachedBackendUrl = null
    }

    // MARK: – Private

    /**
     * Gibt die konfigurierte Backend-URL zurück. Beim ersten Aufruf (oder nach
     * [invalidateCache]) wird der Wert live aus den Nextcloud-Settings geladen
     * und danach gecacht.
     */
    private suspend fun resolveBackendUrl(): String = mutex.withLock {
        cachedBackendUrl?.takeIf { it.isNotEmpty() }?.let { return@withLock it }

        val url = api.getSettings().reportBackendUrl.trim()
        if (url.isEmpty()) throw ReportError.BackendUrlNotConfigured

        cachedBackendUrl = url
        url
    }

    @Serializable
    private data class ReportPayload(val url: String, val comment: String)

    // MARK: – Errors

    /** Meldung für Nutzer über [userMessage] (String-Ressourcen `report.error.*`). */
    sealed class ReportError(message: String, override val messageRes: Int) : Exception(message), LocalizedError {
        object BackendUrlNotConfigured : ReportError(
            "No report backend configured",
            R.string.report_error_backendNotConfigured,
        )

        data class BackendUrlInvalid(val url: String) : ReportError("Invalid backend URL: $url", R.string.report_error_backendURLInvalid) {
            override val messageArgs: Array<out Any> get() = arrayOf(url)
        }

        object ServerError : ReportError("Server rejected the report", R.string.report_error_serverRejected)
    }
}
