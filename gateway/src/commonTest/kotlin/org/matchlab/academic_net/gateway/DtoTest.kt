package org.matchlab.academic_net.gateway

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.matchlab.academic_net.gateway.dto.ConfirmEmailRequestDto
import org.matchlab.academic_net.gateway.dto.CredentialsDto
import org.matchlab.academic_net.gateway.dto.LoginResultDto
import org.matchlab.academic_net.gateway.dto.RegistrationResultDto
import org.matchlab.academic_net.gateway.dto.UserIdDto
import org.matchlab.academic_net.gateway.internal.GatewayJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The shapes of the account calls: names on the wire, defaults, and what they print about themselves. */
class DtoTest {
    // ---- on the wire ----

    @Test
    fun credentialsAreWrittenWithTheNamesOfTheSpec() {
        assertEquals(
            """{"email":"student@hse.ru","password":"example-password-123"}""",
            GatewayJson.encodeToString(Spec.credentials),
        )
    }

    @Test
    fun theLetterTokenIsWrittenAsTheTokenField() {
        assertEquals("""{"token":"abc"}""", GatewayJson.encodeToString(ConfirmEmailRequestDto("abc")))
    }

    @Test
    fun theLoginResultUsesSnakeCaseBothWays() {
        val result = LoginResultDto(userId = "u1", accessToken = "t1")

        val json = GatewayJson.encodeToString(result)

        assertEquals("""{"user_id":"u1","access_token":"t1"}""", json)
        assertEquals(result, GatewayJson.decodeFromString<LoginResultDto>(json))
    }

    @Test
    fun theUserIdUsesSnakeCaseBothWays() {
        val result = UserIdDto("u1")

        assertEquals("""{"user_id":"u1"}""", GatewayJson.encodeToString(result))
        assertEquals(result, GatewayJson.decodeFromString<UserIdDto>("""{"user_id":"u1"}"""))
    }

    @Test
    fun theRegistrationStatusIsSentEvenWhenItIsTheDefault() {
        // encodeDefaults: a field that has a default is still written.
        assertEquals("""{"status":""}""", GatewayJson.encodeToString(RegistrationResultDto()))
    }

    @Test
    fun aRegistrationResultWithoutStatusReadsAsEmpty() {
        assertEquals(RegistrationResultDto(""), GatewayJson.decodeFromString<RegistrationResultDto>("{}"))
    }

    @Test
    fun theUnicodeOfTextSurvivesTheRoundTrip() {
        val credentials = CredentialsDto("студент@вшэ.рф", "пароль\"с\\кавычкой")

        val json = GatewayJson.encodeToString(credentials)

        assertEquals(credentials, GatewayJson.decodeFromString<CredentialsDto>(json))
    }

    // ---- what they print ----

    @Test
    fun credentialsPrintWithoutTheMailOrThePassword() {
        val credentials = CredentialsDto("student@hse.ru", "hunter2-hunter2")

        val printed = credentials.toString()

        assertEquals("CredentialsDto(***)", printed)
        assertFalse("student" in printed)
        assertFalse("hunter2" in "$credentials")
    }

    @Test
    fun theLetterTokenPrintsMasked() {
        val request = ConfirmEmailRequestDto("one-time-letter-token")

        assertFalse("one-time" in request.toString())
        assertTrue("token=***" in request.toString())
    }

    @Test
    fun theLoginResultPrintsTheUserButNotTheToken() {
        val result = LoginResultDto(userId = "user-42", accessToken = "very-secret-access-token")

        val printed = result.toString()

        assertTrue("user-42" in printed)
        assertFalse("very-secret" in printed)
        assertTrue("accessToken=***" in printed)
    }

    @Test
    fun masksSurviveStringTemplatesAndCollections() {
        val credentials = CredentialsDto("student@hse.ru", "hunter2-hunter2")
        val login = LoginResultDto("u1", "very-secret-access-token")

        val printed = "$credentials | ${listOf(credentials, login)} | ${mapOf("k" to login)}"

        assertFalse("hunter2" in printed)
        assertFalse("student@hse.ru" in printed)
        assertFalse("very-secret" in printed)
    }

    @Test
    fun dataClassesStillCompareByTheirContent() {
        assertEquals(CredentialsDto("a@b.c", "p"), CredentialsDto("a@b.c", "p"))
        assertEquals(LoginResultDto("u", "t").hashCode(), LoginResultDto("u", "t").hashCode())
        assertEquals("other", ConfirmEmailRequestDto("t").copy(token = "other").token)
    }
}
