package org.matchlab.academic_net.feature.projects.domain.usecase

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.projects.domain.ProjectDraft
import org.matchlab.academic_net.feature.projects.domain.ProjectDraftError
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.toNewProject
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * "Опубликовать новый проект" (together with "объявить роли": the draft carries its roles).
 *
 * Roles are optional: more can be declared later from the project's roles screen.
 */
class PublishProjectUseCase(private val repository: ProjectsRepository) {
    suspend operator fun invoke(draft: ProjectDraft): SubmitResult<Project, ProjectDraftError> {
        val normalized = draft.normalized()
        val errors = normalized.validate()
        val newProject = normalized.toNewProject()
        if (errors.isNotEmpty() || newProject == null) return SubmitResult.Invalid(errors)

        return suspendRunCatching { repository.publishProject(newProject) }.fold(
            onSuccess = { SubmitResult.Success(it) },
            onFailure = { SubmitResult.Failure(it) },
        )
    }
}
