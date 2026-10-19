package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.ResponsesParticipationSource
import org.matchlab.academic_net.feature.projects.data.ProjectsDataSource
import org.matchlab.academic_net.feature.projects.data.ProjectsRepositoryImpl
import org.matchlab.academic_net.feature.projects.domain.ParticipationSource
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.usecase.AddRoleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ChangeProjectLifecycleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.GetParticipatingProjectsUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.GetSpheresUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.LoadProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveMyProjectsUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.PublishProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.RemoveMemberUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.RemoveRoleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UnpublishProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UpdateProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UpdateRoleUseCase
import org.matchlab.academic_net.feature.projects.ui.ProjectsUiDependencies

/**
 * The projects feature: its repository, use cases and what its screens receive. The projects the viewer joined (06b) come
 * from the responses feature through [ResponsesParticipationSource], the one adapter connecting the two.
 */
internal interface ProjectsModule {
    @Provides
    @SessionScope
    fun projectsRepository(source: ProjectsDataSource): ProjectsRepository = ProjectsRepositoryImpl(source)

    @Provides
    fun participationSource(adapter: ResponsesParticipationSource): ParticipationSource = adapter

    @Provides
    fun projectsUiDependencies(
        repository: ProjectsRepository,
        participation: ParticipationSource,
    ): ProjectsUiDependencies = ProjectsUiDependencies(
        observeMyProjects = ObserveMyProjectsUseCase(repository),
        observeProject = ObserveProjectUseCase(repository),
        loadProject = LoadProjectUseCase(repository),
        publishProject = PublishProjectUseCase(repository),
        unpublishProject = UnpublishProjectUseCase(repository),
        addRole = AddRoleUseCase(repository),
        getSpheres = GetSpheresUseCase(repository),
        changeLifecycle = ChangeProjectLifecycleUseCase(repository),
        updateProject = UpdateProjectUseCase(repository),
        updateRole = UpdateRoleUseCase(repository),
        removeRole = RemoveRoleUseCase(repository),
        removeMember = RemoveMemberUseCase(repository),
        getParticipatingProjects = GetParticipatingProjectsUseCase(participation, repository),
        capabilities = repository.capabilities,
    )
}
