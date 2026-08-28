package dev.merlin.android.data

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Setzt den `Referer`-Header auf das Origin der angefragten Bild-URL selbst
 * (`scheme://host/`) – Äquivalent zum Referer-Handling in
 * `ImageCacheService.swift` (Zeile ~198), das damit Hotlink-Schutz mancher
 * CDNs umgeht (z. B. Gumlet), die nur Anfragen von der eigenen Domain erlauben.
 */
class RefererInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val referer = "${original.url.scheme}://${original.url.host}/"
        val withReferer = original.newBuilder()
            .header("Referer", referer)
            .build()
        return chain.proceed(withReferer)
    }
}
