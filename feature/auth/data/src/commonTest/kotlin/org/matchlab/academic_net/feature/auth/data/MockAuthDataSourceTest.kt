package org.matchlab.academic_net.feature.auth.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.auth.data.mock.MockAuthDataSource
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Credentials
import org.matchlab.academic_net.feature.auth.domain.Password

private const val Letter = MockAuthDataSource.MockLetterToken

private fun credentials(email: String = "student@hse.ru", password: String = "hunter2-hunter2") =
    Credentials(email, Password(password))

@OptIn(ExperimentalCoroutinesApi::class)
class MockAuthDataSourceTest {
    private val time = TestTimeSource()
    private var tokens = 0
    private val source = MockAuthDataSource(
        latencyMillis = 0,
        timeSource = time,
        trustedDomains = setOf("hse.ru", "stanford.edu"),
        tokenGenerator = { "token-${++tokens}" },
    )

    /** Registers [email] and opens the link of the letter: the account exists afterwards. */
    private suspend fun createAccount(email: String = "student@hse.ru", password: String = "hunter2-hunter2") {
        assertEquals(RegisterReply.LetterSent, source.register(credentials(email, password)))
        assertEquals(ConfirmReply.Confirmed, source.confirmEmail(Letter))
    }

    // ---- the registry of domains ----

    @Test
    fun registeredDomainGetsALetter() = runTest {
        assertEquals(RegisterReply.LetterSent, source.register(credentials("student@hse.ru")))
        assertEquals(RegisterReply.LetterSent, source.register(credentials("i@stanford.edu")))
    }

    @Test
    fun subdomainOfRegisteredDomainIsTrusted() = runTest {
        assertEquals(RegisterReply.LetterSent, source.register(credentials("a@student.hse.ru")))
        assertEquals(RegisterReply.LetterSent, source.register(credentials("a@cs.student.hse.ru")))
    }

    @Test
    fun lookAlikeDomainIsNotTrusted() = runTest {
        assertEquals(RegisterReply.DomainNotAllowed, source.register(credentials("a@nothse.ru")))
        assertEquals(RegisterReply.DomainNotAllowed, source.register(credentials("a@hse.ru.evil.com")))
        assertEquals(RegisterReply.DomainNotAllowed, source.register(credentials("a@ru")))
    }

    @Test
    fun foreignDomainIsNotAllowedAndNoLetterIsSent() = runTest {
        assertEquals(RegisterReply.DomainNotAllowed, source.register(credentials("student@gmail.com")))

        // Nothing waits for a link, so the token of a letter finds nothing.
        assertEquals(ConfirmReply.InvalidLink, source.confirmEmail(Letter))
    }

    @Test
    fun addressWithoutOneAtIsNotAllowed() = runTest {
        for (address in listOf("hse.ru", "a@b@hse.ru", "student@", "@", "")) {
            assertEquals(RegisterReply.DomainNotAllowed, source.register(credentials(address)), "address '$address'")
        }
    }

    @Test
    fun addressIsNormalized() = runTest {
        assertEquals(RegisterReply.LetterSent, source.register(credentials("  Student@HSE.RU ")))
        source.confirmEmail(Letter)

        val reply = source.signIn(credentials("STUDENT@hse.ru"))

        assertEquals("student@hse.ru", assertIs<SignInReply.Success>(reply).session.email)
    }

    // ---- registration ----

    @Test
    fun registrationDoesNotCreateAnAccount() = runTest {
        source.register(credentials())

        assertEquals(SignInReply.Unauthorized, source.signIn(credentials()))
    }

    @Test
    fun addressWithAnAccountCannotRegisterAgain() = runTest {
        createAccount()

        assertEquals(RegisterReply.AlreadyRegistered, source.register(credentials()))
        val again = credentials("Student@HSE.ru", "another-password")
        assertEquals(RegisterReply.AlreadyRegistered, source.register(again))
    }

    @Test
    fun refusedRegistrationLeavesTheAccountAsItWas() = runTest {
        createAccount()
        source.register(credentials(password = "another-password"))

        assertIs<SignInReply.Success>(source.signIn(credentials()))
        assertEquals(SignInReply.Unauthorized, source.signIn(credentials(password = "another-password")))
    }

    @Test
    fun registeringAgainReplacesThePassword() = runTest {
        source.register(credentials(password = "first-password"))
        source.register(credentials(password = "second-password"))
        source.confirmEmail(Letter)

        assertEquals(SignInReply.Unauthorized, source.signIn(credentials(password = "first-password")))
        assertIs<SignInReply.Success>(source.signIn(credentials(password = "second-password")))
    }

    @Test
    fun registeringAgainRestartsTheLifetimeOfTheLink() = runTest {
        source.register(credentials())
        time += 20.minutes
        source.register(credentials())
        time += 20.minutes

        // Forty minutes after the first request, but twenty after the last one.
        assertEquals(ConfirmReply.Confirmed, source.confirmEmail(Letter))
    }

