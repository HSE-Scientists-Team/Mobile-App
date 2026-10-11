package org.matchlab.academic_net.feature.auth.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.auth.data.mock.MockAuthDataSource
import org.matchlab.academic_net.feature.auth.domain.CodeRequestOutcome
import org.matchlab.academic_net.feature.auth.domain.CodeVerificationOutcome

@OptIn(ExperimentalCoroutinesApi::class)
class MockAuthDataSourceTest {
    private val time = TestTimeSource()
    private val codes = ArrayDeque(listOf("111111", "222222", "333333", "444444", "555555", "666666", "777777"))
    private var tokens = 0
    private val source = MockAuthDataSource(
        latencyMillis = 0,
        timeSource = time,
        trustedDomains = setOf("hse.ru", "stanford.edu"),
        codeGenerator = { codes.removeFirst() },
        tokenGenerator = { "token-${++tokens}" },
    )

    private suspend fun requestAt(email: String = "student@hse.ru") = source.requestCode(email)

    @Test
    fun registeredDomainGetsACode() = runTest {
        val outcome = assertIs<CodeRequestOutcome.Sent>(requestAt("student@hse.ru"))

        assertEquals("student@hse.ru", outcome.challenge.email)
        assertEquals(60.seconds, outcome.challenge.resendAfter)
    }

    @Test
    fun subdomainOfRegisteredDomainIsTrusted() = runTest {
        assertIs<CodeRequestOutcome.Sent>(requestAt("a@student.hse.ru"))
    }

    @Test
    fun lookAlikeDomainIsNotTrusted() = runTest {
        assertEquals(CodeRequestOutcome.UntrustedDomain, requestAt("a@nothse.ru"))
        assertEquals(CodeRequestOutcome.UntrustedDomain, requestAt("a@hse.ru.evil.com"))
    }

    @Test
    fun foreignDomainIsUntrustedAndNothingIsSent() = runTest {
        assertEquals(CodeRequestOutcome.UntrustedDomain, requestAt("student@gmail.com"))

        // No code was generated, so there is nothing to verify.
        assertEquals(CodeVerificationOutcome.Expired, source.verifyCode("student@gmail.com", "111111"))
        assertEquals(7, codes.size)
    }

    @Test
    fun addressWithoutOneAtIsUntrusted() = runTest {
        assertEquals(CodeRequestOutcome.UntrustedDomain, requestAt("hse.ru"))
        assertEquals(CodeRequestOutcome.UntrustedDomain, requestAt("a@b@hse.ru"))
        assertEquals(CodeRequestOutcome.UntrustedDomain, requestAt("student@"))
    }

    @Test
    fun addressIsNormalized() = runTest {
        val outcome = assertIs<CodeRequestOutcome.Sent>(requestAt("  Student@HSE.RU "))
        assertEquals("student@hse.ru", outcome.challenge.email)

        val verified = source.verifyCode("STUDENT@hse.ru", "111111")
        assertEquals("student@hse.ru", assertIs<CodeVerificationOutcome.Verified>(verified).session.email)
    }

    @Test
    fun rightCodeSignsInAsTheMockViewer() = runTest {
        requestAt()

        val verified = assertIs<CodeVerificationOutcome.Verified>(source.verifyCode("student@hse.ru", "111111"))

        assertEquals(UserId("user-me"), verified.session.userId)
        assertEquals("token-1", verified.session.accessToken.value)
        assertTrue(source.isTokenActive(verified.session.accessToken))
    }

    @Test
    fun codeIsSingleUse() = runTest {
        requestAt()
        source.verifyCode("student@hse.ru", "111111")

        assertEquals(CodeVerificationOutcome.Expired, source.verifyCode("student@hse.ru", "111111"))
    }

    @Test
    fun codeWasNeverRequestedIsExpired() = runTest {
        assertEquals(CodeVerificationOutcome.Expired, source.verifyCode("student@hse.ru", "111111"))
    }

    @Test
    fun codeLivesTenMinutes() = runTest {
        requestAt()
        time += 10.minutes - 1.seconds
        val almost = source.verifyCode("student@hse.ru", "000000")
        assertIs<CodeVerificationOutcome.WrongCode>(almost)

        time += 1.seconds
        assertEquals(CodeVerificationOutcome.Expired, source.verifyCode("student@hse.ru", "111111"))
    }

    @Test
    fun codeIsStillGoodJustBeforeTheLimit() = runTest {
        requestAt()
        time += 10.minutes - 1.seconds

        assertIs<CodeVerificationOutcome.Verified>(source.verifyCode("student@hse.ru", "111111"))
    }

    @Test
    fun resendIsRefusedWithinASecondMinute() = runTest {
        requestAt()
        time += 20.seconds

        val outcome = assertIs<CodeRequestOutcome.TooManyRequests>(requestAt())

        assertEquals(40.seconds, outcome.retryAfter)
        // The refused request did not replace the code.
        assertIs<CodeVerificationOutcome.Verified>(source.verifyCode("student@hse.ru", "111111"))
    }

    @Test
    fun resendIsAllowedAfterAMinute() = runTest {
        requestAt()
        time += 60.seconds

        assertIs<CodeRequestOutcome.Sent>(requestAt())
    }

