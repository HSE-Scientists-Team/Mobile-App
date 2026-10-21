package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.gateway.contract.LetterLinks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The link of the confirmation letter as the backend builds it (`verification_url` plus `?token=`). */
class LetterLinksTest {
    private val token = "pV1TQj5pu9TGwjQfh2T_THQyYN-Cf_x9lq5uNgX3OwQ"

    @Test
    fun theLinkOfTheLetterGivesItsToken() {
        assertEquals(token, LetterLinks.tokenOf("https://matchlab.space/verify-email?token=$token"))
    }

    @Test
    fun theUnderscoreAndTheDashOfBase64UrlStay() {
        assertEquals("a_b-c", LetterLinks.tokenOf("https://matchlab.space/verify-email?token=a_b-c"))
    }

    @Test
    fun theHostAndTheSchemeAreReadWithoutRegardToCaseAndTheOuterSpacesAreCut() {
        assertEquals(token, LetterLinks.tokenOf("  HTTPS://MatchLab.Space/verify-email?token=$token\n"))
    }

    @Test
    fun aTrailingSlashAndOtherParametersDoNotMatter() {
        assertEquals(token, LetterLinks.tokenOf("https://matchlab.space/verify-email/?utm=mail&token=$token"))
    }

    @Test
    fun anotherHostIsNotALinkOfTheLetter() {
        for (host in listOf("evil.example", "matchlab.space.evil.example", "api.matchlab.space", "www.matchlab.space", "matchlab.space:8443", "user@matchlab.space")) {
            assertNull(LetterLinks.tokenOf("https://$host/verify-email?token=$token"), host)
        }
    }

    @Test
    fun anotherSchemeIsNotALinkOfTheLetter() {
        for (scheme in listOf("http", "ftp", "matchlab", "javascript")) {
            assertNull(LetterLinks.tokenOf("$scheme://matchlab.space/verify-email?token=$token"), scheme)
        }
    }

    @Test
    fun anotherPathIsNotALinkOfTheLetter() {
        for (path in listOf("", "/", "/verify", "/verify-email-now", "/a/verify-email", "/confirm")) {
            assertNull(LetterLinks.tokenOf("https://matchlab.space$path?token=$token"), path)
        }
    }

    @Test
    fun aLinkWithoutATokenOrWithAnEmptyOneGivesNone() {
        assertNull(LetterLinks.tokenOf("https://matchlab.space/verify-email"))
        assertNull(LetterLinks.tokenOf("https://matchlab.space/verify-email?"))
        assertNull(LetterLinks.tokenOf("https://matchlab.space/verify-email?token="))
        assertNull(LetterLinks.tokenOf("https://matchlab.space/verify-email?other=1"))
    }

    @Test
    fun aFragmentMakesItNotALinkOfTheLetter() {
        assertNull(LetterLinks.tokenOf("https://matchlab.space/verify-email?token=$token#x"))
    }

    @Test
    fun textThatIsNotALinkGivesNone() {
        for (text in listOf("", " ", token, "matchlab.space/verify-email?token=$token", "https://")) {
            assertNull(LetterLinks.tokenOf(text), text)
        }
    }
}
