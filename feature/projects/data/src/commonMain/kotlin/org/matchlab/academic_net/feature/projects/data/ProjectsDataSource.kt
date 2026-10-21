package org.matchlab.academic_net.feature.projects.data

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.projects.domain.MemberNotRemovableException
import org.matchlab.academic_net.feature.projects.domain.NewProject
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.domain.ProjectInfo
import org.matchlab.academic_net.feature.projects.domain.ProjectLifecycleAction
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.feature.projects.domain.RoleNotRemovableException

/**
 * Where the viewer's projects come from: the Gateway ([GatewayProjectsDataSource]). Mirrors [ProjectsRepository]: the
 * same members, the same meaning; `createProject` is what the repository calls `publishProject`.
 */
interface ProjectsDataSource {
    /** What the server behind this source can do; the screens hide the rest. */
    val capabilities: ProjectsCapabilities

    /**
     * The viewer's projects: the ones created in this session first, then the server's order. Collecting it loads the list on
     * first use (a failure of that is thrown to the collector, which asks again by collecting again), and it follows the
     * changes of the projects made in this session. Fails with `FeatureUnavailableException` unless
     * [ProjectsCapabilities.canListMine].
     */
    fun observeMyProjects(): Flow<List<Project>>

    /** The project as last read or changed in this session; `null` until it is read and after the server denies it. */
    fun observeProject(id: ProjectId): Flow<Project?>

    /** Reads the project from the server into [observeProject]; `null` when the viewer may not see such a project. */
    suspend fun loadProject(id: ProjectId): Project?

    /** The project as last read in this session, else as [loadProject] reads it. */
    suspend fun getProject(id: ProjectId): Project?

    /** Creates the project already published. Only with [ProjectsCapabilities.canCreate]. */
    suspend fun createProject(project: NewProject): Project

    /**
     * Hides the project from search. Only with [ProjectsCapabilities.canUnpublish].
     * @throws ProjectNotFoundException the project is gone (404)
     * @throws ProjectConflictException the project changed meanwhile (409); it has been read again
     */
    suspend fun unpublishProject(id: ProjectId): Project

    /** Adds a vacant role for one person to the project. */
    suspend fun addRole(projectId: ProjectId, role: RoleDraft): Role

    /**
     * Moves the project with the cached revision. Only with [ProjectsCapabilities.canChangeLifecycle].
     * @throws ProjectNotFoundException the project is gone (404)
     * @throws ProjectConflictException the server refused (409); the project has been read again
     */
    suspend fun changeLifecycle(id: ProjectId, action: ProjectLifecycleAction): Project

    /**
     * Replaces the description of the project (without roles: API-GAP G63).
     * @throws ProjectNotFoundException the project is gone (404)
     * @throws ProjectConflictException the project changed meanwhile (409); it has been read again
     */
    suspend fun updateProject(id: ProjectId, info: ProjectInfo): Project

    /**
     * Changes the title and the skills of the role; its places and description stay as the server has them.
     * @throws ProjectNotFoundException the project is gone
     * @throws ProjectConflictException 409, or the role is no longer there; the project has been read again
     * @throws org.matchlab.academic_net.common.result.RemoteFailure.Malformed the answer has no edited role (the project in
     *  the cache has been updated already)
     */
    suspend fun updateRole(projectId: ProjectId, roleId: RoleId, role: RoleDraft): Role

    /**
     * Deletes the role and returns the project; a role that is already gone (404, not in the project read again) counts as deleted.
     * @throws ProjectNotFoundException the project is gone
     * @throws ProjectConflictException 404 while the role is still in the project that was read again
     * @throws RoleNotRemovableException 403 or 409 (API-GAP G64); the project has been read again
     */
    suspend fun removeRole(projectId: ProjectId, roleId: RoleId): Project

    /**
     * Removes the member and returns the project; a 404 reads the project again: gone is [ProjectNotFoundException], still
     * there counts as removed (that project is returned).
     * @throws MemberNotRemovableException 403 or 409 (API-GAP G65); the project has been read again
     */
    suspend fun removeMember(projectId: ProjectId, userId: UserId): Project

    /**
     * The projects the viewer takes part in without owning them (06b "Я участник"): the server's list (`mine=participant`),
     * read afresh on every call, each project once and with its initiator. The projects are cached, so that each can be
     * opened with [getProject]. Throws the failure of a request.
     */
    suspend fun getParticipatingProjects(): List<Project>

    /** The spheres a project can belong to: the app's own list, `Spheres.all`. */
    suspend fun getSpheres(): List<Sphere>
}
