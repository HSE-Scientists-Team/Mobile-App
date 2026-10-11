package org.matchlab.academic_net.feature.auth.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthRulesTest {
    @Test
    fun normalizeTrimsAndLowerCases() {
        assertEquals("student@hse.ru", EmailRules.normalize("  Student@HSE.ru \n"))
    }

    @Test
    fun emptyAddressIsEmpty() {
        assertEquals(EmailError.Empty, EmailRules.validate(EmailRules.normalize("   ")))
        assertEquals(EmailError.Empty, EmailRules.validate(""))
    }

    @Test
    fun wellFormedAddressesPass() {
        assertNull(EmailRules.validate("student@hse.ru"))
        assertNull(EmailRules.validate("a.b+c@student.hse.ru"))
        assertNull(EmailRules.validate("i@stanford.edu"))
        assertNull(EmailRules.validate("иван@университет.рф"))
        assertNull(EmailRules.validate("x@a-b.co"))
    }

    @Test
    fun addressWithoutAtIsMalformed() {
        assertEquals(EmailError.Malformed, EmailRules.validate("student.hse.ru"))
    }

    @Test
    fun addressWithTwoAtsIsMalformed() {
        assertEquals(EmailError.Malformed, EmailRules.validate("a@b@hse.ru"))
    }

    @Test
    fun addressWithoutLocalPartOrDomainIsMalformed() {
        assertEquals(EmailError.Malformed, EmailRules.validate("@hse.ru"))
        assertEquals(EmailError.Malformed, EmailRules.validate("student@"))
    }

    @Test
    fun domainWithoutTopLevelPartIsMalformed() {
        assertEquals(EmailError.Malformed, EmailRules.validate("student@hse"))
        assertEquals(EmailError.Malformed, EmailRules.validate("student@hse."))
        assertEquals(EmailError.Malformed, EmailRules.validate("student@.ru"))
        assertEquals(EmailError.Malformed, EmailRules.validate("student@hse.r"))
    }

    @Test
    fun domainWithBadLabelsIsMalformed() {
        assertEquals(EmailError.Malformed, EmailRules.validate("student@hse..ru"))
        assertEquals(EmailError.Malformed, EmailRules.validate("student@-hse.ru"))
        assertEquals(EmailError.Malformed, EmailRules.validate("student@hse-.ru"))
        assertEquals(EmailError.Malformed, EmailRules.validate("student@hs_e.ru"))
    }

    @Test
    fun addressWithSpacesIsMalformed() {
        assertEquals(EmailError.Malformed, EmailRules.validate("stu dent@hse.ru"))
        assertEquals(EmailError.Malformed, EmailRules.validate("student@hse .ru"))
    }

    @Test
    fun tooLongAddressIsMalformed() {
        assertEquals(EmailError.Malformed, EmailRules.validate("a".repeat(250) + "@hse.ru"))
    }

    @Test
    fun sanitizeKeepsDigitsOnly() {
        assertEquals("123456", VerificationCodeRules.sanitize("12 34-56"))
        assertEquals("", VerificationCodeRules.sanitize("abc"))
    }

    @Test
    fun sanitizeCutsToCodeLength() {
        assertEquals("123456", VerificationCodeRules.sanitize("1234567890"))
    }

    @Test
    fun sanitizeDropsNonAsciiDigits() {
        assertEquals("12", VerificationCodeRules.sanitize("1٢2"))
    }

    @Test
    fun onlyAFullCodeIsComplete() {
        assertNull(VerificationCodeRules.validate("123456"))
        assertNull(VerificationCodeRules.validate("12 34 56"))
        assertEquals(CodeError.Incomplete, VerificationCodeRules.validate("12345"))
        assertEquals(CodeError.Incomplete, VerificationCodeRules.validate(""))
    }
}
