package org.matchlab.academic_net.feature.auth.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.gateway.AccountApi
import org.matchlab.academic_net.gateway.dto.ConfirmEmailRequestDto
import org.matchlab.academic_net.gateway.dto.CredentialsDto
import org.matchlab.academic_net.gateway.dto.LoginResultDto
import org.matchlab.academic_net.gateway.dto.RegistrationResultDto
import org.matchlab.academic_net.gateway.dto.UserIdDto

/** An [AccountApi] a test scripts; the calls are recorded, the `on…` lambdas answer or throw. */
private class FakeAccountApi : AccountApi {
    var onRegister: suspend (CredentialsDto) -> RegistrationResultDto = { RegistrationResultDto("pending") }
    var onConfirm: suspend (ConfirmEmailRequestDto) -> Unit = {}
    var onLogin: suspend (CredentialsDto) -> LoginResultDto = { LoginResultDto("user-uuid", "access-token") }
    var onCurrentSession: suspend (String) -> UserIdDto = { UserIdDto("user-uuid") }
    var onRevoke: suspend (String) -> Unit = {}

    val registered = mutableListOf<CredentialsDto>()
    val confirmed = mutableListOf<ConfirmEmailRequestDto>()
    val logins = mutableListOf<CredentialsDto>()
    val checked = mutableListOf<String>()
    val revoked = mutableListOf<String>()

    override suspend fun register(credentials: CredentialsDto): RegistrationResultDto {
        registered += credentials
        return onRegister(credentials)
    }

    override suspend fun confirmEmail(request: ConfirmEmailRequestDto) {
        confirmed += request
        onConfirm(request)
    }

    override suspend fun login(credentials: CredentialsDto): LoginResultDto {
        logins += credentials
        return onLogin(credentials)
    }

    override suspend fun currentSession(accessToken: String): UserIdDto {
        checked += accessToken
        return onCurrentSession(accessToken)
    }

    override suspend fun revokeSession(accessToken: String) {
        revoked += accessToken
        onRevoke(accessToken)
    }
}

private val Token = AccessToken("access-token")

/** The statuses of the calls with no word of their own: they are thrown, not turned into replies. */
private val UnexpectedStatuses = listOf(400, 401, 402, 404, 405, 408, 410, 422, 429, 500, 502, 503, 504)

/** The failures of the network: never turned into replies by any call. */
private fun transportFailures(): List<RemoteFailure> =
    listOf(RemoteFailure.NoConnection(), RemoteFailure.Timeout(), RemoteFailure.Malformed())

class GatewayAuthDataSourceTest {
    private val api = FakeAccountApi()
    private val source = GatewayAuthDataSource(api)

    private fun http(status: Int) = RemoteFailure.Http(status, "text of the server")

    // ---- register ----

    @Test
    fun registerSendsTheAddressAndThePasswordAsTheyAre() = runTest {
        source.register(Hunter)

        assertEquals(listOf(CredentialsDto("student@hse.ru", "hunter2-hunter2")), api.registered)
    }

    @Test
    fun created201IsLetterSent() = runTest {
        assertEquals(RegisterReply.LetterSent, source.register(Hunter))
    }

    @Test
    fun anyStatusTextOfRegistrationIsLetterSent() = runTest {
        // The spec knows only "pending"; a 201 is a letter on its way whatever the body says.
        api.onRegister = { RegistrationResultDto("") }

        assertEquals(RegisterReply.LetterSent, source.register(Hunter))
    }

    @Test
    fun register403IsDomainNotAllowed() = runTest {
        api.onRegister = { throw http(403) }

        assertEquals(RegisterReply.DomainNotAllowed, source.register(Hunter))
    }

    @Test
    fun register409IsAlreadyRegistered() = runTest {
        api.onRegister = { throw http(409) }

        assertEquals(RegisterReply.AlreadyRegistered, source.register(Hunter))
    }

    @Test
    fun registerThrowsEveryOtherStatus() = runTest {
        for (status in UnexpectedStatuses) {
            val failure = http(status)
            api.onRegister = { throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure.Http> { source.register(Hunter) })
        }
    }

    @Test
    fun registerThrowsTheFailuresOfTheNetwork() = runTest {
        for (failure in transportFailures()) {
            api.onRegister = { throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure> { source.register(Hunter) })
        }
    }

