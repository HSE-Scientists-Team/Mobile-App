package org.matchlab.academic_net.feature.projectdetails.domain

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.RoleMember
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * "Команда" of 04: the members of [Project.members] other than the project's initiator (by user id), each (person, role)
 * once. Without such members it returns an empty list and asks for nothing. Should the repository throw anyway (not a
 * cancellation), the members are returned without profiles, built from `project.members`.
 */
class GetProjectTeamUseCase(private val repository: ProjectDetailsRepository) {
    suspend operator fun invoke(project: Project): List<TeamMember> {
        val others = project.members
            .filter { it.userId != project.initiator.id }
            .distinctBy { it.userId to it.roleId }
        if (others.isEmpty()) return emptyList()

        return suspendRunCatching { repository.getTeam(project.copy(members = others)) }.getOrElse {
            others.map { member ->
                TeamMember(
                    person = RoleMember(UserSummary(member.userId, name = ""), hasProfile = false),
                    roleTitle = member.roleTitle,
                    isViewer = member.isViewer,
                )
            }
        }
    }
}
