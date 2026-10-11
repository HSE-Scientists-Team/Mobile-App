package org.matchlab.academic_net.feature.projectdetails.ui

import org.matchlab.academic_net.feature.projectdetails.domain.GetProjectDetailsUseCase

/**
 * What the screens of the project-details feature need from the outside; the app creates it once
 * and hands it to [ProjectDetailsRoute].
 */
class ProjectDetailsUiDependencies(
    val getProjectDetails: GetProjectDetailsUseCase,
)
