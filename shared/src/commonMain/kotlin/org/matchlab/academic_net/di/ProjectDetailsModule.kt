package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.feature.projectdetails.data.ProjectDetailsDataSource
import org.matchlab.academic_net.feature.projectdetails.data.ProjectDetailsRepositoryImpl
import org.matchlab.academic_net.feature.projectdetails.domain.GetProjectDetailsUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetailsRepository
import org.matchlab.academic_net.feature.projectdetails.ui.ProjectDetailsUiDependencies

/** The project details feature: its repository, use case and what its screen receives. */
internal interface ProjectDetailsModule {
    @Provides
    @AppScope
    fun projectDetailsRepository(source: ProjectDetailsDataSource): ProjectDetailsRepository =
        ProjectDetailsRepositoryImpl(source)

    @Provides
    fun projectDetailsUiDependencies(repository: ProjectDetailsRepository): ProjectDetailsUiDependencies =
        ProjectDetailsUiDependencies(getProjectDetails = GetProjectDetailsUseCase(repository))
}
