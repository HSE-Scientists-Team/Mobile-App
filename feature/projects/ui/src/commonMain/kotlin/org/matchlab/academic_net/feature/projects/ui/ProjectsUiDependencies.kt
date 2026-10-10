package org.matchlab.academic_net.feature.projects.ui

import org.matchlab.academic_net.feature.projects.domain.usecase.AddRoleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.GetSpheresUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveMyProjectsUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.PublishProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UnpublishProjectUseCase

/**
 * What the screens of the projects feature need from the outside; the app creates it once and
 * hands it to every `…Route`.
 */
class ProjectsUiDependencies(
    val observeMyProjects: ObserveMyProjectsUseCase,
    val observeProject: ObserveProjectUseCase,
    val publishProject: PublishProjectUseCase,
    val unpublishProject: UnpublishProjectUseCase,
    val addRole: AddRoleUseCase,
    val getSpheres: GetSpheresUseCase,
)
