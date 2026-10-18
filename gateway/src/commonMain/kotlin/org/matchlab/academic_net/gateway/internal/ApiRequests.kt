package org.matchlab.academic_net.gateway.internal

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.http.withCharset
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.utils.suspendRunCatching
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

private val JsonBody = ContentType.Application.Json.withCharset(Charsets.UTF_8)

/**
 * Makes the calls of the API with one [HttpClient]: the one place that builds a request, checks the answer and
 * turns whatever went wrong into a [RemoteFailure]. [KtorAccountApi] and the APIs of the next blocks (profiles,
 * projects, search...) are thin layers on top of it: a path, a body, an answer type.
 *
 * - **Paths** are relative to the API prefix (`auth/login`, `users/me/profile`); an id taken from outside goes
 *   into a path through [pathSegment].
 * - **Bodies** are encoded and answers decoded here, with [GatewayJson], not by a Ktor plugin. A decoding error
 *   carries a piece of the answer in its message (for `login`, the token); inside Ktor's pipeline the logging
 *   plugin would print that message, here it surfaces only as [RemoteFailure.Malformed] with the piece cut out.
 * - **Authorization.** The client of a session already carries `Bearer <token>` in every request; the calls of
 *   [AccountApi] that name a token pass it as `bearerToken`, and no other call has the header.
 * - **A status outside 2xx** is [RemoteFailure.Http], with the `{"error": ...}` text if the body has it. If the
 *   status is 401 and [onUnauthorized] is set, it is told first (the client of a session sets it).
 * - **Cancellation** of the caller is never turned into a failure.
 * - **A closed session** calls [refuseFurtherCalls] on the instance of its client; from then on every call ends with
 *   a `CancellationException` before anything is sent.
 *
 * @param apiBase address with the API prefix, no trailing slash: `https://api.matchlab.space/api/v1`.
 * @param onUnauthorized called, inside [NonCancellable], once for each call whose answer is a 401.
 */
internal class ApiRequests(
    private val http: HttpClient,
    private val apiBase: String,
    private val onUnauthorized: (suspend () -> Unit)? = null,
) {
    // Written once by refuseFurtherCalls() on the thread that closes the session, read by every call on its own thread.
    @Volatile
    private var refusing = false

    /**
     * Makes every call from now on end with a `CancellationException("The session was closed")`, without a request.
     * The client of a session cannot be trusted to refuse by itself at once: Ktor finishes a closed client a moment
     * after `close()` returns, and until then it still takes a request, which would reach the server with the token of
     * the session that was closed. Calls that are already running are not touched here (the owner cancels the client
     * for those). Idempotent.
     */
    fun refuseFurtherCalls() {
        refusing = true
    }

    /**
     * Sends one request and hands the text of a 2xx answer to [parse]. Everything that goes wrong, here or in
     * [parse], is thrown as a [RemoteFailure]; see [asRemoteFailure] for which exception is which. After
     * [refuseFurtherCalls] nothing is sent and a `CancellationException` is thrown.
     *
     * @param body the JSON text to send, or `null` for a request without a body.
     * @param query parameters of the URL; those with a `null` value are left out.
     * @param repeatedQuery parameters of the URL that may come several times, the way OpenAPI writes an array in the
     * query by default (`form`, exploded: `skill=a&skill=b`); each value is a `name=value` of its own, in the order of
     * the list, and an empty string is left out. A name that is also in [query] is added to it, not replaced.
     * @param bearerToken a token to send as `Authorization: Bearer`, for calls made outside a session.
     */
    suspend fun <T> call(
        method: HttpMethod,
        path: String,
        query: Map<String, String?> = emptyMap(),
        repeatedQuery: Map<String, List<String>> = emptyMap(),
        body: String? = null,
        bearerToken: String? = null,
        parse: (String) -> T,
    ): T {
        // Before anything else: a closed session sends nothing, whatever it was asked for.
        if (refusing) throw CancellationException("The session was closed")
        require(bearerToken == null || bearerToken.isNotBlank()) { "The access token cannot be blank" }
        return translating {
            val response = http.request("$apiBase/${path.trimStart('/')}") {
                this.method = method
                accept(ContentType.Application.Json)
                if (bearerToken != null) bearerAuth(bearerToken)
                query.forEach { (name, value) -> if (value != null) parameter(name, value) }
                // Ktor's parameter() appends to the ones already there, so the same name can be given many times.
                repeatedQuery.forEach { (name, values) ->
                    values.forEach { value -> if (value.isNotEmpty()) parameter(name, value) }
                }
                if (body != null) setBody(TextContent(body, JsonBody))
            }
            if (!response.status.isSuccess()) throw failureOf(response)
            parse(response.bodyAsText())
        }
    }

    /** `GET` whose answer is a JSON of type [T]; [query] and [repeatedQuery] are those of [call]. */
    suspend inline fun <reified T> get(
        path: String,
        query: Map<String, String?> = emptyMap(),
        repeatedQuery: Map<String, List<String>> = emptyMap(),
        bearerToken: String? = null,
    ): T = call(HttpMethod.Get, path, query, repeatedQuery, bearerToken = bearerToken) {
        GatewayJson.decodeFromString<T>(it)
    }

    /** `POST` of [body] as JSON, whose answer is a JSON of type [T]. */
    suspend inline fun <reified B, reified T> post(path: String, body: B, bearerToken: String? = null): T =
        call(HttpMethod.Post, path, body = GatewayJson.encodeToString(body), bearerToken = bearerToken) {
            GatewayJson.decodeFromString<T>(it)
        }

    /** `POST` of [body] as JSON, whose answer has no body that matters (204). */
    suspend inline fun <reified B> postWithoutAnswer(path: String, body: B, bearerToken: String? = null) {
        call(HttpMethod.Post, path, body = GatewayJson.encodeToString(body), bearerToken = bearerToken) { }
    }

    /**
     * `GET` whose answer is read as the DTO [T] and also keeps the raw `details` object of the JSON, for a
     * read-modify-write (see [layOver]).
     */
    suspend inline fun <reified T> getWithDetails(path: String): AnswerWithDetails<T> =
        call(HttpMethod.Get, path) { text -> AnswerWithDetails(GatewayJson.decodeFromString<T>(text), rawDetailsOf(text)) }

    /** `POST` without a body, whose answer is a JSON of type [T]. */
    suspend inline fun <reified T> postEmpty(path: String): T =
        call(HttpMethod.Post, path) { GatewayJson.decodeFromString<T>(it) }

    /** `PUT` of [body], the JSON text of a whole request body, whose answer is a JSON of type [T]. */
    suspend inline fun <reified T> putJson(path: String, body: String): T =
        call(HttpMethod.Put, path, body = body) { GatewayJson.decodeFromString<T>(it) }

    /** `DELETE` whose answer has no body that matters (204). */
    suspend fun delete(path: String, bearerToken: String? = null) {
        call(HttpMethod.Delete, path, bearerToken = bearerToken) { }
    }

    /**
     * Runs [block] and throws what went wrong as a [RemoteFailure].
     *
     * Ktor unwraps a cancellation to the exception that caused it, so a coroutine cancelled with a cause
     * can see that cause (an `IOException`) here instead of a `CancellationException`. A caller that has been
     * cancelled must stay cancelled, whatever the transport reported, hence [ensureActive].
     */
    private suspend fun <T> translating(block: suspend () -> T): T =
        try {
            block()
        } catch (cause: Throwable) {
            currentCoroutineContext().ensureActive()
            throw cause.asRemoteFailure()
        }

    /** The failure for a non-2xx [response]; tells [onUnauthorized] about a 401 before returning it. */
    private suspend fun failureOf(response: HttpResponse): RemoteFailure.Http {
        val failure = httpFailure(response.status.value, suspendRunCatching { response.bodyAsText() }.getOrNull())
        if (response.status == HttpStatusCode.Unauthorized) notifyUnauthorized(failure)
        return failure
    }

    /**
     * The session is lost even if the caller no longer waits for the answer, hence [NonCancellable]. If the
     * listener fails, the call still ends with the 401: the listener's error is attached to it.
     */
    private suspend fun notifyUnauthorized(failure: RemoteFailure.Http) {
        val listener = onUnauthorized ?: return
        try {
            withContext(NonCancellable) { listener() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            failure.addSuppressed(error)
        }
    }
}
