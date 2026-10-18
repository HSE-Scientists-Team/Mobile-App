package org.matchlab.academic_net.gateway

import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.ConfirmEmailRequestDto
import org.matchlab.academic_net.gateway.dto.CredentialsDto
import org.matchlab.academic_net.gateway.dto.LoginResultDto
import org.matchlab.academic_net.gateway.dto.RegistrationResultDto
import org.matchlab.academic_net.gateway.dto.UserIdDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** What each call of [AccountApi] sends and how it reads the answer, with the examples of the spec. */
class AccountApiTest {
    private fun accountFixture(config: GatewayConfig = GatewayConfig()) = GatewayFixture(config) { request ->
        when (request.url.encodedPath) {
            "/api/v1/auth/register" -> json(Spec.RegistrationAnswer, HttpStatusCode.Created)
            "/api/v1/auth/email/confirm" -> noContent()
            "/api/v1/auth/login" -> json(Spec.LoginAnswer)
            "/api/v1/sessions/current" ->
                if (request.method == HttpMethod.Delete) noContent() else json(Spec.UserIdAnswer)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    // ---- paths, methods, bodies ----

    @Test
    fun registerPostsTheCredentialsAndReadsTheStatus() = runTest {
        val fixture = accountFixture()

        val result = fixture.account.register(Spec.credentials)

        assertEquals(RegistrationResultDto("pending"), result)
        val request = fixture.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://api.matchlab.space/api/v1/auth/register", request.url.toString())
        assertEquals("""{"email":"student@hse.ru","password":"example-password-123"}""", request.bodyText())
    }

    @Test
    fun confirmEmailPostsTheTokenAndExpectsNoContent() = runTest {
        val fixture = accountFixture()

        fixture.account.confirmEmail(ConfirmEmailRequestDto(Spec.LetterToken))

        val request = fixture.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/auth/email/confirm", request.url.encodedPath)
        assertEquals("""{"token":"ТОКЕН_ИЗ_ПИСЬМА"}""", request.bodyText())
    }

    @Test
    fun loginPostsTheCredentialsAndReadsTheSession() = runTest {
        val fixture = accountFixture()

        val result = fixture.account.login(Spec.credentials)

        assertEquals(LoginResultDto(Spec.UserId, Spec.AccessToken), result)
        val request = fixture.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/auth/login", request.url.encodedPath)
        assertEquals("""{"email":"student@hse.ru","password":"example-password-123"}""", request.bodyText())
    }

    @Test
    fun currentSessionGetsTheUserOfTheToken() = runTest {
        val fixture = accountFixture()

        val result = fixture.account.currentSession("session-token")

        assertEquals(UserIdDto(Spec.UserId), result)
        val request = fixture.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/sessions/current", request.url.encodedPath)
        assertEquals("", request.bodyText())
    }

    @Test
    fun revokeSessionDeletesTheCurrentSession() = runTest {
        val fixture = accountFixture()

        fixture.account.revokeSession("session-token")

        val request = fixture.requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/api/v1/sessions/current", request.url.encodedPath)
        assertEquals("", request.bodyText())
    }

    @Test
    fun bodiesAreJsonInUtf8WhateverTheTextIs() = runTest {
        val fixture = accountFixture()

        fixture.account.login(CredentialsDto("студент@вшэ.рф", "пароль-юникод-🔒"))

        val request = fixture.requests.single()
        assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8), request.body.contentType)
        assertEquals("""{"email":"студент@вшэ.рф","password":"пароль-юникод-🔒"}""", request.bodyText())
    }

    @Test
    fun requestsAskForJson() = runTest {
        val fixture = accountFixture()

        fixture.account.login(Spec.credentials)
        fixture.account.currentSession("session-token")

        assertTrue(fixture.requests.all { it.headers[HttpHeaders.Accept] == "application/json" })
    }

    // ---- Authorization ----

    @Test
    fun authCallsNeverSendAuthorization() = runTest {
        val fixture = accountFixture()

        fixture.account.register(Spec.credentials)
        fixture.account.confirmEmail(ConfirmEmailRequestDto(Spec.LetterToken))
        fixture.account.login(Spec.credentials)

        assertEquals(3, fixture.requests.size)
        assertTrue(fixture.requests.all { it.authorizations.isEmpty() })
    }

    @Test
    fun theCheckAndTheSignOutSendTheTokenTheyAreGiven() = runTest {
        val fixture = accountFixture()

        fixture.account.currentSession("token-a")
        fixture.account.revokeSession("token-b")

        assertEquals(listOf("Bearer token-a"), fixture.requests[0].authorizations)
        assertEquals(listOf("Bearer token-b"), fixture.requests[1].authorizations)
    }

