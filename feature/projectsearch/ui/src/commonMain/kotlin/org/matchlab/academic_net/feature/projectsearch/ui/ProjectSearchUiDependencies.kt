package org.matchlab.academic_net.feature.projectsearch.ui

import org.matchlab.academic_net.feature.projectsearch.domain.GetSearchSpheresUseCase
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectSearchCapabilities
import org.matchlab.academic_net.feature.projectsearch.domain.SearchProjectsUseCase

/**
 * What the screens of the project-search feature need from the outside; the app creates it once
 * and hands it to [ProjectSearchRoute].
 *
 * @param capabilities what the source can filter by; the filters sheet offers only that (the "Тип проекта" and
 *  "Сроки" groups of 03 are shown only when `typeFilter` and `durationFilter` are on, see `docs/api-values.md`)
 */
class ProjectSearchUiDependencies(
    val searchProjects: SearchProjectsUseCase,
    val getSpheres: GetSearchSpheresUseCase,
    val capabilities: ProjectSearchCapabilities,
)
