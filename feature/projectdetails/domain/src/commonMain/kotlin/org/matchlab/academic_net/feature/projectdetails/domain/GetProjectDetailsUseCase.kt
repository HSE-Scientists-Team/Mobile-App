package org.matchlab.academic_net.feature.projectdetails.domain

import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.utils.suspendRunCatching

/** How asking for a project page ended; the screen has a state for each case. */
sealed interface ProjectDetailsResult {
    /** The server returned the project. */
    data class Found(val details: ProjectDetails) : ProjectDetailsResult

    /** The viewer may not see such a project: unknown id or taken off publication. */
    data object NotFound : ProjectDetailsResult

    /** The request failed (no network, server error); asking again may help. */
    data class Failure(val cause: Throwable) : ProjectDetailsResult
}

/** "Оценить проект": loads the page of one project from the server. */
class GetProjectDetailsUseCase(private val repository: ProjectDetailsRepository) {
    /**
     * Asks for the project with [id]. A failure of the repository becomes
     * [ProjectDetailsResult.Failure]; cancellation of the caller is not a failure and is rethrown.
     */
    suspend operator fun invoke(id: ProjectId): ProjectDetailsResult =
        suspendRunCatching { repository.getProjectDetails(id) }.fold(
            onSuccess = { details ->
                if (details == null) ProjectDetailsResult.NotFound else ProjectDetailsResult.Found(details)
            },
            onFailure = { ProjectDetailsResult.Failure(it) },
        )
}
