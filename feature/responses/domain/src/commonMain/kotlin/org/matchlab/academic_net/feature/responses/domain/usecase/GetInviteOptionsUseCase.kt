package org.matchlab.academic_net.feature.responses.domain.usecase

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.result.FeatureUnavailableException
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.feature.responses.domain.ResponsesProjectSource
import org.matchlab.academic_net.utils.suspendRunCatching

/** A project of the sheet 09k with the roles one can be invited to. */
data class InviteProject(val project: Project, val roles: List<Role>)

/**
 * The options of 09k. `getMyProjects`: the viewer's (`viewerIsInitiator`) projects with status Recruiting or `null`
 * (unknown) and at least one vacant role, in their order, each with its vacant roles. A server that cannot list the
 * viewer's projects ([FeatureUnavailableException]) gives `Found(empty)`; the sheet then offers to create a project. There
 * is no NotFound.
 */
class GetInviteOptionsUseCase(private val projects: ResponsesProjectSource) {
    suspend operator fun invoke(): LoadResult<List<InviteProject>> {
        val mine = suspendRunCatching { projects.getMyProjects() }.getOrElse {
            return if (it is FeatureUnavailableException) LoadResult.Found(emptyList()) else LoadResult.Failure(it)
        }
        val options = mine.mapNotNull { project ->
            val open = project.status == null || project.status == ProjectStatus.Recruiting
            val roles = project.vacantRoles
            if (project.viewerIsInitiator && open && roles.isNotEmpty()) InviteProject(project, roles) else null
        }
        return LoadResult.Found(options)
    }
}
