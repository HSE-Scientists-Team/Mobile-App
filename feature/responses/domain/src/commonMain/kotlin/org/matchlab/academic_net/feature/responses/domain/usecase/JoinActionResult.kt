package org.matchlab.academic_net.feature.responses.domain.usecase

import org.matchlab.academic_net.common.model.ProjectJoin
import org.matchlab.academic_net.feature.responses.domain.JoinRefusal
import org.matchlab.academic_net.feature.responses.domain.JoinRefusedException
import kotlin.coroutines.cancellation.CancellationException

/** How an action on an application or an invitation (withdraw, accept, decline) ended; the screen has a state for each case. */
sealed interface JoinActionResult {
    /** The server did it; [join] is the record as it is now. */
    data class Done(val join: ProjectJoin) : JoinActionResult

    /** The role has no free place: nothing was sent (only [DecideApplicationUseCase] with accept). */
    data object RoleFull : JoinActionResult

    /** The server refused (403, 404, 409): "Действие недоступно". */
    data class Refused(val refusal: JoinRefusal) : JoinActionResult

    /** The request failed (no network, a server error); asking again may help. */
    data class Failure(val cause: Throwable) : JoinActionResult
}

/**
 * Runs [action] and turns its outcome into a [JoinActionResult]: a [JoinRefusedException] is [JoinActionResult.Refused]
 * (and [onRefused] runs first, best effort: the screen's list is read again, since it showed what the server has changed),
 * any other exception is [JoinActionResult.Failure]. A cancellation passes through.
 */
internal suspend inline fun joinAction(
    onRefused: () -> Unit = {},
    action: () -> ProjectJoin,
): JoinActionResult =
    try {
        JoinActionResult.Done(action())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (refused: JoinRefusedException) {
        onRefused()
        JoinActionResult.Refused(refused.refusal)
    } catch (error: Throwable) {
        JoinActionResult.Failure(error)
    }
