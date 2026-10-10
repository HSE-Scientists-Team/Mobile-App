package org.matchlab.academic_net.utils

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching] for suspending code, but a cancelled coroutine is not turned into a failed
 * result: [CancellationException] is rethrown so cancellation keeps working.
 */
inline fun <T> suspendRunCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }
