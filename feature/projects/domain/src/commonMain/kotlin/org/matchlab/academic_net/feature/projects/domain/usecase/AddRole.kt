package org.matchlab.academic_net.feature.projects.domain.usecase

import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.feature.projects.domain.RoleDraftError
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * "Объявить роли": adds a vacant role for one person to a project that is already published (API-GAP G49).
 *
 * A draft that does not pass validation is [SubmitResult.Invalid] and nothing is sent. A failure of the repository is
 * [SubmitResult.Failure]: among them [org.matchlab.academic_net.feature.projects.domain.ProjectConflictException] (the
 * project changed on the server; it has been read again) and
 * [org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException] (the project is gone).
 */
class AddRoleUseCase(private val repository: ProjectsRepository) {
    suspend operator fun invoke(
        projectId: ProjectId,
        draft: RoleDraft,
    ): SubmitResult<Role, RoleDraftError> {
        val normalized = draft.normalized()
        val errors = normalized.validate()
        if (errors.isNotEmpty()) return SubmitResult.Invalid(errors)

        return suspendRunCatching { repository.addRole(projectId, normalized) }.fold(
            onSuccess = { SubmitResult.Success(it) },
            onFailure = { SubmitResult.Failure(it) },
        )
    }
}