    // ---- the link of the letter ----

    @Test
    fun letterConfirmsTheRequestAndCreatesTheAccountWithItsPassword() = runTest {
        source.register(credentials(password = "chosen-password"))

        assertEquals(ConfirmReply.Confirmed, source.confirmEmail(Letter))

        assertIs<SignInReply.Success>(source.signIn(credentials(password = "chosen-password")))
        assertEquals(SignInReply.Unauthorized, source.signIn(credentials(password = "other-password")))
    }

    @Test
    fun linkWithTheTokenIsAcceptedLikeTheToken() = runTest {
        val texts = listOf(
            Letter,
            " $Letter\n",
            "https://matchlab.space/confirm?token=$Letter",
            "https://matchlab.space/confirm/$Letter",
            "https://matchlab.space/#/confirm?token=$Letter",
        )
        for (text in texts) {
            val fresh = MockAuthDataSource(latencyMillis = 0, timeSource = time, trustedDomains = setOf("hse.ru"))
            fresh.register(credentials())

            assertEquals(ConfirmReply.Confirmed, fresh.confirmEmail(text), "text '$text'")
        }
    }

    @Test
    fun theTokenOfTheLetterIsSingleUse() = runTest {
        createAccount()

        assertEquals(ConfirmReply.InvalidLink, source.confirmEmail(Letter))
    }

    @Test
    fun otherTokensAreUnknown() = runTest {
        source.register(credentials())

        assertEquals(ConfirmReply.InvalidLink, source.confirmEmail("654321"))
        assertEquals(ConfirmReply.InvalidLink, source.confirmEmail("https://matchlab.space/confirm?token=654321"))
        assertEquals(ConfirmReply.InvalidLink, source.confirmEmail("$Letter-0"))
        // The wrong tries did not spoil the request.
        assertEquals(ConfirmReply.Confirmed, source.confirmEmail(Letter))
    }

    @Test
    fun textWithoutATokenIsInvalidLink() = runTest {
        source.register(credentials())

        val texts = listOf("", "   ", "two words", "https://matchlab.space/", "https://matchlab.space/confirm?token=")
        for (text in texts) {
            assertEquals(ConfirmReply.InvalidLink, source.confirmEmail(text), "text '$text'")
        }
        assertEquals(ConfirmReply.Confirmed, source.confirmEmail(Letter))
    }

    @Test
    fun noRequestMeansNothingToConfirm() = runTest {
        assertEquals(ConfirmReply.InvalidLink, source.confirmEmail(Letter))
    }

    @Test
    fun linkLivesThirtyMinutes() = runTest {
        assertEquals(30.minutes, MockAuthDataSource.LinkLifetime)
        source.register(credentials())
        time += 30.minutes - 1.seconds

        assertEquals(ConfirmReply.Confirmed, source.confirmEmail(Letter))
    }

    @Test
    fun linkOlderThanThirtyMinutesIsExpired() = runTest {
        source.register(credentials())
        time += 30.minutes

        assertEquals(ConfirmReply.Expired, source.confirmEmail(Letter))
        // Still expired on the next try, and no account was made.
        time += 1.minutes
        assertEquals(ConfirmReply.Expired, source.confirmEmail(Letter))
        assertEquals(SignInReply.Unauthorized, source.signIn(credentials()))
    }

    @Test
    fun registeringAgainAfterTheLinkExpiredStartsOver() = runTest {
        source.register(credentials())
        time += 31.minutes
        source.confirmEmail(Letter)

        source.register(credentials())

        assertEquals(ConfirmReply.Confirmed, source.confirmEmail(Letter))
    }

    @Test
    fun letterBelongsToTheLastRequest() = runTest {
        source.register(credentials("first@hse.ru"))
        source.register(credentials("second@hse.ru"))

        assertEquals(ConfirmReply.Confirmed, source.confirmEmail(Letter))

        assertIs<SignInReply.Success>(source.signIn(credentials("second@hse.ru")))
        assertEquals(SignInReply.Unauthorized, source.signIn(credentials("first@hse.ru")))
        // The token is used up; the first address has to register again to get a link.
        assertEquals(ConfirmReply.InvalidLink, source.confirmEmail(Letter))
    }

    // ---- signing in ----

    @Test
    fun signInWithoutAnAccountIsUnauthorized() = runTest {
        assertEquals(SignInReply.Unauthorized, source.signIn(credentials()))
        assertEquals(SignInReply.Unauthorized, source.signIn(credentials("student@example.com")))
    }