    @Test
    fun aTokenIsNotRememberedBetweenCalls() = runTest {
        val fixture = accountFixture()

        fixture.account.currentSession("token-a")
        fixture.account.login(Spec.credentials)

        assertTrue(fixture.requests[1].authorizations.isEmpty())
    }

    @Test
    fun aBlankTokenIsAProgrammingErrorAndSendsNothing() = runTest {
        val fixture = accountFixture()

        assertFailsWith<IllegalArgumentException> { fixture.account.currentSession("") }
        assertFailsWith<IllegalArgumentException> { fixture.account.revokeSession("   ") }

        assertTrue(fixture.requests.isEmpty())
    }

    // ---- reading answers ----

    @Test
    fun answersMayHaveFieldsTheClientDoesNotKnow() = runTest {
        val fixture = GatewayFixture {
            json("""{"user_id":"u1","access_token":"t1","expires_at_unix":"1760000000","roles":["a","b"]}""")
        }

        val result = fixture.account.login(Spec.credentials)

        assertEquals(LoginResultDto("u1", "t1"), result)
    }

    @Test
    fun aMissingOrNullStatusTakesTheDefault() = runTest {
        val missing = GatewayFixture { json("{}", HttpStatusCode.Created) }
        val explicitNull = GatewayFixture { json("""{"status":null}""", HttpStatusCode.Created) }

        assertEquals(RegistrationResultDto(""), missing.account.register(Spec.credentials))
        assertEquals(RegistrationResultDto(""), explicitNull.account.register(Spec.credentials))
    }

    @Test
    fun anAnswerWithoutARequiredFieldIsMalformed() = runTest {
        val fixture = GatewayFixture { json("""{"user_id":"u1"}""") }

        failureOf<RemoteFailure.Malformed> { fixture.account.login(Spec.credentials) }
    }

    @Test
    fun anAnswerThatIsNotJsonIsMalformed() = runTest {
        val fixture = GatewayFixture { respond("<html>welcome</html>", HttpStatusCode.OK) }

        failureOf<RemoteFailure.Malformed> { fixture.account.currentSession("session-token") }
    }

    @Test
    fun anEmptyAnswerWhereABodyIsPromisedIsMalformed() = runTest {
        val fixture = GatewayFixture { respond("", HttpStatusCode.OK) }

        failureOf<RemoteFailure.Malformed> { fixture.account.login(Spec.credentials) }
    }

    @Test
    fun anAnswerOfTheWrongShapeIsMalformed() = runTest {
        val fixture = GatewayFixture { json("""["u1","t1"]""") }

        failureOf<RemoteFailure.Malformed> { fixture.account.login(Spec.credentials) }
    }

    @Test
    fun anyTwoHundredStatusIsASuccess() = runTest {
        val fixture = GatewayFixture { request ->
            if (request.url.encodedPath.endsWith("confirm")) {
                json("{}", HttpStatusCode.OK)
            } else {
                json(Spec.RegistrationAnswer, HttpStatusCode.OK)
            }
        }

        fixture.account.confirmEmail(ConfirmEmailRequestDto(Spec.LetterToken))
        assertEquals("pending", fixture.account.register(Spec.credentials).status)
    }

    @Test
    fun theBodyOfANoContentAnswerIsNotRequired() = runTest {
        val fixture = GatewayFixture { respond("anything at all", HttpStatusCode.NoContent) }

        fixture.account.confirmEmail(ConfirmEmailRequestDto(Spec.LetterToken))
        fixture.account.revokeSession("session-token")
    }

    // ---- addresses ----

    @Test
    fun theServerAddressComesFromTheConfig() = runTest {
        val fixture = accountFixture(GatewayConfig(baseUrl = "http://10.0.2.2:8080"))

        fixture.account.login(Spec.credentials)

        assertEquals("http://10.0.2.2:8080/api/v1/auth/login", fixture.requests.single().url.toString())
    }

    @Test
    fun aTrailingSlashOfTheBaseUrlIsIgnored() = runTest {
        val fixture = accountFixture(GatewayConfig(baseUrl = "https://api.matchlab.space/"))

        fixture.account.login(Spec.credentials)

        assertEquals("https://api.matchlab.space/api/v1/auth/login", fixture.requests.single().url.toString())
    }

    @Test
    fun manyTrailingSlashesAreIgnoredToo() = runTest {
        val fixture = accountFixture(GatewayConfig(baseUrl = "http://localhost:8080///"))

        fixture.account.login(Spec.credentials)

        assertEquals("http://localhost:8080/api/v1/auth/login", fixture.requests.single().url.toString())
    }

    @Test
    fun theProductionAddressIsTheDefault() = runTest {
        val fixture = accountFixture()

        fixture.account.login(Spec.credentials)

        assertEquals("api.matchlab.space", fixture.requests.single().url.host)
        assertEquals("https://api.matchlab.space", GatewayConfig.PRODUCTION_BASE_URL)
    }
}
