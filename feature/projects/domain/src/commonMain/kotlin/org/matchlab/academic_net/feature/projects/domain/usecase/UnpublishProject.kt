package org.matchlab.academic_net.feature.projects.domain.usecase

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.utils.suspendRunCatching

/** How taking a project off publication ended; the screen has a state for each case. */
sealed interface UnpublishProjectResult {
    /** The server hid the project from search; [project] is the project as it is now. */
    data class Unpublished(val project: Project) : UnpublishProjectResult

    /**
     * The project is already off publication ([ProjectStatus.Unpublished]), so there is nothing to take off. A project
     * whose status is unknown (`null`) is not this: it is tried (`docs/api-values.md`, §4).
     */
    data object NotPublished : UnpublishProjectResult

    /** There is no such project, or the viewer may not see it. */
    data object NotFound : UnpublishProjectResult

    /**
     * The request failed (no network, a server error, a conflict of the project's version), or [cause] is
     * [org.matchlab.academic_net.common.result.FeatureUnavailableException] because the source cannot do it.
     */
    data class Failure(val cause: Throwable) : UnpublishProjectResult
}

/**
 * "Снять проект с публикации": any project that is not already off publication can be unpublished. The status of the
 * project is whatever the cache has, and an unknown one (`null`) does not stop the attempt: the server decides.
 *
 * The action is `POST /projects/{id}/transitions` (`docs/api-values.md`). Without
 * [org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities.canUnpublish] the screen does not offer it.
 */
class UnpublishProjectUseCase(private val repository: ProjectsRepository) {
    suspend operator fun invoke(id: ProjectId): UnpublishProjectResult {
        val project = suspendRunCatching { repository.getProject(id) }
            .getOrElse { return UnpublishProjectResult.Failure(it) }
            ?: return UnpublishProjectResult.NotFound
        if (project.status == ProjectStatus.Unpublished) return UnpublishProjectResult.NotPublished

        return suspendRunCatching { repository.unpublishProject(id) }.fold(
            onSuccess = { UnpublishProjectResult.Unpublished(it) },
            onFailure = { UnpublishProjectResult.Failure(it) },
        )
    }
}
