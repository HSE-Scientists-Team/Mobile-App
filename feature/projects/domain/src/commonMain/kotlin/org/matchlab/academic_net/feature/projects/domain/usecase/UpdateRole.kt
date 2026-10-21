package org.matchlab.academic_net.feature.projects.domain.usecase

import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.feature.projects.domain.RoleDraftError
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * The pencil of a role in 04d: changes the title and the skills of a role. Like [AddRoleUseCase], a draft that does not
 * pass validation is [SubmitResult.Invalid] and nothing is sent; a failure of the repository is [SubmitResult.Failure],
 * among them `ProjectConflictException` (the project changed, or the role is gone; it has been read again) and
 * `ProjectNotFoundException`.
 */
class UpdateRoleUseCase(private val repository: ProjectsRepository) {
    suspend operator fun invoke(
        projectId: ProjectId,
        roleId: RoleId,
        draft: RoleDraft,
    ): SubmitResult<Role, RoleDraftError> {
        val normalized = draft.normalized()
        val errors = normalized.validate()
        if (errors.isNotEmpty()) return SubmitResult.Invalid(errors)

        return suspendRunCatching { repository.updateRole(projectId, roleId, normalized) }.fold(
            onSuccess = { SubmitResult.Success(it) },
            onFailure = { SubmitResult.Failure(it) },
        )
    }
}
