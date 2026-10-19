package org.matchlab.academic_net.feature.projects.domain.usecase

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.projects.domain.MemberNotRemovableException
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.utils.suspendRunCatching

/** How sending a member away ended; the screen has a state for each case. */
sealed interface RemoveMemberResult {
    /** The member is off the team; [project] is the project as it is now. */
    data class Removed(val project: Project) : RemoveMemberResult

    /** The member is the viewer or the initiator (nothing sent), or the server refused ([MemberNotRemovableException]). */
    data object NotAllowed : RemoveMemberResult

    /** There is no such project, or the viewer may not see it. */
    data object NotFound : RemoveMemberResult

    /** The request failed (no network, a server error). */
    data class Failure(val cause: Throwable) : RemoveMemberResult
}

/**
 * "Выгнать" of 07b. `getProject` (none is [RemoveMemberResult.NotFound]); the initiator and the viewer themselves are
 * [RemoveMemberResult.NotAllowed] without a request; else `removeMember`.
 */
class RemoveMemberUseCase(private val repository: ProjectsRepository) {
    suspend operator fun invoke(projectId: ProjectId, userId: UserId): RemoveMemberResult {
        val project = suspendRunCatching { repository.getProject(projectId) }
            .getOrElse { return RemoveMemberResult.Failure(it) }
            ?: return RemoveMemberResult.NotFound
        if (userId == project.initiator.id || project.members.any { it.userId == userId && it.isViewer }) {
            return RemoveMemberResult.NotAllowed
        }
        return suspendRunCatching { repository.removeMember(projectId, userId) }.fold(
            onSuccess = { RemoveMemberResult.Removed(it) },
            onFailure = {
                when (it) {
                    is MemberNotRemovableException -> RemoveMemberResult.NotAllowed
                    is ProjectNotFoundException -> RemoveMemberResult.NotFound
                    else -> RemoveMemberResult.Failure(it)
                }
            },
        )
    }
}
