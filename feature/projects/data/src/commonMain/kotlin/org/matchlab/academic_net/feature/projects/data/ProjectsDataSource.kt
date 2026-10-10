package org.matchlab.academic_net.feature.projects.data

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.feature.projects.domain.NewProject
import org.matchlab.academic_net.feature.projects.domain.RoleDraft

/**
 * Where the projects of the current user come from. The only implementation is
 * [org.matchlab.academic_net.feature.projects.data.mock.MockProjectsDataSource]; a remote one
 * (with DTOs and their mappers) replaces it when the backend appears.
 */
interface ProjectsDataSource {
    fun observeMyProjects(): Flow<List<Project>>

    fun observeProject(id: ProjectId): Flow<Project?>

    suspend fun getProject(id: ProjectId): Project?

    suspend fun createProject(project: NewProject): Project

    suspend fun unpublishProject(id: ProjectId): Project

    suspend fun addRole(projectId: ProjectId, role: RoleDraft): Role

    suspend fun getSpheres(): List<Sphere>
}