    @Test
    fun registerLetsCancellationAndForeignExceptionsThrough() = runTest {
        api.onRegister = { throw CancellationException("cancelled") }
        assertFailsWith<CancellationException> { source.register(Hunter) }

        val bug = IllegalStateException("bug")
        api.onRegister = { throw bug }
        assertSame(bug, assertFailsWith<IllegalStateException> { source.register(Hunter) })
    }

    // ---- confirmEmail ----

    @Test
    fun confirm204IsConfirmed() = runTest {
        assertEquals(ConfirmReply.Confirmed, source.confirmEmail("abc"))
        assertEquals(listOf(ConfirmEmailRequestDto("abc")), api.confirmed)
    }

    @Test
    fun confirm409IsAlreadyConfirmed() = runTest {
        api.onConfirm = { throw http(409) }

        assertEquals(ConfirmReply.AlreadyConfirmed, source.confirmEmail("abc"))
    }

    @Test
    fun confirm400And404AreInvalidLink() = runTest {
        for (status in listOf(400, 404)) {
            api.onConfirm = { throw http(status) }

            assertEquals(ConfirmReply.InvalidLink, source.confirmEmail("abc"), "status $status")
        }
    }

    @Test
    fun confirm410IsExpired() = runTest {
        api.onConfirm = { throw http(410) }

        assertEquals(ConfirmReply.Expired, source.confirmEmail("abc"))
    }

    @Test
    fun confirm403IsDomainNotAllowed() = runTest {
        api.onConfirm = { throw http(403) }

        assertEquals(ConfirmReply.DomainNotAllowed, source.confirmEmail("abc"))
    }

    @Test
    fun confirmThrowsEveryOtherStatus() = runTest {
        val others = UnexpectedStatuses - setOf(400, 404, 410)
        for (status in others) {
            val failure = http(status)
            api.onConfirm = { throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure.Http> { source.confirmEmail("abc") })
        }
    }

    @Test
    fun confirmThrowsTheFailuresOfTheNetwork() = runTest {
        for (failure in transportFailures()) {
            api.onConfirm = { throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure> { source.confirmEmail("abc") })
        }
    }

