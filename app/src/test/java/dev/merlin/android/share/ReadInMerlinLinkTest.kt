package dev.merlin.android.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadInMerlinLinkTest {
    @Test fun plainUrl() =
        assertEquals("https://example.com/a?b=1&c=2#x", ReadInMerlinLink.firstWebUrl("https://example.com/a?b=1&c=2#x"))

    @Test fun urlInsideText() =
        assertEquals("https://example.com/artikel", ReadInMerlinLink.firstWebUrl("Schau mal: https://example.com/artikel. Spannend!"))

    @Test fun keepsBalancedParenthesis() =
        assertEquals(
            "https://de.wikipedia.org/wiki/Merlin_(Zauberer)",
            ReadInMerlinLink.firstWebUrl("(siehe https://de.wikipedia.org/wiki/Merlin_(Zauberer))"),
        )

    @Test fun firstTextWithUrlWins() =
        assertEquals("http://example.org/x", ReadInMerlinLink.firstWebUrl(null, "nur Text", "http://example.org/x"))

    @Test fun noUrl() {
        assertNull(ReadInMerlinLink.firstWebUrl("kein Link hier"))
        assertNull(ReadInMerlinLink.firstWebUrl("https://"))
        assertNull(ReadInMerlinLink.firstWebUrl(null, ""))
    }
}
