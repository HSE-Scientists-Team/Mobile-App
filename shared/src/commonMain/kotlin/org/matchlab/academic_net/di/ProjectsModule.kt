package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.feature.projects.data.ProjectsDataSource
import org.matchlab.academic_net.feature.projects.data.ProjectsRepositoryImpl
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.usecase.AddRoleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.GetSpheresUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.LoadProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveMyProjectsUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.PublishProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UnpublishProjectUseCase
import org.matchlab.academic_net.feature.projects.ui.ProjectsUiDependencies

/** The projects feature: its repository, use cases and what its screens receive. */
internal interface ProjectsModule {
    @Provides
    @SessionScope
    fun projectsRepository(source: ProjectsDataSource): ProjectsRepository = ProjectsRepositoryImpl(source)

    @Provides
    fun projectsUiDependencies(repository: ProjectsRepository): ProjectsUiDependencies = ProjectsUiDependencies(
        observeMyProjects = ObserveMyProjectsUseCase(repository),
        observeProject = ObserveProjectUseCase(repository),
        loadProject = LoadProjectUseCase(repository),
        publishProject = PublishProjectUseCase(repository),
        unpublishProject = UnpublishProjectUseCase(repository),
        addRole = AddRoleUseCase(repository),
        getSpheres = GetSpheresUseCase(repository),
        capabilities = repository.capabilities,
    )
}