    @Test
    fun confirmLetsCancellationThrough() = runTest {
        api.onConfirm = { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { source.confirmEmail("abc") }
    }

    @Test
    fun textWithoutATokenIsInvalidLinkAndNothingIsSent() = runTest {
        val texts = listOf(
            "",
            "   ",
            "\n",
            "two words",
            "token abc",
            "https://matchlab.space/",
            "https://matchlab.space",
            "https://matchlab.space/confirm?token=",
            "https://matchlab.space/confirm?token=&other=1",
        )
        for (text in texts) {
            assertEquals(ConfirmReply.InvalidLink, source.confirmEmail(text), "text: '$text'")
        }

        assertEquals(emptyList(), api.confirmed)
    }

    @Test
    fun theTokenIsTakenFromTheLinkOrTheText() = runTest {
        val cases = mapOf(
            "abc" to "abc",
            "  abc \n" to "abc",
            "https://matchlab.space/confirm?token=abc" to "abc",
            "https://matchlab.space/confirm?a=1&TOKEN=abc&b=2" to "abc",
            "https://matchlab.space/#/confirm?token=abc" to "abc",
            "https://matchlab.space/confirm/abc" to "abc",
            "https://matchlab.space/confirm/abc/" to "abc",
            "https://matchlab.space/confirm?token=a%2Bb%2Fc" to "a+b/c",
            "https://matchlab.space/confirm?token=a+b" to "a+b",
        )
        for ((text, token) in cases) {
            api.confirmed.clear()

            source.confirmEmail(text)

            assertEquals(listOf(ConfirmEmailRequestDto(token)), api.confirmed, "text: '$text'")
        }
    }

    // ---- signIn ----

    @Test
    fun signInSendsTheAddressAndThePasswordAsTheyAre() = runTest {
        source.signIn(Hunter)

        assertEquals(listOf(CredentialsDto("student@hse.ru", "hunter2-hunter2")), api.logins)
    }

    @Test
    fun login200IsASessionOfTheUserAndTheAddress() = runTest {
        val reply = source.signIn(Hunter)

        assertEquals(
            SignInReply.Success(Session(UserId("user-uuid"), "student@hse.ru", AccessToken("access-token"))),
            reply,
        )
    }

    @Test
    fun login401IsUnauthorized() = runTest {
        api.onLogin = { throw http(401) }

        assertEquals(SignInReply.Unauthorized, source.signIn(Hunter))
    }

    @Test
    fun login403IsForbidden() = runTest {
        api.onLogin = { throw http(403) }

        assertEquals(SignInReply.Forbidden, source.signIn(Hunter))
    }

    @Test
    fun loginThrowsEveryOtherStatus() = runTest {
        for (status in UnexpectedStatuses - 401) {
            val failure = http(status)
            api.onLogin = { throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure.Http> { source.signIn(Hunter) })
        }
    }

    @Test
    fun loginThrowsTheFailuresOfTheNetwork() = runTest {
        for (failure in transportFailures()) {
            api.onLogin = { throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure> { source.signIn(Hunter) })
        }
    }

    @Test
    fun loginLetsCancellationThrough() = runTest {
        api.onLogin = { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { source.signIn(Hunter) }
    }

    @Test
    fun loginWithoutAUserOrATokenIsMalformed() = runTest {
        val answers = listOf(
            LoginResultDto("", "token"),
            LoginResultDto("  ", "token"),
            LoginResultDto("user", ""),
            LoginResultDto("user", "  "),
        )
        for (answer in answers) {
            api.onLogin = { answer }

            assertIs<RemoteFailure.Malformed>(assertFailsWith<RemoteFailure> { source.signIn(Hunter) })
        }
    }

    // ---- checkSession ----

    @Test
    fun checkAsksWithTheTokenOfTheSession() = runTest {
        source.checkSession(Token)

        assertEquals(listOf("access-token"), api.checked)
    }

    @Test
    fun session200IsValidForTheUserOfTheServer() = runTest {
        api.onCurrentSession = { UserIdDto("somebody-else") }

        assertEquals(SessionCheck.Valid(UserId("somebody-else")), source.checkSession(Token))
    }

    @Test
    fun session401And403AreRejected() = runTest {
        for (status in listOf(401, 403)) {
            api.onCurrentSession = { throw http(status) }

            assertEquals(SessionCheck.Rejected, source.checkSession(Token), "status $status")
        }
    }

    @Test
    fun sessionThrowsEveryOtherStatus() = runTest {
        for (status in UnexpectedStatuses - 401) {
            val failure = http(status)
            api.onCurrentSession = { throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure.Http> { source.checkSession(Token) })
        }
    }

    @Test
    fun sessionThrowsTheFailuresOfTheNetwork() = runTest {
        for (failure in transportFailures()) {
            api.onCurrentSession = { throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure> { source.checkSession(Token) })
        }
    }

    @Test
    fun sessionLetsCancellationThrough() = runTest {
        api.onCurrentSession = { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { source.checkSession(Token) }
    }

    @Test
    fun sessionWithoutAUserIsMalformed() = runTest {
        api.onCurrentSession = { UserIdDto("  ") }

        assertIs<RemoteFailure.Malformed>(assertFailsWith<RemoteFailure> { source.checkSession(Token) })
    }

    // ---- revoke ----

    @Test
    fun revokeEndsTheSessionOfTheToken() = runTest {
        source.revoke(Token)

        assertEquals(listOf("access-token"), api.revoked)
    }

    @Test
    fun revoke401IsSuccess() = runTest {
        api.onRevoke = { throw http(401) }

        source.revoke(Token)

        assertEquals(listOf("access-token"), api.revoked)
    }

    @Test
    fun revokeThrowsEveryOtherStatus() = runTest {
        for (status in UnexpectedStatuses - 401 + 403) {
            val failure = http(status)
            api.onRevoke = { throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure.Http> { source.revoke(Token) })
        }
    }

    @Test
    fun revokeThrowsTheFailuresOfTheNetwork() = runTest {
        for (failure in transportFailures()) {
            api.onRevoke = { throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure> { source.revoke(Token) })
        }
    }

    @Test
    fun revokeLetsCancellationThrough() = runTest {
        api.onRevoke = { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { source.revoke(Token) }
    }
}
