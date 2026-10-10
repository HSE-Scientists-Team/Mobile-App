package org.matchlab.academic_net.feature.projects.domain.usecase

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.utils.suspendRunCatching

sealed interface UnpublishProjectResult {
    data class Unpublished(val project: Project) : UnpublishProjectResult

    /** The project is not published (any more), so there is nothing to take off. */
    data object NotPublished : UnpublishProjectResult

    data object NotFound : UnpublishProjectResult

    data class Failure(val cause: Throwable) : UnpublishProjectResult
}

/** "Снять проект с публикации": only a project that is looking for people can be unpublished. */
class UnpublishProjectUseCase(private val repository: ProjectsRepository) {
    suspend operator fun invoke(id: ProjectId): UnpublishProjectResult {
        val project = suspendRunCatching { repository.getProject(id) }
            .getOrElse { return UnpublishProjectResult.Failure(it) }
            ?: return UnpublishProjectResult.NotFound
        if (project.status != ProjectStatus.Recruiting) return UnpublishProjectResult.NotPublished

        return suspendRunCatching { repository.unpublishProject(id) }.fold(
            onSuccess = { UnpublishProjectResult.Unpublished(it) },
            onFailure = { UnpublishProjectResult.Failure(it) },
        )
    }
}
