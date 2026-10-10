package org.matchlab.academic_net.feature.projects.data

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.feature.projects.domain.NewProject
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.RoleDraft

class ProjectsRepositoryImpl(
    private val dataSource: ProjectsDataSource,
) : ProjectsRepository {
    override fun observeMyProjects(): Flow<List<Project>> = dataSource.observeMyProjects()

    override fun observeProject(id: ProjectId): Flow<Project?> = dataSource.observeProject(id)

    override suspend fun getProject(id: ProjectId): Project? = dataSource.getProject(id)

    override suspend fun publishProject(project: NewProject): Project = dataSource.createProject(project)

    override suspend fun unpublishProject(id: ProjectId): Project = dataSource.unpublishProject(id)

    override suspend fun addRole(projectId: ProjectId, role: RoleDraft): Role =
        dataSource.addRole(projectId, role)

    override suspend fun getSpheres(): List<Sphere> = dataSource.getSpheres()
}
