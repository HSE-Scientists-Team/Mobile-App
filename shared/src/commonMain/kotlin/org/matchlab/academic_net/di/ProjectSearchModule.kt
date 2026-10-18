package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.ProjectsSphereProvider
import org.matchlab.academic_net.feature.projectsearch.data.ProjectSearchDataSource
import org.matchlab.academic_net.feature.projectsearch.data.ProjectSearchRepositoryImpl
import org.matchlab.academic_net.feature.projectsearch.domain.GetSearchSpheresUseCase
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectSearchRepository
import org.matchlab.academic_net.feature.projectsearch.domain.SearchProjectsUseCase
import org.matchlab.academic_net.feature.projectsearch.domain.SphereProvider
import org.matchlab.academic_net.feature.projectsearch.ui.ProjectSearchUiDependencies

/**
 * The project search feature. The spheres of the filter come from the projects feature through
 * [ProjectsSphereProvider], the one adapter connecting the two.
 */
internal interface ProjectSearchModule {
    @Provides
    fun sphereProvider(adapter: ProjectsSphereProvider): SphereProvider = adapter

    @Provides
    @SessionScope
    fun projectSearchRepository(source: ProjectSearchDataSource): ProjectSearchRepository =
        ProjectSearchRepositoryImpl(source)

    @Provides
    fun projectSearchUiDependencies(
        repository: ProjectSearchRepository,
        spheres: SphereProvider,
    ): ProjectSearchUiDependencies = ProjectSearchUiDependencies(
        searchProjects = SearchProjectsUseCase(repository),
        getSpheres = GetSearchSpheresUseCase(spheres),
    )
}
