package org.matchlab.academic_net.feature.projects.domain.usecase

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository

/** "Просматривать свои проекты": projects the user has initiated, newest first. */
class ObserveMyProjectsUseCase(private val repository: ProjectsRepository) {
    operator fun invoke(): Flow<List<Project>> = repository.observeMyProjects()
}

/** One project with its roles (the "roles" and "management" screens); `null` if it is gone. */
class ObserveProjectUseCase(private val repository: ProjectsRepository) {
    operator fun invoke(id: ProjectId): Flow<Project?> = repository.observeProject(id)
}
