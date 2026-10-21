package org.matchlab.academic_net.feature.projects.domain.usecase

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * "Просматривать роли" (07): reads one project from the server. Its value also comes through
 * [ObserveProjectUseCase]; a cancellation of the caller is rethrown, not a failure.
 */
class LoadProjectUseCase(private val repository: ProjectsRepository) {
    suspend operator fun invoke(id: ProjectId): LoadResult<Project> =
        suspendRunCatching { repository.loadProject(id) }.fold(
            onSuccess = { project -> if (project == null) LoadResult.NotFound else LoadResult.Found(project) },
            onFailure = { LoadResult.Failure(it) },
        )
}
