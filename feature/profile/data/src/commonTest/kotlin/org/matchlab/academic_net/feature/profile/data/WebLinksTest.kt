package org.matchlab.academic_net.feature.profile.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.matchlab.academic_net.feature.profile.data.mock.linkSource
import org.matchlab.academic_net.feature.profile.data.mock.webLinkHost

class WebLinksTest {
    @Test
    fun hostIsLowercaseWithoutWww() {
        assertEquals("example.com", webLinkHost("https://WWW.Example.com/Path"))
        assertEquals("doi.org", webLinkHost("http://doi.org"))
    }

    @Test
    fun hostSkipsCredentialsPortQueryAndFragment() {
        assertEquals("example.com", webLinkHost("https://user:pw@example.com:8080/x?q=1#top"))
        assertEquals("example.com", webLinkHost("https://example.com?q=1"))
        assertEquals("example.com", webLinkHost("https://example.com#top"))
    }

    @Test
    fun notAWebLinkHasNoHost() {
        assertNull(webLinkHost("example.com"))
        assertNull(webLinkHost("ftp://example.com"))
        assertNull(webLinkHost("https://"))
        assertNull(webLinkHost(""))
    }

    @Test
    fun doiLinkIsLabelledWithItsIdentifier() {
        assertEquals("DOI 10.1234/cv", linkSource("https://doi.org/10.1234/cv"))
        assertEquals("DOI 10.1234/cv", linkSource("https://dx.doi.org/10.1234/cv?x=1#top"))
    }

    @Test
    fun doiLinkWithoutIdentifierIsJustDoi() {
        assertEquals("DOI", linkSource("https://doi.org"))
        assertEquals("DOI", linkSource("https://doi.org/"))
    }

    @Test
    fun githubIsLabelledGitHub() {
        assertEquals("GitHub", linkSource("https://github.com/user/repo"))
        assertEquals("GitHub", linkSource("https://www.github.com/user/repo"))
    }

    @Test
    fun arxivIsLabelledArXiv() {
        assertEquals("arXiv", linkSource("https://arxiv.org/abs/2403.01234"))
    }

    @Test
    fun otherSitesAreLabelledWithTheirDomain() {
        assertEquals("gitlab.com", linkSource("https://gitlab.com/user/repo"))
        assertEquals("drive.example.com", linkSource("https://drive.example.com/a"))
    }

    @Test
    fun noLinkOrABrokenLinkHasNoSource() {
        assertNull(linkSource(null))
        assertNull(linkSource("not a link"))
    }
}
