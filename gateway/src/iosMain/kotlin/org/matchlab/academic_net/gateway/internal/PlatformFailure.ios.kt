package org.matchlab.academic_net.gateway.internal

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import kotlinx.cinterop.UnsafeNumber
import org.matchlab.academic_net.common.result.RemoteFailure
import platform.Foundation.NSURLErrorTimedOut

/**
 * The Darwin engine reports a failed NSURLSession task as a `DarwinHttpRequestException` that wraps the
 * `NSError`. A timeout is told by the error code (the engine turns most of them into Ktor's own timeout
 * exception, but not all paths do); any other `NSError` is a failure to talk to the server.
 */
@OptIn(UnsafeNumber::class)
internal actual fun platformFailure(cause: Throwable): RemoteFailure? {
    if (cause !is DarwinHttpRequestException) return null
    val timedOut = cause.origin.code == NSURLErrorTimedOut
    return if (timedOut) RemoteFailure.Timeout(cause) else RemoteFailure.NoConnection(cause)
}
