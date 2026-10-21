package org.matchlab.academic_net.feature.projectdetails.data

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.feature.projectdetails.domain.LeaveNotAllowedException
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetails
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectGoneException
import org.matchlab.academic_net.feature.projectdetails.domain.TeamMember

/**
 * Where the project pages come from: the Gateway ([GatewayProjectDetailsDataSource]). The server decides what the viewer
 * may see.
 */
interface ProjectDetailsDataSource {
    /** The project with [id], or `null` when the viewer may not see such a project. */
    suspend fun getProjectDetails(id: ProjectId): ProjectDetails?

    /** The team of [project] with their profiles, in the server's order; never fails for one person. */
    suspend fun getTeam(project: Project): List<TeamMember>

    /**
     * Lets the viewer leave the project.
     * @throws LeaveNotAllowedException 403 or 409 (API-GAP G65)
     * @throws ProjectGoneException 404
     */
    suspend fun leaveProject(id: ProjectId)
}
