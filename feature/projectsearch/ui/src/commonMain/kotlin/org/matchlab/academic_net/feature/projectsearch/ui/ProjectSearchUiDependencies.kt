package org.matchlab.academic_net.feature.projectsearch.ui

import org.matchlab.academic_net.feature.projectsearch.domain.GetSearchSpheresUseCase
import org.matchlab.academic_net.feature.projectsearch.domain.SearchProjectsUseCase

/**
 * What the screens of the project-search feature need from the outside; the app creates it once
 * and hands it to [ProjectSearchRoute].
 */
class ProjectSearchUiDependencies(
    val searchProjects: SearchProjectsUseCase,
    val getSpheres: GetSearchSpheresUseCase,
)
