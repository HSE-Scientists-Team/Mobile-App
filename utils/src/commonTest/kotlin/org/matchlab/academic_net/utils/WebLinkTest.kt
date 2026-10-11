package org.matchlab.academic_net.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebLinkTest {
    @Test
    fun httpAndHttpsLinksWithAHostAreWebLinks() {
        assertTrue("https://doi.org/10.1234/cv".isWebLink())
        assertTrue("http://example.com".isWebLink())
        assertTrue("https://github.com/user/repo?tab=readme#top".isWebLink())
    }

    @Test
    fun schemeIsCaseInsensitive() {
        assertTrue("HTTPS://Example.COM/Path".isWebLink())
        assertTrue("Http://example.com".isWebLink())
    }

    @Test
    fun portAndLocalhostAreAllowed() {
        assertTrue("http://localhost:8080/x".isWebLink())
        assertTrue("https://127.0.0.1:443".isWebLink())
    }

    @Test
    fun credentialsAreSkippedWhenLookingForTheHost() {
        assertTrue("https://user:secret@example.com/x".isWebLink())
        assertFalse("https://user:secret@/x".isWebLink())
    }

    @Test
    fun hostMayStartTheQueryOrFragmentRightAway() {
        assertTrue("https://example.com?a=1".isWebLink())
        assertTrue("https://example.com#top".isWebLink())
    }

    @Test
    fun internationalHostsAreAllowed() {
        assertTrue("https://университет.рф/работа".isWebLink())
    }

    @Test
    fun otherSchemesAreNotWebLinks() {
        assertFalse("ftp://example.com".isWebLink())
        assertFalse("javascript:alert(1)".isWebLink())
        assertFalse("mailto:a@example.com".isWebLink())
    }

    @Test
    fun textWithoutASchemeIsNotAWebLink() {
        assertFalse("example.com".isWebLink())
        assertFalse("www.example.com".isWebLink())
        assertFalse("//example.com".isWebLink())
        assertFalse("doi.org/10.1234".isWebLink())
    }

    @Test
    fun emptyOrBlankIsNotAWebLink() {
        assertFalse("".isWebLink())
        assertFalse("   ".isWebLink())
    }

    @Test
    fun schemeWithoutAHostIsNotAWebLink() {
        assertFalse("https://".isWebLink())
        assertFalse("https:///path".isWebLink())
        assertFalse("http://?q=1".isWebLink())
        assertFalse("https://:8080".isWebLink())
    }

    @Test
    fun malformedHostIsNotAWebLink() {
        assertFalse("https://.com".isWebLink())
        assertFalse("https://example..com".isWebLink())
        assertFalse("https://example.com.".isWebLink())
        assertFalse("https://-example.com".isWebLink())
        assertFalse("https://exa_mple.com".isWebLink())
    }

    @Test
    fun malformedPortIsNotAWebLink() {
        assertFalse("https://example.com:".isWebLink())
        assertFalse("https://example.com:80a".isWebLink())
    }

    @Test
    fun whitespaceAnywhereIsNotAWebLink() {
        assertFalse(" https://example.com".isWebLink())
        assertFalse("https://example.com ".isWebLink())
        assertFalse("https://exa mple.com".isWebLink())
        assertFalse("https://example.com/a b".isWebLink())
    }
}
