package org.matchlab.academic_net.feature.projectdetails.domain

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId

/**
 * The page of one project. The server decides what the viewer may see; the call throws on failure
 * (e.g. no network) and the use case turns that into a result.
 */
interface ProjectDetailsRepository {
    /**
     * The project with the facts about its initiator, or `null` when the viewer may not see such
     * a project: there is no project with this [id] or it is not published.
     */
    suspend fun getProjectDetails(id: ProjectId): ProjectDetails?

    /**
     * The team of [project] (its `members`) with their profiles, in the server's order. Never throws a `RemoteFailure`:
     * a person whose profile could not be read is a member without a profile.
     */
    suspend fun getTeam(project: Project): List<TeamMember>
}
