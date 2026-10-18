package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.ProjectsRoleRequirementProvider
import org.matchlab.academic_net.feature.collaborators.data.CollaboratorsDataSource
import org.matchlab.academic_net.feature.collaborators.data.CollaboratorsRepositoryImpl
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorsRepository
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirementProvider
import org.matchlab.academic_net.feature.collaborators.domain.usecase.GetRoleRequirementUseCase
import org.matchlab.academic_net.feature.collaborators.domain.usecase.SearchCollaboratorsUseCase
import org.matchlab.academic_net.feature.collaborators.ui.CollaboratorsUiDependencies

/**
 * The collaborators feature. What a role asks for comes from the projects feature through
 * [ProjectsRoleRequirementProvider], the one adapter connecting the two.
 */
internal interface CollaboratorsModule {
    @Provides
    fun roleRequirementProvider(adapter: ProjectsRoleRequirementProvider): RoleRequirementProvider = adapter

    @Provides
    @SessionScope
    fun collaboratorsRepository(source: CollaboratorsDataSource): CollaboratorsRepository =
        CollaboratorsRepositoryImpl(source)

    @Provides
    fun collaboratorsUiDependencies(
        repository: CollaboratorsRepository,
        requirements: RoleRequirementProvider,
    ): CollaboratorsUiDependencies = CollaboratorsUiDependencies(
        getRoleRequirement = GetRoleRequirementUseCase(requirements),
        searchCollaborators = SearchCollaboratorsUseCase(repository),
    )
}
