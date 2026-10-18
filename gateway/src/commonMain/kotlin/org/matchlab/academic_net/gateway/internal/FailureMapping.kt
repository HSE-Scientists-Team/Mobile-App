package org.matchlab.academic_net.gateway.internal

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.matchlab.academic_net.common.result.RemoteFailure
import kotlin.coroutines.cancellation.CancellationException

// The one place where what went wrong with a call becomes a RemoteFailure.

/** Longest `{"error": ...}` text kept in [RemoteFailure.Http.serverMessage]: it is for diagnostics only. */
internal const val MaxServerMessageLength = 500

/** What kotlinx.serialization appends to the message of a syntax error: a piece of the text that failed to parse. */
private const val JsonInputMarker = "\nJSON input: "

/**
 * What [this], thrown while a call was made or its answer was read, is for the caller:
 * - a [RemoteFailure] already is one;
 * - a `CancellationException` is not a failure and stays what it is;
 * - Ktor's request, connect and socket timeouts are [RemoteFailure.Timeout];
 * - a [SerializationException] (the body is not the JSON the API promises) is [RemoteFailure.Malformed];
 * - every other `IOException`, and what the platform recognises ([platformFailure]), is [RemoteFailure.NoConnection];
 * - anything else is a bug, not a property of the network, and is returned as it is.
 *
 * The timeouts are checked before the `IOException`s because Ktor's timeouts are `IOException`s themselves.
 */
internal fun Throwable.asRemoteFailure(): Throwable {
    if (this is RemoteFailure || this is CancellationException) return this
    return when (this) {
        is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException ->
            RemoteFailure.Timeout(this)
        is SerializationException -> RemoteFailure.Malformed(withoutInputFragment())
        else -> platformFailure(this) ?: if (this is IOException) RemoteFailure.NoConnection(this) else this
    }
}

/**
 * [this] without the piece of the JSON that kotlinx.serialization quotes in the message of a syntax error.
 * That piece is part of the answer, and the answer of `login` holds the access token: the cause of
 * [RemoteFailure.Malformed] ends up in logs and crash reports, which must not hold it.
 */
private fun SerializationException.withoutInputFragment(): Throwable {
    val text = message ?: return this
    if (JsonInputMarker !in text) return this
    return SerializationException(text.substringBefore(JsonInputMarker))
}

/** The failure for an answer with the non-2xx [status]; [body] is the text of the answer, if it could be read. */
internal fun httpFailure(status: Int, body: String?): RemoteFailure.Http =
    RemoteFailure.Http(status, body?.let(::serverMessageOf))

/**
 * The text of `{"error": "..."}`, or `null` if the body is anything else: not JSON, not an object, no `error`,
 * an `error` that is not a string, or an empty one. Never throws: the status is what matters, the text is a
 * bonus for the developer. The text is trimmed and cut to [MaxServerMessageLength].
 */
internal fun serverMessageOf(body: String): String? {
    val root = try {
        GatewayJson.parseToJsonElement(body)
    } catch (_: SerializationException) {
        return null
    }
    val error = (root as? JsonObject)?.get("error") as? JsonPrimitive ?: return null
    if (!error.isString) return null
    return error.content.trim().takeIf { it.isNotEmpty() }?.take(MaxServerMessageLength)
}
