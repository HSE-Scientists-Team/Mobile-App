package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.ResponsesAppliedRolesSource
import org.matchlab.academic_net.feature.projectdetails.data.ProjectDetailsDataSource
import org.matchlab.academic_net.feature.projectdetails.data.ProjectDetailsRepositoryImpl
import org.matchlab.academic_net.feature.projectdetails.domain.AppliedRolesSource
import org.matchlab.academic_net.feature.projectdetails.domain.GetProjectDetailsUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.GetProjectTeamUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.LeaveProjectUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.ObserveAppliedRolesUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetailsRepository
import org.matchlab.academic_net.feature.projectdetails.ui.ProjectDetailsUiDependencies

/**
 * The project details feature: its repository, use cases and what its screen receives. The roles the viewer has applied
 * to come from the responses feature through [ResponsesAppliedRolesSource], the one adapter connecting the two.
 */
internal interface ProjectDetailsModule {
    @Provides
    @SessionScope
    fun projectDetailsRepository(source: ProjectDetailsDataSource): ProjectDetailsRepository =
        ProjectDetailsRepositoryImpl(source)

    @Provides
    fun appliedRolesSource(adapter: ResponsesAppliedRolesSource): AppliedRolesSource = adapter

    @Provides
    fun projectDetailsUiDependencies(
        repository: ProjectDetailsRepository,
        appliedRoles: AppliedRolesSource,
    ): ProjectDetailsUiDependencies =
        ProjectDetailsUiDependencies(
            getProjectDetails = GetProjectDetailsUseCase(repository),
            getProjectTeam = GetProjectTeamUseCase(repository),
            observeAppliedRoles = ObserveAppliedRolesUseCase(appliedRoles),
            leaveProject = LeaveProjectUseCase(repository),
        )
}
