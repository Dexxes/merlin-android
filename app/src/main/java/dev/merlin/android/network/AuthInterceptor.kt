package dev.merlin.android.network

import android.util.Base64
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

class AuthInterceptor @Inject constructor(
    private val credentialsProvider: CredentialsProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val username = credentialsProvider.username()
        val password = credentialsProvider.appPassword()
        val request = chain.request().newBuilder().apply {
            if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                val credential = "$username:$password"
                val encoded = Base64.encodeToString(credential.toByteArray(), Base64.NO_WRAP)
                addHeader("Authorization", "Basic $encoded")
            }
        }.build()
        return chain.proceed(request)
    }
}
