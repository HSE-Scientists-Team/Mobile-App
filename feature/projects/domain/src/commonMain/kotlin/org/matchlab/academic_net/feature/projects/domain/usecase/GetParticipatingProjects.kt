package org.matchlab.academic_net.feature.projects.domain.usecase

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.utils.suspendRunCatching

/** How finding the projects the viewer takes part in ended. */
sealed interface ParticipatingProjectsResult {
    /** The projects, in the server's order; may be empty. */
    data class Loaded(val projects: List<Project>) : ParticipatingProjectsResult

    /** A request failed (no network, a server error); asking again may help. */
    data class Failure(val cause: Throwable) : ParticipatingProjectsResult
}

/**
 * 06b "Я участник": the projects the server lists as the viewer's participation ([ProjectsRepository.getParticipatingProjects],
 * read afresh: the membership may have changed). Any failure is [ParticipatingProjectsResult.Failure].
 */
class GetParticipatingProjectsUseCase(
    private val repository: ProjectsRepository,
) {
    suspend operator fun invoke(): ParticipatingProjectsResult =
        suspendRunCatching { repository.getParticipatingProjects() }.fold(
            onSuccess = { ParticipatingProjectsResult.Loaded(it) },
            onFailure = { ParticipatingProjectsResult.Failure(it) },
        )
}
