package org.matchlab.academic_net

import org.matchlab.academic_net.feature.projects.data.ProjectsRepositoryImpl
import org.matchlab.academic_net.feature.projects.data.mock.MockProjectsDataSource
import org.matchlab.academic_net.feature.projects.domain.usecase.AddRoleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.GetSpheresUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveMyProjectsUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.PublishProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UnpublishProjectUseCase
import org.matchlab.academic_net.feature.projects.ui.ProjectsUiDependencies

/**
 * Composition root: the only place that knows which data source stands behind each repository.
 * It outlives activities, so the mock data keeps what the user has changed on rotation.
 *
 * When the backend appears, the mock sources are replaced here and nowhere else.
 */
internal object AppContainer {
    private val projectsRepository by lazy { ProjectsRepositoryImpl(MockProjectsDataSource()) }

    val projectsUi: ProjectsUiDependencies by lazy {
        ProjectsUiDependencies(
            observeMyProjects = ObserveMyProjectsUseCase(projectsRepository),
            observeProject = ObserveProjectUseCase(projectsRepository),
            publishProject = PublishProjectUseCase(projectsRepository),
            unpublishProject = UnpublishProjectUseCase(projectsRepository),
            addRole = AddRoleUseCase(projectsRepository),
            getSpheres = GetSpheresUseCase(projectsRepository),
        )
    }
}
