package org.matchlab.academic_net.feature.projects.domain

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Sphere

/** Requested project does not exist (or the user is not allowed to see it). */
class ProjectNotFoundException(val projectId: ProjectId) :
    NoSuchElementException("Project ${projectId.value} not found")

/**
 * The server refused a change because the project had changed since it was read (a 409). By the time this is thrown
 * the project has been read again where possible, so [ProjectsRepository.observeProject] shows what the server has:
 * the user checks and tries again.
 */
class ProjectConflictException(val projectId: ProjectId) :
    IllegalStateException("Project ${projectId.value} changed on the server")

/** The server refused to delete the role: it has people on it or is otherwise not free (a 403 or 409; API-GAP G64). */
class RoleNotRemovableException(val projectId: ProjectId, val roleId: RoleId) :
    IllegalStateException("Role ${roleId.value} of project ${projectId.value} cannot be removed")

/**
 * Projects of the current user as their initiator. Suspending functions throw on failure (no network, a server
 * error, [org.matchlab.academic_net.common.result.FeatureUnavailableException] for what [capabilities] switch off);
 * the use cases turn that into results.
 */
interface ProjectsRepository {
    /** What the server behind the projects can do; the screens hide the rest. */
    val capabilities: ProjectsCapabilities

    /**
     * The viewer's projects: the ones created in this session first, then the server's order. Collecting it loads the list
     * on first use, and a failure of that load is thrown to the collector, which asks again by collecting again; after
     * that the list follows the changes made in this session (a project created, taken off publication, given a role).
     * Fails with `FeatureUnavailableException` unless [ProjectsCapabilities.canListMine].
     */
    fun observeMyProjects(): Flow<List<Project>>

    /**
     * The project as last read or changed in this session: `null` until [loadProject] has read it, and after the
     * server said there is no such project. Never fails.
     */
    fun observeProject(id: ProjectId): Flow<Project?>

    /** Reads the project from the server into [observeProject]; `null` when the viewer may not see such a project. */
    suspend fun loadProject(id: ProjectId): Project?

    /** The project as last read in this session, else as [loadProject] reads it. */
    suspend fun getProject(id: ProjectId): Project?

    /** Creates the project already published. Only with [ProjectsCapabilities.canCreate]. */
    suspend fun publishProject(project: NewProject): Project

    /**
     * Hides the project from search. Only with [ProjectsCapabilities.canUnpublish].
     * @throws ProjectNotFoundException the project is gone (404)
     * @throws ProjectConflictException the project changed meanwhile (409); it has been read again
     */
    suspend fun unpublishProject(id: ProjectId): Project

    /**
     * Adds a vacant role for one person to the project (API-GAP G49).
     * @throws ProjectNotFoundException the project is gone (404)
     * @throws ProjectConflictException the project changed meanwhile (409); it has been read again
     */
    suspend fun addRole(projectId: ProjectId, role: RoleDraft): Role

    /**
     * Moves the project ([ProjectLifecycleAction] to the action "3", "4", "5") with the cached revision. Only with
     * [ProjectsCapabilities.canChangeLifecycle].
     * @throws ProjectNotFoundException the project is gone (404)
     * @throws ProjectConflictException the server refused (409): stale revision or a move not allowed now; read again
     */
    suspend fun changeLifecycle(id: ProjectId, action: ProjectLifecycleAction): Project

    /**
     * Replaces the description of the project (`PUT`, without roles: API-GAP G63).
     * @throws ProjectNotFoundException the project is gone (404)
     * @throws ProjectConflictException the project changed meanwhile (409); it has been read again
     */
    suspend fun updateProject(id: ProjectId, info: ProjectInfo): Project

    /**
     * Changes the title and the skills of the role; its places and description stay as the server has them.
     * @throws ProjectNotFoundException the project is gone
     * @throws ProjectConflictException 409, or the role is no longer there (404 with the project still there); read again
     * @throws org.matchlab.academic_net.common.result.RemoteFailure.Malformed the answer has no edited role (the project in
     *  the cache has been updated already)
     */
    suspend fun updateRole(projectId: ProjectId, roleId: RoleId, role: RoleDraft): Role

    /**
     * Deletes the role and returns the project. A role that is already gone (404 and the project read again has no such
     * role) counts as deleted: that project is returned.
     * @throws ProjectNotFoundException the project is gone
     * @throws ProjectConflictException 404 while the role is still in the project that was read again
     * @throws RoleNotRemovableException 403 or 409 (API-GAP G64); the project has been read again
     */
    suspend fun removeRole(projectId: ProjectId, roleId: RoleId): Project

    /** The spheres a project can belong to: the app's own list, `Spheres.all` (`docs/api-values.md`). */
    suspend fun getSpheres(): List<Sphere>
}
