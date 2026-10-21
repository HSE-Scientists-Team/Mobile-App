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

    // ---- the password ----

    @Test
    fun passwordLimitsAreEightAndSeventyTwoBytes() {
        assertEquals(8, PasswordRules.MinBytes)
        assertEquals(72, PasswordRules.MaxBytes)
    }

    @Test
    fun emptyPasswordIsEmpty() {
        assertEquals(CredentialsError.PasswordEmpty, PasswordRules.validate(""))
    }

    @Test
    fun asciiPasswordIsCountedByCharacters() {
        assertEquals(CredentialsError.PasswordTooShort, PasswordRules.validate("a"))
        assertEquals(CredentialsError.PasswordTooShort, PasswordRules.validate("a".repeat(7)))
        assertNull(PasswordRules.validate("a".repeat(8)))
        assertNull(PasswordRules.validate("a".repeat(72)))
        assertEquals(CredentialsError.PasswordTooLong, PasswordRules.validate("a".repeat(73)))
        assertEquals(CredentialsError.PasswordTooLong, PasswordRules.validate("a".repeat(10_000)))
    }

    @Test
    fun passwordIsNeverTrimmed() {
        // Eight spaces are a password; the spaces at the ends count like any other character.
        assertNull(PasswordRules.validate(" ".repeat(8)))
        assertNull(PasswordRules.validate(" ".repeat(7) + "a"))
        assertNull(PasswordRules.validate("abcd    "))
        assertEquals(CredentialsError.PasswordTooShort, PasswordRules.validate("  abc  "))
        assertEquals(CredentialsError.PasswordTooLong, PasswordRules.validate(" " + "a".repeat(72)))
    }

    @Test
    fun cyrillicLetterIsTwoBytes() {
        // Three letters are six bytes, four letters are eight.
        assertEquals(CredentialsError.PasswordTooShort, PasswordRules.validate("пар"))
        assertNull(PasswordRules.validate("парк"))
        assertNull(PasswordRules.validate("пароль"))
        assertNull(PasswordRules.validate("я".repeat(36)))
        assertEquals(CredentialsError.PasswordTooLong, PasswordRules.validate("я".repeat(37)))
    }

    @Test
    fun cjkCharacterIsThreeBytes() {
        assertEquals(CredentialsError.PasswordTooShort, PasswordRules.validate("日本"))
        assertNull(PasswordRules.validate("日本語"))
        assertNull(PasswordRules.validate("語".repeat(24)))
        assertEquals(CredentialsError.PasswordTooLong, PasswordRules.validate("語".repeat(25)))
    }

    @Test
    fun emojiIsFourBytesAlthoughItIsTwoChars() {
        val lock = "\uD83D\uDD12"
        assertEquals(2, lock.length)
        assertEquals(CredentialsError.PasswordTooShort, PasswordRules.validate(lock))
        assertNull(PasswordRules.validate(lock.repeat(2)))
        assertNull(PasswordRules.validate(lock.repeat(18)))
        assertEquals(CredentialsError.PasswordTooLong, PasswordRules.validate(lock.repeat(19)))
    }

    @Test
    fun mixedScriptsAddUp() {
        // 3 ASCII + 2 Cyrillic (4) + 1 CJK (3) + 1 emoji (4) = 14 bytes.
        assertNull(PasswordRules.validate("abcпр語\uD83D\uDD12"))
        // 6 ASCII + 1 Cyrillic (2) = 8 bytes, 7 characters.
        assertNull(PasswordRules.validate("abcdefг"))
        // 5 ASCII + 1 Cyrillic (2) = 7 bytes.
        assertEquals(CredentialsError.PasswordTooShort, PasswordRules.validate("abcdeг"))
    }

    @Test
    fun loneSurrogateIsThreeBytes() {
        val high = "\uD83D"
        val low = "\uDD12"
        assertEquals(CredentialsError.PasswordTooShort, PasswordRules.validate(high))
        assertEquals(CredentialsError.PasswordTooShort, PasswordRules.validate(low))
        assertNull(PasswordRules.validate(high.repeat(3)))
        // A low surrogate before a high one is two lone surrogates, not a pair: 3 + 3 bytes.
        assertEquals(CredentialsError.PasswordTooShort, PasswordRules.validate(low + high))
        // A high surrogate followed by an ordinary character: 3 + 1 bytes.
        assertEquals(CredentialsError.PasswordTooShort, PasswordRules.validate(high + "abcd"))
        assertNull(PasswordRules.validate(high + "abcde"))
    }

    @Test
    fun noOtherRuleIsChecked() {
        assertNull(PasswordRules.validate("12345678"))
        assertNull(PasswordRules.validate("aaaaaaaa"))
        assertNull(PasswordRules.validate("password"))
    }
}