    @Test
    fun wrongPasswordIsUnauthorized() = runTest {
        createAccount(password = "Hunter2-Hunter2")

        assertEquals(SignInReply.Unauthorized, source.signIn(credentials(password = "hunter2-hunter2")))
        assertEquals(SignInReply.Unauthorized, source.signIn(credentials(password = "Hunter2-Hunter2 ")))
        assertEquals(SignInReply.Unauthorized, source.signIn(credentials(password = "")))
    }

    @Test
    fun rightPasswordSignsInAsTheMockViewer() = runTest {
        createAccount()

        val success = assertIs<SignInReply.Success>(source.signIn(credentials()))

        assertEquals(UserId("user-me"), success.session.userId)
        assertEquals("student@hse.ru", success.session.email)
        assertEquals(AccessToken("token-1"), success.session.accessToken)
    }

    @Test
    fun everySignInGetsItsOwnToken() = runTest {
        createAccount()

        val first = assertIs<SignInReply.Success>(source.signIn(credentials()))
        val second = assertIs<SignInReply.Success>(source.signIn(credentials()))

        assertNotEquals(first.session.accessToken, second.session.accessToken)
    }

    @Test
    fun theMockNeverForbidsASignIn() = runTest {
        createAccount()

        for (attempt in listOf(credentials(), credentials(password = "wrong-password"), credentials("a@hse.ru"))) {
            assertNotEquals(SignInReply.Forbidden, source.signIn(attempt))
        }
    }

    // ---- the session ----

    @Test
    fun anyTokenThatIsNotBlankIsValidForTheViewer() = runTest {
        // The mock does not remember the tokens of an earlier launch.
        assertEquals(SessionCheck.Valid(UserId("user-me")), source.checkSession(AccessToken("from-an-earlier-launch")))
    }

    @Test
    fun blankTokenIsRejected() = runTest {
        assertEquals(SessionCheck.Rejected, source.checkSession(AccessToken("")))
        assertEquals(SessionCheck.Rejected, source.checkSession(AccessToken("   ")))
    }

    @Test
    fun revokedTokenIsRejectedAndOthersAreNot() = runTest {
        source.revoke(AccessToken("token-a"))

        assertEquals(SessionCheck.Rejected, source.checkSession(AccessToken("token-a")))
        assertEquals(SessionCheck.Valid(UserId("user-me")), source.checkSession(AccessToken("token-b")))
    }

    @Test
    fun revokingTwiceOrAnUnknownTokenIsHarmless() = runTest {
        source.revoke(AccessToken("token-a"))
        source.revoke(AccessToken("token-a"))
        source.revoke(AccessToken(""))

        assertEquals(SessionCheck.Rejected, source.checkSession(AccessToken("token-a")))
    }

    // ---- the defaults ----

    @Test
    fun defaultRegistryIsTheMockServers() = runTest {
        val default = MockAuthDataSource(latencyMillis = 0, timeSource = time)

        assertEquals(RegisterReply.LetterSent, default.register(credentials("a@student.msu.ru")))
        assertEquals(RegisterReply.LetterSent, default.register(credentials("a@gmail.com")))
        assertEquals(RegisterReply.DomainNotAllowed, default.register(credentials("a@example.com")))
    }

    @Test
    fun defaultLetterTokenIsOneTwoThreeFourFiveSix() = runTest {
        assertEquals("123456", MockAuthDataSource.MockLetterToken)
        val default = MockAuthDataSource(latencyMillis = 0, timeSource = time)
        default.register(credentials())

        assertEquals(ConfirmReply.Confirmed, default.confirmEmail("123456"))
    }

    @Test
    fun defaultTokensAreDifferentEveryTime() = runTest {
        val default = MockAuthDataSource(latencyMillis = 0, timeSource = time)
        default.register(credentials())
        default.confirmEmail(Letter)

        val first = assertIs<SignInReply.Success>(default.signIn(credentials()))
        val second = assertIs<SignInReply.Success>(default.signIn(credentials()))

        assertNotEquals(first.session.accessToken, second.session.accessToken)
    }

    @Test
    fun everyCallAnswersAfterTheLatency() = runTest {
        val slow = MockAuthDataSource(latencyMillis = 600, timeSource = time)

        slow.register(credentials())
        assertEquals(600, currentTime)
        slow.confirmEmail(Letter)
        assertEquals(1_200, currentTime)
        slow.signIn(credentials())
        assertEquals(1_800, currentTime)
        slow.checkSession(AccessToken("token"))
        assertEquals(2_400, currentTime)
        slow.revoke(AccessToken("token"))
        assertEquals(3_000, currentTime)
    }

    @Test
    fun textWithoutATokenIsNotWaitedFor() = runTest {
        val slow = MockAuthDataSource(latencyMillis = 600, timeSource = time)

        assertEquals(ConfirmReply.InvalidLink, slow.confirmEmail("two words"))

        assertEquals(0, currentTime)
    }
}
