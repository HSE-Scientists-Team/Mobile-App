package org.matchlab.academic_net

import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirement
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirementProvider
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository

/**
 * Tells the collaborators feature what a role of a project asks for. The two features do not
 * know each other: this adapter, living in the app, is the only place that connects them.
 */
internal class ProjectsRoleRequirementProvider(
    private val projects: ProjectsRepository,
) : RoleRequirementProvider {
    override suspend fun getRoleRequirement(projectId: ProjectId, roleId: RoleId): RoleRequirement? {
        val project = projects.getProject(projectId) ?: return null
        val role = project.roles.firstOrNull { it.id == roleId } ?: return null
        return RoleRequirement(project.title, role.title, role.skills)
    }
}
