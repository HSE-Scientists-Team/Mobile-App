package org.matchlab.academic_net.feature.responses.domain.usecase

import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.feature.responses.domain.ProjectJoinsRepository
import org.matchlab.academic_net.feature.responses.domain.ProjectResponsesUnavailableException
import org.matchlab.academic_net.feature.responses.domain.ResponsesProjectSource
import org.matchlab.academic_net.utils.suspendRunCatching

/** How reading the applications of a project ended. */
sealed interface RefreshResponsesResult {
    /** The records are read into the repository. */
    data object Done : RefreshResponsesResult

    /** The project is `null`, or the server refused the list ([ProjectResponsesUnavailableException]). */
    data object NotFound : RefreshResponsesResult

    /** The request failed (no network, a server error); asking again may help. */
    data class Failure(val cause: Throwable) : RefreshResponsesResult
}

/** 07d: `getProject` (null is NotFound), then `refreshProjectResponses`. */
class RefreshProjectResponsesUseCase(
    private val joins: ProjectJoinsRepository,
    private val projects: ResponsesProjectSource,
) {
    suspend operator fun invoke(projectId: ProjectId): RefreshResponsesResult {
        val project = suspendRunCatching { projects.getProject(projectId) }
            .getOrElse { return RefreshResponsesResult.Failure(it) }
        if (project == null) return RefreshResponsesResult.NotFound
        return suspendRunCatching { joins.refreshProjectResponses(projectId) }.fold(
            onSuccess = { RefreshResponsesResult.Done },
            onFailure = {
                if (it is ProjectResponsesUnavailableException) RefreshResponsesResult.NotFound
                else RefreshResponsesResult.Failure(it)
            },
        )
    }
}
