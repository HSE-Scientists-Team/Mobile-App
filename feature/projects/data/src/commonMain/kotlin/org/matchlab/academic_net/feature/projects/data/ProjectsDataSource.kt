package org.matchlab.academic_net.feature.projects.data

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.feature.projects.domain.NewProject
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.RoleDraft

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

    /** The spheres a project can belong to: the app's own list, `Spheres.all`. */
    suspend fun getSpheres(): List<Sphere>
}