    @Test
    fun newCodeCancelsTheOldOne() = runTest {
        requestAt()
        time += 60.seconds
        requestAt()

        assertEquals(CodeVerificationOutcome.WrongCode(attemptsLeft = 4), source.verifyCode("student@hse.ru", "111111"))
        assertIs<CodeVerificationOutcome.Verified>(source.verifyCode("student@hse.ru", "222222"))
    }

    @Test
    fun newCodeResetsTheAttempts() = runTest {
        requestAt()
        repeat(3) { source.verifyCode("student@hse.ru", "000000") }
        time += 60.seconds
        requestAt()

        assertEquals(CodeVerificationOutcome.WrongCode(attemptsLeft = 4), source.verifyCode("student@hse.ru", "000000"))
    }

    @Test
    fun sixthEmailInAnHourIsRefusedUntilTheWindowMovesOn() = runTest {
        repeat(5) {
            assertIs<CodeRequestOutcome.Sent>(requestAt())
            time += 60.seconds
        }
        // Five e-mails over five minutes; the first one leaves the window in 55 minutes.
        val outcome = assertIs<CodeRequestOutcome.TooManyRequests>(requestAt())
        assertEquals(1.hours - 5.minutes, outcome.retryAfter)

        time += 1.hours - 5.minutes
        assertIs<CodeRequestOutcome.Sent>(requestAt())
    }

    @Test
    fun hourlyLimitWinsOverTheShorterResendWait() = runTest {
        repeat(4) {
            requestAt()
            time += 60.seconds
        }
        requestAt()
        time += 30.seconds
        // The resend wait would be 30 s, but five e-mails are already in the window.
        val outcome = assertIs<CodeRequestOutcome.TooManyRequests>(requestAt())

        assertEquals(1.hours - 4.minutes - 30.seconds, outcome.retryAfter)
    }

    @Test
    fun addressesAreLimitedSeparately() = runTest {
        requestAt("a@hse.ru")

        assertIs<CodeRequestOutcome.Sent>(requestAt("b@hse.ru"))
    }

    @Test
    fun wrongCodeCountsDownTheAttempts() = runTest {
        requestAt()

        assertEquals(CodeVerificationOutcome.WrongCode(4), source.verifyCode("student@hse.ru", "000000"))
        assertEquals(CodeVerificationOutcome.WrongCode(3), source.verifyCode("student@hse.ru", "000001"))
        assertEquals(CodeVerificationOutcome.WrongCode(2), source.verifyCode("student@hse.ru", "000002"))
        assertEquals(CodeVerificationOutcome.WrongCode(1), source.verifyCode("student@hse.ru", "000003"))
    }

    @Test
    fun fifthWrongCodeBurnsTheCode() = runTest {
        requestAt()
        repeat(4) { source.verifyCode("student@hse.ru", "000000") }

        assertEquals(CodeVerificationOutcome.TooManyAttempts, source.verifyCode("student@hse.ru", "000000"))
        // Even the right code does not work any more.
        assertEquals(CodeVerificationOutcome.Expired, source.verifyCode("student@hse.ru", "111111"))
    }

    @Test
    fun rightCodeAfterWrongOnesStillWorks() = runTest {
        requestAt()
        repeat(4) { source.verifyCode("student@hse.ru", "000000") }

        assertIs<CodeVerificationOutcome.Verified>(source.verifyCode("student@hse.ru", "111111"))
    }

    @Test
    fun everySignInGetsItsOwnToken() = runTest {
        requestAt()
        val first = assertIs<CodeVerificationOutcome.Verified>(source.verifyCode("student@hse.ru", "111111"))
        time += 60.seconds
        requestAt()
        val second = assertIs<CodeVerificationOutcome.Verified>(source.verifyCode("student@hse.ru", "222222"))

        assertNotEquals(first.session.accessToken, second.session.accessToken)
    }

    @Test
    fun revokeInvalidatesOnlyThatToken() = runTest {
        requestAt()
        val first = assertIs<CodeVerificationOutcome.Verified>(source.verifyCode("student@hse.ru", "111111"))
        time += 60.seconds
        requestAt()
        val second = assertIs<CodeVerificationOutcome.Verified>(source.verifyCode("student@hse.ru", "222222"))

        source.revoke(first.session.accessToken)

        assertFalse(source.isTokenActive(first.session.accessToken))
        assertTrue(source.isTokenActive(second.session.accessToken))
    }

    @Test
    fun defaultCodeIsOneTwoThreeFourFiveSix() = runTest {
        val default = MockAuthDataSource(latencyMillis = 0, timeSource = time)
        default.requestCode("student@hse.ru")

        assertIs<CodeVerificationOutcome.Verified>(default.verifyCode("student@hse.ru", "123456"))
    }

    @Test
    fun defaultRegistryIsTheMockServers() = runTest {
        val default = MockAuthDataSource(latencyMillis = 0, timeSource = time)

        assertIs<CodeRequestOutcome.Sent>(default.requestCode("a@student.msu.ru"))
        assertEquals(CodeRequestOutcome.UntrustedDomain, default.requestCode("a@gmail.com"))
    }

    @Test
    fun answersAfterTheLatency() = runTest {
        val slow = MockAuthDataSource(latencyMillis = 600, timeSource = time)

        slow.requestCode("student@hse.ru")

        assertEquals(600, currentTime)
    }
}
