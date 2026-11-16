package dev.merlin.android.network

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpsUrlsTest {
    @Test fun upgradesHttp() =
        assertEquals("https://merlin.dexxes.de/login/v2/poll", HttpsUrls.upgrade("http://merlin.dexxes.de/login/v2/poll"))

    @Test fun keepsHttps() =
        assertEquals("https://merlin.dexxes.de", HttpsUrls.upgrade("https://merlin.dexxes.de"))

    @Test fun collapsesDoublePrefix() {
        assertEquals("https://merlin.dexxes.de", HttpsUrls.upgrade("https://http://merlin.dexxes.de"))
        assertEquals("https://merlin.dexxes.de", HttpsUrls.upgrade("https://https://merlin.dexxes.de"))
        assertEquals("https://merlin.dexxes.de", HttpsUrls.upgrade(" HTTP://merlin.dexxes.de "))
    }

    @Test fun leavesSchemelessAndEmptyAlone() {
        assertEquals("merlin.dexxes.de", HttpsUrls.upgrade("merlin.dexxes.de"))
        assertEquals("httpserver.de", HttpsUrls.upgrade("httpserver.de"))
        assertEquals("", HttpsUrls.upgrade(""))
        assertEquals("https://", HttpsUrls.upgrade("https://"))
    }
}
