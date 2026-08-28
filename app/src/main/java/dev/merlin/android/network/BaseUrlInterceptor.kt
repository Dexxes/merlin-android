package dev.merlin.android.network

import javax.inject.Inject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Schreibt Scheme/Host/Port/Pfad-Präfix jeder Anfrage auf die zur Laufzeit in
 * [CredentialsStore] hinterlegte Nextcloud-Server-URL um.
 *
 * Hintergrund: Retrofit verlangt eine statische `baseUrl` zum Zeitpunkt der
 * Konstruktion (siehe [NetworkModule]), die Server-URL ist bei uns aber erst nach
 * dem Login/Onboarding bekannt und kann sich ändern. Deshalb bleibt die in
 * Retrofit konfigurierte `baseUrl` ein reiner Platzhalter, der nie tatsächlich
 * angefragt wird – dieser Interceptor ersetzt ihn vor jedem Request.
 */
class BaseUrlInterceptor @Inject constructor(
    private val credentialsStore: CredentialsStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val configuredBase = credentialsStore.nextcloudUrl
            .takeIf { it.isNotEmpty() }
            ?.toHttpUrlOrNull()
            ?: return chain.proceed(original)

        // Scheme/Host/Port werden ersetzt; ein eventueller Unterpfad der
        // Nextcloud-Installation (z. B. "/nextcloud") wird dem Platzhalter-Pfad
        // vorangestellt, Query/restlicher Pfad bleiben erhalten.
        //
        // merlin-server hängt die API direkt unter /api statt unter Nextclouds
        // App-Routing-Präfix /index.php/apps/merlin/api (das über
        // PLACEHOLDER_BASE_URL in jedem original.url.encodedPath steckt) -
        // Pfade selbst sind identisch (siehe merlin-server/public/index.php),
        // daher genügt es, das Präfix bei Standalone-Backends abzuschneiden.
        // Settings-Sync, Public-Share und TTS existieren inzwischen unter
        // identischem Pfad-Suffix auf beiden Backends, brauchen also keinen
        // Kurzschluss mehr (siehe merlin-server: UserSettingsController,
        // ShareController, TtsController).
        val isStandalone = credentialsStore.backendKind == CredentialsStore.BackendKind.STANDALONE
        val originalPath = if (isStandalone) {
            original.url.encodedPath.removePrefix("/index.php/apps/merlin")
        } else {
            original.url.encodedPath
        }

        val subPath = configuredBase.encodedPath.trimEnd('/')
        val newUrl = original.url.newBuilder()
            .scheme(configuredBase.scheme)
            .host(configuredBase.host)
            .port(configuredBase.port)
            .encodedPath(subPath + originalPath)
            .build()

        return chain.proceed(original.newBuilder().url(newUrl).build())
    }
}
