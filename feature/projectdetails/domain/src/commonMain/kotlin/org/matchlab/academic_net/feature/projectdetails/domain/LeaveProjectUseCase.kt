package org.matchlab.academic_net.feature.projectdetails.domain

import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.utils.suspendRunCatching

/** How leaving a project ended; the screen has a state for each case. */
sealed interface LeaveProjectResult {
    /** The viewer is off the team; the page is read again. */
    data object Left : LeaveProjectResult

    /** The server refused ([LeaveNotAllowedException]): 04h. */
    data object NotAllowed : LeaveProjectResult

    /** The project is gone for the viewer ([ProjectGoneException]). */
    data object NotFound : LeaveProjectResult

    /** The request failed (no network, a server error). */
    data class Failure(val cause: Throwable) : LeaveProjectResult
}

/** "Покинуть проект" of 04c: `leaveProject` and its outcome as a result. */
class LeaveProjectUseCase(private val repository: ProjectDetailsRepository) {
    suspend operator fun invoke(id: ProjectId): LeaveProjectResult =
        suspendRunCatching { repository.leaveProject(id) }.fold(
            onSuccess = { LeaveProjectResult.Left },
            onFailure = {
                when (it) {
                    is LeaveNotAllowedException -> LeaveProjectResult.NotAllowed
                    is ProjectGoneException -> LeaveProjectResult.NotFound
                    else -> LeaveProjectResult.Failure(it)
                }
            },
        )
}
