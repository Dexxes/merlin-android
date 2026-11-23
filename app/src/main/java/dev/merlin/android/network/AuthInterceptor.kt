package dev.merlin.android.network

import android.util.Base64
import dev.merlin.android.BuildConfig
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

/**
 * HTTP-Basic-Auth-Interceptor (Nextcloud-Nutzername + App-Passwort) –
 * Äquivalent zur Basic-Auth-Behandlung in `MerlinAPI.swift`.
 *
 * [CredentialsProvider] wird mit der Credentials-Store-Portierung durch eine
 * Implementierung ersetzt, die Werte aus dem Android Keystore /
 * EncryptedSharedPreferences liest (siehe todo.md, Abschnitt 3).
 */
interface CredentialsProvider {
    fun username(): String?
    fun appPassword(): String?
}

/**
 * User-Agent aller Anfragen an den eigenen Merlin-Server (Nextcloud bzw. merlin-server), damit der
 * Server die Plattform erkennt – Gegenstück zu `MerlinApp-iOS/1.0` in `MerlinAPI.swift`. Ohne ihn
 * schickte OkHttp nur `okhttp/<version>`. Anfragen an fremde Seiten (Bilder, PDFs, Melde-Backend)
 * laufen über eigene Clients und bekommen ihn bewusst nicht.
 */
object MerlinUserAgent {
    const val PREFIX = "MerlinApp-Android"
    val value: String = "$PREFIX/${BuildConfig.VERSION_NAME}"
}

class AuthInterceptor @Inject constructor(
    private val credentialsProvider: CredentialsProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val username = credentialsProvider.username()
        val password = credentialsProvider.appPassword()
        val request = chain.request().newBuilder().apply {
            header("User-Agent", MerlinUserAgent.value)
            if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                val credential = "$username:$password"
                val encoded = Base64.encodeToString(credential.toByteArray(), Base64.NO_WRAP)
                addHeader("Authorization", "Basic $encoded")
            }
        }.build()
        return chain.proceed(request)
    }
}
