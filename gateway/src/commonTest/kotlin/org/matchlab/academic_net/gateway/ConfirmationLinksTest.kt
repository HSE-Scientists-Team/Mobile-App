package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.gateway.contract.ConfirmationLinks
import org.matchlab.academic_net.gateway.contract.UnverifiedContract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The guesses about the link in the letter: the `token` parameter, else the last segment, else the text itself. */
@OptIn(UnverifiedContract::class)
class ConfirmationLinksTest {
    private fun token(input: String): String? = ConfirmationLinks.extractToken(input)

    // ---- the token parameter ----

    @Test
    fun theTokenParameterOfALinkIsTheToken() {
        assertEquals("abc123", token("https://matchlab.space/confirm?token=abc123"))
    }

    @Test
    fun theTokenParameterWinsOverThePath() {
        assertEquals("abc123", token("https://matchlab.space/confirm/other?token=abc123"))
    }

    @Test
    fun otherParametersAroundItDoNotMatter() {
        assertEquals("abc123", token("https://matchlab.space/confirm?lang=ru&token=abc123&utm_source=mail"))
    }

    @Test
    fun theNameOfTheParameterIgnoresCase() {
        assertEquals("abc123", token("https://matchlab.space/confirm?Token=abc123"))
        assertEquals("abc123", token("https://matchlab.space/confirm?TOKEN=abc123"))
    }

    @Test
    fun aParameterThatIsNotNamedTokenIsNotTheToken() {
        assertEquals("confirm", token("https://matchlab.space/confirm?code=abc123"))
    }

    @Test
    fun theFirstTokenParameterWins() {
        assertEquals("first", token("https://matchlab.space/confirm?token=first&token=second"))
    }

    @Test
    fun paddingOfATokenSurvives() {
        assertEquals("YWJjZA==", token("https://matchlab.space/confirm?token=YWJjZA=="))
    }

    @Test
    fun escapesAreDecodedAsUtf8() {
        assertEquals("a b+c/d=", token("https://matchlab.space/confirm?token=a%20b%2Bc%2Fd%3D"))
        assertEquals("токен", token("https://matchlab.space/confirm?token=%D1%82%D0%BE%D0%BA%D0%B5%D0%BD"))
    }

    @Test
    fun aPlusIsNotASpace() {
        assertEquals("a+b", token("https://matchlab.space/confirm?token=a+b"))
    }

    @Test
    fun anEscapeThatIsNotOneStaysAsItIs() {
        assertEquals("100%", token("https://matchlab.space/confirm?token=100%"))
        assertEquals("a%zzb", token("https://matchlab.space/confirm?token=a%zzb"))
        assertEquals("a%4", token("https://matchlab.space/confirm?token=a%4"))
        assertEquals("a%4zb", token("https://matchlab.space/confirm?token=a%4zb"))
    }

    @Test
    fun theTokenMayBeInTheFragmentOfASinglePageApp() {
        assertEquals("abc123", token("https://matchlab.space/#/confirm?token=abc123"))
        assertEquals("abc123", token("https://matchlab.space/confirm#token=abc123"))
    }

    @Test
    fun theQueryWinsOverTheFragment() {
        assertEquals("fromquery", token("https://matchlab.space/confirm?token=fromquery#token=fromfragment"))
    }

    @Test
    fun anEmptyTokenParameterIsABrokenLinkNotAReasonToLookAtThePath() {
        assertNull(token("https://matchlab.space/confirm/abc123?token="))
        assertNull(token("https://matchlab.space/confirm?token=&lang=ru"))
    }

    @Test
    fun aQueryWithoutAValueIsNotAToken() {
        assertEquals("confirm", token("https://matchlab.space/confirm?token"))
    }

    @Test
    fun aLinkWithoutASchemeIsAlsoRead() {
        assertEquals("abc123", token("matchlab.space/confirm?token=abc123"))
        assertEquals("abc123", token("?token=abc123"))
        assertEquals("abc123", token("/confirm?token=abc123"))
    }

    @Test
    fun aLinkToTheApiItselfIsRead() {
        assertEquals("abc123", token("https://api.matchlab.space/api/v1/auth/email/confirm?token=abc123"))
    }

    // ---- the last segment of the path ----

    @Test
    fun theLastSegmentOfThePathIsTheToken() {
        assertEquals("abc123", token("https://matchlab.space/confirm/abc123"))
    }

    @Test
    fun aTrailingSlashDoesNotHideIt() {
        assertEquals("abc123", token("https://matchlab.space/confirm/abc123/"))
        assertEquals("abc123", token("https://matchlab.space/confirm/abc123//"))
    }

    @Test
    fun theHostIsNeverTheToken() {
        assertNull(token("https://matchlab.space"))
        assertNull(token("https://matchlab.space/"))
        assertNull(token("http://localhost:8080"))
        assertNull(token("https://matchlab.space/?lang=ru"))
        assertNull(token("https://matchlab.space/#top"))
    }

    @Test
    fun theSegmentIsDecodedToo() {
        assertEquals("a b", token("https://matchlab.space/confirm/a%20b"))
    }

    @Test
    fun aSegmentBeforeTheQueryIsUsedWhenThereIsNoTokenParameter() {
        assertEquals("abc123", token("https://matchlab.space/confirm/abc123?lang=ru"))
    }

    @Test
    fun aFragmentIsNotPartOfThePath() {
        assertEquals("abc123", token("https://matchlab.space/confirm/abc123#section"))
    }

    @Test
    fun aPortAndCredentialsInTheLinkAreNotTheToken() {
        assertEquals("abc123", token("https://user:pass@matchlab.space:8443/confirm/abc123"))
        assertNull(token("https://user:pass@matchlab.space:8443"))
    }

    // ---- the bare token ----

    @Test
    fun aTextThatIsNotALinkIsTheTokenItself() {
        assertEquals("abc123", token("abc123"))
        assertEquals("123456", token("123456"))
        assertEquals("ТОКЕН_ИЗ_ПИСЬМА", token("ТОКЕН_ИЗ_ПИСЬМА"))
        assertEquals("YWJjZA==", token("YWJjZA=="))
        assertEquals("a-b_c.d~e", token("a-b_c.d~e"))
    }

    @Test
    fun aBareTokenIsReturnedAsTyped() {
        assertEquals("a%20b", token("a%20b"))
        assertEquals("a+b", token("a+b"))
    }

    @Test
    fun whitespaceAroundTheTextIsDropped() {
        assertEquals("abc123", token("  abc123  "))
        assertEquals("abc123", token("\n\tabc123\r\n"))
        assertEquals("abc123", token("  https://matchlab.space/confirm?token=abc123 \n"))
    }

    // ---- garbage ----

    @Test
    fun blankTextHasNoToken() {
        assertNull(token(""))
        assertNull(token("   "))
        assertNull(token("\n\t"))
    }

    @Test
    fun textWithWhitespaceInsideIsNotAToken() {
        assertNull(token("hello world"))
        assertNull(token("Откройте ссылку из письма"))
        assertNull(token("abc 123"))
        assertNull(token("https://matchlab.space/confirm?token=abc 123"))
        assertNull(token("abc\n123"))
    }

    @Test
    fun controlCharactersMakeItGarbageToo() {
        assertNull(token("abc\u0000123"))
        assertNull(token("abc\u0007"))
    }

    @Test
    fun aSchemeAloneHasNoToken() {
        assertNull(token("https://"))
        assertNull(token("http://"))
        assertNull(token("//"))
        assertNull(token("/"))
        assertNull(token("?"))
        assertNull(token("#"))
        assertNull(token("?token="))
    }
}
