package org.matchlab.academic_net.gateway

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.CredentialsDto
import kotlin.test.assertIs

// What the tests share: a Gateway over a MockEngine, the answers of the API as the spec gives them, and a few
// ways to look at the requests the engine got.

/** The examples of docs/openapi.yaml. */
internal object Spec {
    const val UserId = "4f9a4c95-6144-4ec8-89e8-3866207d7561"
    const val AccessToken = "пример-токена-доступа"
    const val LetterToken = "ТОКЕН_ИЗ_ПИСЬМА"
    const val Email = "student@hse.ru"
    const val Password = "example-password-123"

    val credentials = CredentialsDto(Email, Password)

    const val RegistrationAnswer = """{"status":"pending"}"""
    const val LoginAnswer = """{"user_id":"$UserId","access_token":"$AccessToken"}"""
    const val UserIdAnswer = """{"user_id":"$UserId"}"""
}

/** An answer with a JSON body, like every answer of the API except `/health`. */
internal fun MockRequestHandleScope.json(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

/** An answer without a body (204). */
internal fun MockRequestHandleScope.noContent(): HttpResponseData = respond("", HttpStatusCode.NoContent)

/** An answer with an `{"error": ...}` body, like the errors of the spec. */
internal fun MockRequestHandleScope.jsonError(status: HttpStatusCode, message: String): HttpResponseData =
    json("""{"error":"$message"}""", status)

internal suspend fun HttpRequestData.bodyText(): String = body.toByteArray().decodeToString()

/** Every `Authorization` header of the request, so that "no header" and "two headers" can be told apart. */
internal val HttpRequestData.authorizations: List<String>
    get() = headers.getAll(HttpHeaders.Authorization).orEmpty()

/** Lines of the call log, collected from whichever thread Ktor logs on. */
internal class LogLines : Logger {
    private val channel = Channel<String>(Channel.UNLIMITED)

    override fun log(message: String) {
        channel.trySend(message)
    }

    /** Everything logged so far. */
    fun all(): List<String> = buildList {
        while (true) add(channel.tryReceive().getOrNull() ?: break)
    }
}

/** A [Gateway] over a [MockEngine] driven by [handler]; [log] holds what the Gateway logged. */
internal class GatewayFixture(
    config: GatewayConfig = GatewayConfig(),
    handler: MockRequestHandler,
) {
    val engine = MockEngine(handler)
    val log = LogLines()
    val gateway = Gateway(config, engine, log)
    val account get() = gateway.account

    /** The requests the engine got, in order. */
    val requests: List<HttpRequestData> get() = engine.requestHistory

    val engineIsOpen: Boolean get() = engine.coroutineContext[Job]!!.isActive
}

/** Runs [block] and returns the [RemoteFailure] it throws; fails the test if it throws anything else or nothing. */
internal suspend inline fun <reified F : RemoteFailure> failureOf(block: () -> Unit): F {
    val thrown = try {
        block()
        null
    } catch (failure: Throwable) {
        failure
    }
    assertIs<F>(thrown, "Expected ${F::class.simpleName}, got $thrown")
    return thrown
}
