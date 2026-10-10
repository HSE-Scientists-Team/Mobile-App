package org.matchlab.academic_net.common.result

/**
 * Outcome of sending a form: the data was accepted ([Success]), did not pass validation
 * ([Invalid], nothing was sent) or could not be saved ([Failure], e.g. no network).
 *
 * [E] is the feature's enum of field errors.
 */
sealed interface SubmitResult<out T, out E> {
    data class Success<out T>(val value: T) : SubmitResult<T, Nothing>

    data class Invalid<out E>(val errors: Set<E>) : SubmitResult<Nothing, E>

    data class Failure(val cause: Throwable) : SubmitResult<Nothing, Nothing>
}
