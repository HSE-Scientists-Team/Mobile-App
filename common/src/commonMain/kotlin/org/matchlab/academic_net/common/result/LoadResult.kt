package org.matchlab.academic_net.common.result

/**
 * Outcome of loading one thing from the server (a profile, a review, ...): the screen has a state
 * for each case.
 *
 * Unlike [SubmitResult] there is no validation here, but the server may know nothing about the
 * thing, which is not an error and not worth a retry.
 */
sealed interface LoadResult<out T> {
    /** The server returned the thing. */
    data class Found<out T>(val value: T) : LoadResult<T>

    /** There is no such thing, or the viewer may not see it; asking again will not help. */
    data object NotFound : LoadResult<Nothing>

    /** The request failed (no network, server error); asking again may help. */
    data class Failure(val cause: Throwable) : LoadResult<Nothing>
}
