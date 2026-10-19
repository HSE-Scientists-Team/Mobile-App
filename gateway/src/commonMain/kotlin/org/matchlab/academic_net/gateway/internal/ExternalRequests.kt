package org.matchlab.academic_net.gateway.internal

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/**
 * The requests that leave the API: the signed links of the file storage ([MediaApi]). The client is the one of the
 * Gateway **without** the token of the session, so the token never reaches the storage, and the signature of the link
 * is the only credential. Failures are translated like those of [ApiRequests]; the body of an error of the storage is
 * not read (it is not JSON of the API).
 */
internal class ExternalRequests(private val client: Lazy<HttpClient>) {
    private val http: HttpClient get() = client.value

    @Volatile
    private var refusing = false

    /** Like [ApiRequests.refuseFurtherCalls]: a closed session sends nothing. */
    fun refuseFurtherCalls() {
        refusing = true
    }

    /**
     * `PUT` of [bytes] to [url] with [headers]. `Content-Type` is given to the body and `Content-Length` is the
     * engine's own, as the engines control both.
     */
    suspend fun put(url: String, headers: Map<String, String>, bytes: ByteArray) {
        val contentType = headers.entries.firstOrNull { it.key.equals(HttpHeaders.ContentType, ignoreCase = true) }
            ?.let { runCatching { ContentType.parse(it.value) }.getOrNull() }
            ?: ContentType.Application.OctetStream
        execute {
            http.request(url) {
                method = HttpMethod.Put
                headers.forEach { (name, value) ->
                    if (!name.equals(HttpHeaders.ContentType, true) && !name.equals(HttpHeaders.ContentLength, true)) {
                        header(name, value)
                    }
                }
                setBody(ByteArrayContent(bytes, contentType))
            }
        }
    }

    /** `GET` of [url]: the bytes of a 2xx answer. */
    suspend fun get(url: String): ByteArray = execute { http.request(url) { method = HttpMethod.Get } }.readRawBytes()

    private suspend fun execute(send: suspend () -> HttpResponse): HttpResponse {
        if (refusing) throw CancellationException("The session was closed")
        return try {
            val response = send()
            if (!response.status.isSuccess()) throw httpFailure(response.status.value, null)
            response
        } catch (cause: Throwable) {
            currentCoroutineContext().ensureActive()
            throw cause.asRemoteFailure()
        }
    }
}
