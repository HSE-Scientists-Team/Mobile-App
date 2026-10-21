package org.matchlab.academic_net.feature.profile.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which link of a work the app opens (API-GAP G56): the `url` of a work is other people's data, so only an `http(s)` link
 * with a host is handed to the system, in its trimmed form; the others (`tel:`, `intent:`, `someapp://…`) never are.
 */
class ProfileLinksTest {
    // ---- there is nothing to open ----

    @Test
    fun noLinkIsNothingToOpen() {
        assertNull(openableLink(null))
    }

    @Test
    fun anEmptyOrBlankLinkIsNothingToOpen() {
        for (blank in listOf("", " ", "  ", "\t", "\n", " \t\n ")) {
            assertNull(openableLink(blank), "'${blank.map { it.code }}' is not a link")
        }
    }

    // ---- web links are opened ----

    @Test
    fun anHttpsLinkIsOpenedAsItIs() {
        assertEquals("https://doi.org/10.1234/cv", openableLink("https://doi.org/10.1234/cv"))
    }

    @Test
    fun anHttpLinkIsOpened() {
        assertEquals("http://example.org/page?q=1#top", openableLink("http://example.org/page?q=1#top"))
        assertEquals("http://localhost:8080/work", openableLink("http://localhost:8080/work"))
    }

    @Test
    fun theSpacesAroundALinkAreCutOffAndTheCutLinkIsWhatIsOpened() {
        assertEquals("https://x.org", openableLink(" https://x.org "))
        assertEquals("https://x.org/a", openableLink("\thttps://x.org/a\n"))
    }

    @Test
    fun theSchemeIsReadInAnyCase() {
        // The scheme is case-insensitive, and the text is opened as it was written.
        assertEquals("HTTPS://Example.com", openableLink("HTTPS://Example.com"))
        assertEquals("Http://example.com/x", openableLink("Http://example.com/x"))
    }

    // ---- everything else is never opened ----

    @Test
    fun linksOfOtherSchemesAreNeverOpened() {
        val others = listOf(
            "tel:+79001234567",
            "sms:+79001234567?body=hi",
            "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "someapp://x",
            "ftp://h",
            "ftp://files.example.org/work.pdf",
            "mailto:someone@example.org",
            "data:text/html,<script>alert(1)</script>",
            "content://com.android.contacts/contacts",
        )

        for (link in others) {
            assertNull(openableLink(link), "$link must not be opened")
        }
    }

    @Test
    fun aTextWithoutASchemeIsNotALink() {
        assertNull(openableLink("example.com"))
        assertNull(openableLink("www.example.com/path"))
        assertNull(openableLink("//example.com"))
    }

    @Test
    fun aSchemeThatIsNotAtTheStartDoesNotMakeALink() {
        assertNull(openableLink("javascript:alert(1)//https://x.org"))
        assertNull(openableLink("xhttps://x.org"))
        assertNull(openableLink("see https://x.org"))
    }

    @Test
    fun aLinkWithWhitespaceInsideIsNotOpened() {
        // Cutting the ends is not enough: what is left must be one word, or a second line could ride along.
        assertNull(openableLink("https://exa mple.org"))
        assertNull(openableLink("https://x.org/a b"))
        assertNull(openableLink("https://x.org\njavascript:alert(1)"))
    }

    @Test
    fun aWebSchemeWithoutAHostIsNotALink() {
        assertNull(openableLink("https://"))
        assertNull(openableLink("http://"))
        assertNull(openableLink("https:///path"))
        assertNull(openableLink("https:x.org"))
    }
}
