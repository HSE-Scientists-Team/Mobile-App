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
 * Projects of the current user as their initiator. Suspending functions throw on failure
 * (e.g. no network); the use cases turn that into results.
 */
interface ProjectsRepository {
    fun observeMyProjects(): Flow<List<Project>>

    /** Emits `null` if there is no such project. */
    fun observeProject(id: ProjectId): Flow<Project?>

    suspend fun getProject(id: ProjectId): Project?

    /** Creates the project already published ([org.matchlab.academic_net.common.model.ProjectStatus.Recruiting]). */
    suspend fun publishProject(project: NewProject): Project

    /** Hides the project from search. @throws ProjectNotFoundException */
    suspend fun unpublishProject(id: ProjectId): Project

    /** Adds a vacant role to the project. @throws ProjectNotFoundException */
    suspend fun addRole(projectId: ProjectId, role: RoleDraft): Role

    suspend fun getSpheres(): List<Sphere>
}
