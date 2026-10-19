package org.matchlab.academic_net.feature.projectdetails.ui

import org.matchlab.academic_net.feature.projectdetails.domain.GetProjectDetailsUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.GetProjectTeamUseCase

/**
 * What the screens of the project-details feature need from the outside; the app creates it once
 * and hands it to [ProjectDetailsRoute].
 *
 * @property getProjectTeam reads the people of "Команда" after the page has been shown
 */
class ProjectDetailsUiDependencies(
    val getProjectDetails: GetProjectDetailsUseCase,
    val getProjectTeam: GetProjectTeamUseCase,
)
