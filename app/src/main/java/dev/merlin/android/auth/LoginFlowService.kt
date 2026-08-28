package dev.merlin.android.auth

import dev.merlin.android.network.CredentialsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Portierung von `LoginFlowService.swift`: Nextcloud Login Flow v2.
 *
 * Ablauf:
 * 1. [start] → POST `/index.php/login/v2`, liefert eine Login-URL, die der Aufrufer
 *    (ViewModel/UI) in Custom Tabs öffnen muss (`androidx.browser`).
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
            val base = serverUrl.trim().trimEnd('/')
            if (base.isEmpty()) throw LoginFlowException.InvalidServerUrl

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
                .post(ByteArray(0).toRequestBody(null))
                .build()

            val response = httpClient.newCall(request).execute()
            response.use {
                if (!it.isSuccessful) {
                    throw LoginFlowException.ServerError("Server returned HTTP ${it.code}")
                }
                val body = it.body?.string().orEmpty()
                val decoded = json.decodeFromString<InitResponse>(body)

                pollToken = decoded.poll.token
                pollEndpoint = decoded.poll.endpoint
                _loginUrl.value = decoded.login
                decoded.login
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

        while (System.currentTimeMillis() < deadline) {
            val result = attemptPoll(endpoint, token)
            if (result != null) {
                credentialsStore.nextcloudUrl = result.server
                credentialsStore.username = result.loginName
                credentialsStore.appPassword = result.appPassword
                _loginUrl.value = null
                return@withContext
            }
            delay(intervalMillis)
        }

        throw LoginFlowException.Timeout
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

    fun cancel() {
        pollToken = null
        pollEndpoint = null
        _loginUrl.value = null
    }
}
