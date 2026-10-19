package org.matchlab.academic_net.feature.projects.domain.usecase

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository

/**
 * "Просматривать свои проекты": projects the user has initiated, the ones created in this session first and then in the
 * server's order. The first collection loads the list, and its failure is thrown to the collector (the screen shows
 * "Повторить" and collects again).
 *
 * Without [org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities.canListMine] the screen does not collect
 * this flow, and collecting it anyway fails with [org.matchlab.academic_net.common.result.FeatureUnavailableException].
 */
class ObserveMyProjectsUseCase(private val repository: ProjectsRepository) {
    operator fun invoke(): Flow<List<Project>> = repository.observeMyProjects()
}

/**
 * One project with its roles (the "roles" and "management" screens), as last read in this session. `null` means the
 * project has not been read yet (see [LoadProjectUseCase]) or the server said there is no such project; the flow never
 * fails.
 */
class ObserveProjectUseCase(private val repository: ProjectsRepository) {
    operator fun invoke(id: ProjectId): Flow<Project?> = repository.observeProject(id)
}
