package org.matchlab.academic_net.feature.projects.domain.usecase

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.projects.domain.ProjectDraftError
import org.matchlab.academic_net.feature.projects.domain.ProjectInfoDraft
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.toProjectInfo
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * "Сохранить изменения" of 04d: replaces the description of a published project (the roles are changed by their own use
 * cases). A draft that does not pass validation is [SubmitResult.Invalid] and nothing is sent. A failure of the
 * repository is [SubmitResult.Failure]: among them `ProjectConflictException` (the project changed on the server; it has
 * been read again) and `ProjectNotFoundException` (the project is gone).
 */
class UpdateProjectUseCase(private val repository: ProjectsRepository) {
    suspend operator fun invoke(
        id: ProjectId,
        draft: ProjectInfoDraft,
    ): SubmitResult<Project, ProjectDraftError> {
        val normalized = draft.normalized()
        val errors = normalized.validate()
        val info = normalized.toProjectInfo()
        if (errors.isNotEmpty() || info == null) return SubmitResult.Invalid(errors)

        return suspendRunCatching { repository.updateProject(id, info) }.fold(
            onSuccess = { SubmitResult.Success(it) },
            onFailure = { SubmitResult.Failure(it) },
        )
    }
}
