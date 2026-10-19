package org.matchlab.academic_net.feature.projectdetails.domain

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId

/** The server refused to let the viewer leave the project (403 or 409; API-GAP G65). */
class LeaveNotAllowedException(val projectId: ProjectId) : IllegalStateException("Cannot leave ${projectId.value}")

/** The project is gone for the viewer (404). */
class ProjectGoneException(val projectId: ProjectId) : NoSuchElementException("Project ${projectId.value} is gone")

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

    /**
     * "Покинуть проект": `DELETE /projects/{id}/membership`. The page is read again by the caller.
     * @throws LeaveNotAllowedException 403 or 409 (API-GAP G65)
     * @throws ProjectGoneException 404
     */
    suspend fun leaveProject(id: ProjectId)
}
