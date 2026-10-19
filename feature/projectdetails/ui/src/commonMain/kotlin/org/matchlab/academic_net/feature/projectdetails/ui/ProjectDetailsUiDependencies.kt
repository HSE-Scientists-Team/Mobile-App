package org.matchlab.academic_net.feature.projectdetails.ui

import org.matchlab.academic_net.feature.projectdetails.domain.GetProjectDetailsUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.GetProjectTeamUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.LeaveProjectUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.ObserveAppliedRolesUseCase

/**
 * What the screens of the project-details feature need from the outside; the app creates it once
 * and hands it to [ProjectDetailsRoute].
 *
 * @property getProjectTeam reads the people of "Команда" after the page has been shown
 * @property observeAppliedRoles the roles of the project the viewer has applied to: "Вы откликнулись"
 * @property leaveProject takes the viewer out of the project they are a member of
 */
class ProjectDetailsUiDependencies(
    val getProjectDetails: GetProjectDetailsUseCase,
    val getProjectTeam: GetProjectTeamUseCase,
    val observeAppliedRoles: ObserveAppliedRolesUseCase,
    val leaveProject: LeaveProjectUseCase,
)
