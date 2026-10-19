package org.matchlab.academic_net.feature.projects.domain

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
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

    /** The spheres a project can belong to: the app's own list, `Spheres.all` (`docs/api-values.md`). */
    suspend fun getSpheres(): List<Sphere>
}
