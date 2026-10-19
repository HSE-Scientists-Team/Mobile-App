package org.matchlab.academic_net.feature.projects.domain.usecase

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.RoleNotRemovableException
import org.matchlab.academic_net.utils.suspendRunCatching

/** How the deletion of a role ended; the screen has a state for each case. */
sealed interface RemoveRoleResult {
    /** The role is gone (or was); [project] is the project as it is now. */
    data class Removed(val project: Project) : RemoveRoleResult

    /** The role has taken places (nothing was sent), or the server refused ([RoleNotRemovableException]). */
    data object NotRemovable : RemoveRoleResult

    /** There is no such project, or the viewer may not see it. */
    data object NotFound : RemoveRoleResult

    /** The request failed (no network, a server error, a conflict of the project's version). */
    data class Failure(val cause: Throwable) : RemoveRoleResult
}

/**
 * The trash of a role in 04d. `getProject` (none is [RemoveRoleResult.NotFound]); a role that is not in the project is
 * [RemoveRoleResult.Removed] with the project and no request; a role with `occupied > 0` is
 * [RemoveRoleResult.NotRemovable] and no request (API-GAP G64); else `removeRole`.
 */
class RemoveRoleUseCase(private val repository: ProjectsRepository) {
    suspend operator fun invoke(projectId: ProjectId, roleId: RoleId): RemoveRoleResult {
        val project = suspendRunCatching { repository.getProject(projectId) }
            .getOrElse { return RemoveRoleResult.Failure(it) }
            ?: return RemoveRoleResult.NotFound
        val role = project.roles.firstOrNull { it.id == roleId } ?: return RemoveRoleResult.Removed(project)
        if (role.occupied > 0) return RemoveRoleResult.NotRemovable

        return suspendRunCatching { repository.removeRole(projectId, roleId) }.fold(
            onSuccess = { RemoveRoleResult.Removed(it) },
            onFailure = {
                when (it) {
                    is ProjectNotFoundException -> RemoveRoleResult.NotFound
                    is RoleNotRemovableException -> RemoveRoleResult.NotRemovable
                    else -> RemoveRoleResult.Failure(it)
                }
            },
        )
    }
}
