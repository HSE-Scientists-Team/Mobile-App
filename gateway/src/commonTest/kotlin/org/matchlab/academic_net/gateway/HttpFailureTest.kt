package org.matchlab.academic_net.gateway

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.ConfirmEmailRequestDto
import org.matchlab.academic_net.gateway.internal.MaxServerMessageLength
import org.matchlab.academic_net.gateway.internal.serverMessageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** A status outside 2xx becomes [RemoteFailure.Http], with the text of `{"error": ...}` when there is one. */
class HttpFailureTest {
    @Test
    fun everyErrorStatusOfTheSpecIsKeptWithItsText() = runTest {
        val statuses = mapOf(
            HttpStatusCode.BadRequest to "некорректное тело запроса",
            HttpStatusCode.Unauthorized to "неверный email или пароль",
            HttpStatusCode.Forbidden to "подтвердите адрес электронной почты",
            HttpStatusCode.NotFound to "токен подтверждения не найден или уже использован",
            HttpStatusCode.Conflict to "email уже зарегистрирован",
            HttpStatusCode.Gone to "срок подтверждения адреса электронной почты истёк",
            HttpStatusCode.InternalServerError to "внутренняя ошибка",
            HttpStatusCode.ServiceUnavailable to "сервис недоступен",
        )
        for ((status, text) in statuses) {
            val fixture = GatewayFixture { jsonError(status, text) }

            val failure = failureOf<RemoteFailure.Http> { fixture.account.login(Spec.credentials) }

            assertEquals(status.value, failure.status)
            assertEquals(text, failure.serverMessage)
        }
    }

    @Test
    fun theFailureOfEveryCallCarriesItsStatus() = runTest {
        val fixture = GatewayFixture { jsonError(HttpStatusCode.Conflict, "email уже зарегистрирован") }

        assertEquals(409, failureOf<RemoteFailure.Http> { fixture.account.register(Spec.credentials) }.status)
        assertEquals(
            409,
            failureOf<RemoteFailure.Http> { fixture.account.confirmEmail(ConfirmEmailRequestDto("t")) }.status,
        )
        assertEquals(409, failureOf<RemoteFailure.Http> { fixture.account.login(Spec.credentials) }.status)
        assertEquals(409, failureOf<RemoteFailure.Http> { fixture.account.currentSession("t") }.status)
        assertEquals(409, failureOf<RemoteFailure.Http> { fixture.account.revokeSession("t") }.status)
    }

    @Test
    fun theMessageNamesTheStatusOnly() = runTest {
        val fixture = GatewayFixture { jsonError(HttpStatusCode.Unauthorized, "неверный email или пароль") }

        val failure = failureOf<RemoteFailure.Http> { fixture.account.login(Spec.credentials) }

        assertEquals("HTTP 401", failure.message)
        assertFalse("неверный" in failure.toString())
    }

    @Test
    fun aBodyThatIsNotJsonKeepsTheStatusOnly() = runTest {
        val fixture = GatewayFixture {
            respond("<html><body>502 Bad Gateway</body></html>", HttpStatusCode.BadGateway)
        }

        val failure = failureOf<RemoteFailure.Http> { fixture.account.login(Spec.credentials) }

        assertEquals(502, failure.status)
        assertNull(failure.serverMessage)
    }

    @Test
    fun anEmptyBodyKeepsTheStatusOnly() = runTest {
        val fixture = GatewayFixture { respond("", HttpStatusCode.ServiceUnavailable) }

        val failure = failureOf<RemoteFailure.Http> { fixture.account.register(Spec.credentials) }

        assertEquals(503, failure.status)
        assertNull(failure.serverMessage)
    }

    @Test
    fun aBodyThatCannotBeDecodedKeepsTheStatusOnly() = runTest {
        // 0xC3 0x28 is not UTF-8.
        val fixture = GatewayFixture {
            respond(
                byteArrayOf(0xC3.toByte(), 0x28),
                HttpStatusCode.InternalServerError,
                headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8"),
            )
        }

        val failure = failureOf<RemoteFailure.Http> { fixture.account.login(Spec.credentials) }

        assertEquals(500, failure.status)
        assertNull(failure.serverMessage)
    }

    @Test
    fun aRedirectOfAPostIsAFailureNotSomethingToFollow() = runTest {
        val fixture = GatewayFixture {
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://elsewhere.example/login"))
        }

        val failure = failureOf<RemoteFailure.Http> { fixture.account.login(Spec.credentials) }

        assertEquals(302, failure.status)
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun aStatusThatIsNotInTheSpecIsKeptAsWell() = runTest {
        val fixture = GatewayFixture { respond("", HttpStatusCode(418, "I'm a teapot")) }

        assertEquals(418, failureOf<RemoteFailure.Http> { fixture.account.login(Spec.credentials) }.status)
    }

    // ---- the text of the error, tolerantly ----

    @Test
    fun theErrorTextIsReadFromAnObject() {
        assertEquals("email уже зарегистрирован", serverMessageOf("""{"error":"email уже зарегистрирован"}"""))
    }

    @Test
    fun otherFieldsAroundTheErrorDoNotMatter() {
        assertEquals("boom", serverMessageOf("""{"code":7,"error":"boom","details":{"a":[1,2]}}"""))
    }

    @Test
    fun whitespaceAroundTheErrorTextIsTrimmed() {
        // \n inside a raw string is the JSON escape for a line break.
        assertEquals("boom", serverMessageOf("""{"error":"  boom\n"}"""))
    }

    @Test
    fun aBodyThatHasNoUsableErrorTextGivesNull() {
        val bodies = listOf(
            "",
            "   ",
            "not json",
            "<html></html>",
            "{",
            "[]",
            """["error"]""",
            "null",
            "42",
            "\"error\"",
            "{}",
            """{"message":"boom"}""",
            """{"error":null}""",
            """{"error":""}""",
            """{"error":"   "}""",
            """{"error":42}""",
            """{"error":true}""",
            """{"error":{"message":"boom"}}""",
            """{"error":["boom"]}""",
        )
        for (body in bodies) {
            assertNull(serverMessageOf(body), "body: $body")
        }
    }

    @Test
    fun aLongErrorTextIsCut() {
        val message = serverMessageOf("""{"error":"${"x".repeat(MaxServerMessageLength * 3)}"}""")

        assertEquals(MaxServerMessageLength, message?.length)
    }

    @Test
    fun anErrorTextAsLongAsTheLimitIsKeptWhole() {
        val text = "y".repeat(MaxServerMessageLength)

        assertEquals(text, serverMessageOf("""{"error":"$text"}"""))
    }
}
