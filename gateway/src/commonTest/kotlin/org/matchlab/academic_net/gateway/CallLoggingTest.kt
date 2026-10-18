package org.matchlab.academic_net.gateway

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.ConfirmEmailRequestDto
import org.matchlab.academic_net.gateway.dto.UserIdDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The call log: off unless asked for, and never a body, a password or a token. */
class CallLoggingTest {
    private val secrets = listOf(
        Spec.Password,
        Spec.Email,
        Spec.AccessToken,
        Spec.LetterToken,
        "Bearer",
        "session-token",
        "Authorization",
    )

    private fun assertNoSecrets(lines: List<String>) {
        val text = lines.joinToString("\n")
        for (secret in secrets) {
            assertFalse(secret in text, "the log holds \"$secret\":\n$text")
        }
    }

    @Test
    fun nothingIsLoggedByDefault() = runTest {
        val fixture = GatewayFixture {
            json(Spec.LoginAnswer)
        }
        val session = fixture.gateway.openSession("session-token") { }

        fixture.account.login(Spec.credentials)
        session.requests.delete("probe")

        assertTrue(fixture.log.all().isEmpty())
    }

    @Test
    fun theDefaultConfigDoesNotLog() {
        assertFalse(GatewayConfig().logCalls)
    }

    @Test
    fun withLogCallsTheMethodUrlAndStatusOfACallAreLogged() = runTest {
        val fixture = GatewayFixture(GatewayConfig(logCalls = true)) { json(Spec.LoginAnswer) }

        fixture.account.login(Spec.credentials)

        val text = fixture.log.all().joinToString("\n")
        assertTrue("https://api.matchlab.space/api/v1/auth/login" in text, text)
        assertTrue("POST" in text, text)
        assertTrue("200" in text, text)
    }

    @Test
    fun aFailedStatusIsLoggedToo() = runTest {
        val fixture = GatewayFixture(GatewayConfig(logCalls = true)) {
            jsonError(HttpStatusCode.Unauthorized, "неверный email или пароль")
        }

        failureOf<RemoteFailure.Http> { fixture.account.login(Spec.credentials) }

        val text = fixture.log.all().joinToString("\n")
        assertTrue("401" in text, text)
        assertFalse("неверный" in text, "the text of an error is a body: $text")
    }

    @Test
    fun authCallsLogNothingFromTheirBodies() = runTest {
        val fixture = GatewayFixture(GatewayConfig(logCalls = true)) { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/register" -> json(Spec.RegistrationAnswer, HttpStatusCode.Created)
                "/api/v1/auth/email/confirm" -> noContent()
                "/api/v1/auth/login" -> json(Spec.LoginAnswer)
                else -> json(Spec.UserIdAnswer)
            }
        }

        fixture.account.register(Spec.credentials)
        fixture.account.confirmEmail(ConfirmEmailRequestDto(Spec.LetterToken))
        fixture.account.login(Spec.credentials)
        fixture.account.currentSession("session-token")
        fixture.account.revokeSession("session-token")

        val lines = fixture.log.all()
        assertTrue(lines.isNotEmpty())
        assertNoSecrets(lines)
        assertFalse("pending" in lines.joinToString("\n"), "the answer of register is a body")
        assertFalse(Spec.UserId in lines.joinToString("\n"), "the answer of login is a body")
    }

    @Test
    fun theTokenOfASessionIsNeverLogged() = runTest {
        val fixture = GatewayFixture(GatewayConfig(logCalls = true)) { json(Spec.UserIdAnswer) }
        val session = fixture.gateway.openSession("session-token") { }

        session.requests.get<UserIdDto>("users/me/profile")
        session.requests.delete("users/me/achievements/a1")

        val lines = fixture.log.all()
        assertTrue(lines.isNotEmpty())
        assertNoSecrets(lines)
        assertTrue(lines.joinToString("\n").contains("/api/v1/users/me/profile"))
    }

    @Test
    fun aCallIsLoggedOncePerRequestAndOncePerResponseWhetherOrNotItBelongsToASession() = runTest {
        val fixture = GatewayFixture(GatewayConfig(logCalls = true)) { json(Spec.UserIdAnswer) }
        val session = fixture.gateway.openSession("session-token") { }

        fixture.account.currentSession("session-token")
        session.requests.get<UserIdDto>("users/me/profile")

        val text = fixture.log.all().joinToString("\n")
        assertEquals(2, Regex("REQUEST: ").findAll(text).count(), text)
        assertEquals(2, Regex("RESPONSE: ").findAll(text).count(), text)
    }

    @Test
    fun aBrokenLoginAnswerDoesNotLeakTheTokenThroughTheFailureLog() = runTest {
        val fixture = GatewayFixture(GatewayConfig(logCalls = true)) {
            json("""{"user_id":"u1","access_token":"${Spec.AccessToken}",,}""")
        }

        val failure = failureOf<RemoteFailure.Malformed> { fixture.account.login(Spec.credentials) }

        assertNoSecrets(fixture.log.all())
        assertFalse(Spec.AccessToken in failure.stackTraceToString())
    }

    @Test
    fun aFailedConnectionIsLoggedWithoutTheBody() = runTest {
        val fixture = GatewayFixture(GatewayConfig(logCalls = true)) { throw IOException("connection reset") }

        failureOf<RemoteFailure.NoConnection> { fixture.account.login(Spec.credentials) }

        val text = fixture.log.all().joinToString("\n")
        assertTrue("/api/v1/auth/login" in text, text)
        assertNoSecrets(listOf(text))
    }

    @Test
    fun theLogOfOneGatewayIsNotSharedWithAnother() = runTest {
        val quiet = GatewayFixture { json(Spec.LoginAnswer) }
        val loud = GatewayFixture(GatewayConfig(logCalls = true)) { json(Spec.LoginAnswer) }

        quiet.account.login(Spec.credentials)
        loud.account.login(Spec.credentials)

        assertEquals(0, quiet.log.all().size)
        assertTrue(loud.log.all().isNotEmpty())
    }
}
