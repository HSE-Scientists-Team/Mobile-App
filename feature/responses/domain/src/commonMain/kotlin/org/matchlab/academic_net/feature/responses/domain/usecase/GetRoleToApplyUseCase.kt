package org.matchlab.academic_net.feature.responses.domain.usecase

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.feature.responses.domain.ResponsesProjectSource
import org.matchlab.academic_net.utils.suspendRunCatching

/** The role of the sheet 04s with the project it belongs to. */
data class RoleToApply(val project: Project, val role: Role)

/** 04s: the project and the role to apply to. NotFound: the project is `null` (the viewer may not see it) or has no such role. */
class GetRoleToApplyUseCase(private val projects: ResponsesProjectSource) {
    suspend operator fun invoke(projectId: ProjectId, roleId: RoleId): LoadResult<RoleToApply> {
        val project = suspendRunCatching { projects.getProject(projectId) }
            .getOrElse { return LoadResult.Failure(it) }
            ?: return LoadResult.NotFound
        val role = project.roles.firstOrNull { it.id == roleId } ?: return LoadResult.NotFound
        return LoadResult.Found(RoleToApply(project, role))
    }
}
