package org.matchlab.academic_net.gateway.internal

import org.matchlab.academic_net.common.result.RemoteFailure

/**
 * Translates a transport error that only this platform produces, or returns `null` if [cause] is not one.
 *
 * Everything that is an `IOException` (OkHttp's errors on Android; Ktor's own timeouts and the Darwin
 * engine's `DarwinHttpRequestException` on iOS) is already handled by the common mapping, so this is the
 * place for what the platform knows better: on iOS, the code of the `NSError` behind the exception.
 */
internal expect fun platformFailure(cause: Throwable): RemoteFailure?
