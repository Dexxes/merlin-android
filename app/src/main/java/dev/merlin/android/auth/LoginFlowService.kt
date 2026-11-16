package dev.merlin.android.auth

import dev.merlin.android.network.CredentialsStore
import dev.merlin.android.network.HttpsUrls
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Portierung von `LoginFlowService.swift`: Nextcloud Login Flow v2.
 *
 * Ablauf:
 * 1. [start] → POST `/index.php/login/v2`, liefert eine Login-URL, die die UI – wie
 *    iOS' `SafariLoginView` – in einer App-eigenen WebView öffnet
 *    ([dev.merlin.android.ui.components.LoginWebViewDialog]).
 * 2. [pollForCredentials] → pollt den Endpoint, bis der Nutzer den Login im Browser
 *    abgeschlossen hat; schreibt das Ergebnis in [CredentialsStore].
 *
 * Nutzt absichtlich einen eigenen, unauthentifizierten [OkHttpClient] – die normale
 * Retrofit/OkHttp-Instanz aus `NetworkModule` hängt [dev.merlin.android.network.BaseUrlInterceptor]
 * ein, der bereits eine konfigurierte Server-URL voraussetzt, die hier noch nicht existiert.
 */
@Singleton
class LoginFlowService @Inject constructor(
    private val credentialsStore: CredentialsStore,
) {
    sealed class LoginFlowException(message: String) : Exception(message) {
        data object InvalidServerUrl : LoginFlowException("Invalid server URL")
        data class ServerError(val msg: String) : LoginFlowException(msg)
        data object Timeout : LoginFlowException("Login timed out. Please try again.")
    }

    private companion object {
        const val USER_AGENT = "Merlin (Android)"
    }

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loginUrl = MutableStateFlow<String?>(null)
    val loginUrl: StateFlow<String?> = _loginUrl.asStateFlow()

    private var pollToken: String? = null
    private var pollEndpoint: String? = null

    @Serializable
    private data class InitResponse(val poll: Poll, val login: String) {
        @Serializable
        data class Poll(val token: String, val endpoint: String)
    }

    @Serializable
    private data class PollResult(val server: String, val loginName: String, val appPassword: String)

    /** Schritt 1: Login-Flow starten, liefert die im Browser zu öffnende URL. */
    suspend fun start(serverUrl: String): String = withContext(Dispatchers.IO) {
        _isLoading.value = true
        try {
            val base = normalizeBaseUrl(serverUrl)

            // merlin-server bildet Nextclouds Login-Flow-v2-JSON identisch nach
            // (siehe merlin-server/src/Controller/LoginFlowController.php) - nur
            // die Start-URL unterscheidet sich, Polling/Parsing bleibt unverändert.
            val path = if (credentialsStore.backendKind == CredentialsStore.BackendKind.STANDALONE) {
                "/login/v2"
            } else {
                "/index.php/login/v2"
            }
            val request = Request.Builder()
                .url("$base$path")
                .header("Accept", "application/json")
                // Nextcloud benennt das erzeugte App-Passwort und die Anzeige auf der
                // Login-Seite nach dem User-Agent – sonst stünde dort "okhttp/…".
                .header("User-Agent", USER_AGENT)
                .post(ByteArray(0).toRequestBody(null))
                .build()

            val response = httpClient.newCall(request).execute()
            response.use {
                if (!it.isSuccessful) {
                    throw LoginFlowException.ServerError("Server returned HTTP ${it.code}")
                }
                val body = it.body?.string().orEmpty()
                val decoded = json.decodeFromString<InitResponse>(body)
                // Wurde während des Requests abgebrochen, keine Login-URL mehr setzen –
                // sonst ginge die Login-Seite nach "Abbrechen" erneut auf.
                coroutineContext.ensureActive()

                // Hinter einem TLS-Proxy meldet der Server Poll-/Login-URL oft mit
                // http:// – Cleartext ist gesperrt, also auf https anheben.
                val loginUrl = HttpsUrls.upgrade(decoded.login)
                pollToken = decoded.poll.token
                pollEndpoint = HttpsUrls.upgrade(decoded.poll.endpoint)
                _loginUrl.value = loginUrl
                loginUrl
            }
        } finally {
            _isLoading.value = false
        }
    }

    /** Schritt 2: Pollt bis zu 5 Minuten lang, bis der Login im Browser abgeschlossen ist. */
    suspend fun pollForCredentials() = withContext(Dispatchers.IO) {
        val token = pollToken ?: throw LoginFlowException.ServerError("Login flow not started")
        val endpoint = pollEndpoint ?: throw LoginFlowException.ServerError("Login flow not started")

        val deadline = System.currentTimeMillis() + 5 * 60 * 1000L
        val intervalMillis = 5_000L

        // Login-Seite in jedem Fall schließen (Erfolg, Timeout, Fehler, Abbruch) –
        // sonst bliebe das Vollbild über einer Fehlermeldung stehen.
        try {
            while (System.currentTimeMillis() < deadline) {
                val result = attemptPoll(endpoint, token)
                if (result != null) {
                    credentialsStore.nextcloudUrl = HttpsUrls.upgrade(result.server)
                    credentialsStore.username = result.loginName
                    credentialsStore.appPassword = result.appPassword
                    return@withContext
                }
                delay(intervalMillis)
            }

            throw LoginFlowException.Timeout
        } finally {
            _loginUrl.value = null
        }
    }

    /** Gibt das Ergebnis zurück, `null` solange der Nutzer noch nicht fertig ist. */
    private fun attemptPoll(endpoint: String, token: String): PollResult? {
        val request = Request.Builder()
            .url(endpoint)
            .post("token=$token".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        response.use {
            // 404 = Nutzer noch nicht fertig – kein Fehler, nur weiter pollen.
            if (it.code == 404) return null
            if (!it.isSuccessful) {
                throw LoginFlowException.ServerError("Poll failed with HTTP ${it.code}")
            }
            val body = it.body?.string().orEmpty()
            return json.decodeFromString<PollResult>(body)
        }
    }

    /**
     * Entfernt Leerzeichen/abschließende Slashes und erzwingt `https://`: fehlt das Schema,
     * wird es ergänzt; `http://` oder ein doppeltes Präfix (z. B. "https://http://…" nach
     * Einfügen einer vollständigen URL in das vorbelegte Feld) wird zu genau einem
     * `https://` – Cleartext ist in der App nicht erlaubt.
     */
    private fun normalizeBaseUrl(serverUrl: String): String {
        var base = HttpsUrls.upgrade(serverUrl).trimEnd('/')
        if (base.isEmpty() || base.equals("https:", true) || base.equals("http:", true) || base.equals("https://", true)) {
            throw LoginFlowException.InvalidServerUrl
        }
        if (!base.startsWith("https://", ignoreCase = true)) {
            base = "https://$base"
        }
        if (base.toHttpUrlOrNull() == null) throw LoginFlowException.InvalidServerUrl
        return base
    }

    fun cancel() {
        pollToken = null
        pollEndpoint = null
        _loginUrl.value = null
    }
}
