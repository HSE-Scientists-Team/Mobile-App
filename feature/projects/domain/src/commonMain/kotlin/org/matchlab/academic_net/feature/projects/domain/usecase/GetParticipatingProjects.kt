package org.matchlab.academic_net.feature.projects.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.feature.projects.domain.ParticipationSource
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.utils.suspendRunCatching

/** How finding the projects the viewer takes part in ended. */
sealed interface ParticipatingProjectsResult {
    /** The projects, in the order of the viewer's records; may be empty. */
    data class Loaded(val projects: List<Project>) : ParticipatingProjectsResult

    /** A request failed (no network, a server error); asking again may help. */
    data class Failure(val cause: Throwable) : ParticipatingProjectsResult
}

/**
 * 06b "Я участник" (API-GAP G67): `joinedProjectIds`, then `repository.loadProject` of each (in parallel, afresh: the
 * membership may have changed), in the order of the ids. Kept: not `null`, `viewerIsMember`, not `viewerIsInitiator`. Any
 * failure is [ParticipatingProjectsResult.Failure].
 */
class GetParticipatingProjectsUseCase(
    private val source: ParticipationSource,
    private val repository: ProjectsRepository,
) {
    suspend operator fun invoke(): ParticipatingProjectsResult =
        suspendRunCatching {
            val ids = source.joinedProjectIds()
            coroutineScope { ids.map { id -> async { repository.loadProject(id) } }.awaitAll() }
                .filterNotNull()
                .filter { it.viewerIsMember && !it.viewerIsInitiator }
        }.fold(
            onSuccess = { ParticipatingProjectsResult.Loaded(it) },
            onFailure = { ParticipatingProjectsResult.Failure(it) },
        )
}
