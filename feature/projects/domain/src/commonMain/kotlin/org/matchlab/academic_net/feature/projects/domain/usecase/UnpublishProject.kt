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
     * The project is not published (any more), so there is nothing to take off. API-GAP G41: also while the status of
     * the project is unknown (`null`), because only a project known to be [ProjectStatus.Recruiting] can be taken off.
     */
    data object NotPublished : UnpublishProjectResult

    /** There is no such project, or the viewer may not see it. */
    data object NotFound : UnpublishProjectResult

    /**
     * The request failed (no network, a server error), or [cause] is
     * [org.matchlab.academic_net.common.result.FeatureUnavailableException] (API-GAP G42).
     */
    data class Failure(val cause: Throwable) : UnpublishProjectResult
}

/**
 * "Снять проект с публикации": only a project that is looking for people can be unpublished.
 *
 * API-GAP G42: the Gateway cannot do it (the `action` of the transitions has no list of values). Without
 * [org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities.canUnpublish] the screen does not offer it.
 */
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
