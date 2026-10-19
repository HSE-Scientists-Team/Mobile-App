package org.matchlab.academic_net.feature.projects.data

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.projects.domain.NewProject
import org.matchlab.academic_net.feature.projects.domain.ProjectInfo
import org.matchlab.academic_net.feature.projects.domain.ProjectLifecycleAction
import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.RoleDraft

/** [ProjectsRepository] over a [ProjectsDataSource]: passes everything to the source as it is; `publishProject` is `createProject`. */
class ProjectsRepositoryImpl(
    private val dataSource: ProjectsDataSource,
) : ProjectsRepository {
    override val capabilities: ProjectsCapabilities get() = dataSource.capabilities

    override fun observeMyProjects(): Flow<List<Project>> = dataSource.observeMyProjects()

    override fun observeProject(id: ProjectId): Flow<Project?> = dataSource.observeProject(id)

    override suspend fun loadProject(id: ProjectId): Project? = dataSource.loadProject(id)

    override suspend fun getProject(id: ProjectId): Project? = dataSource.getProject(id)

    override suspend fun publishProject(project: NewProject): Project = dataSource.createProject(project)

    override suspend fun unpublishProject(id: ProjectId): Project = dataSource.unpublishProject(id)

    override suspend fun addRole(projectId: ProjectId, role: RoleDraft): Role =
        dataSource.addRole(projectId, role)

    override suspend fun changeLifecycle(id: ProjectId, action: ProjectLifecycleAction): Project =
        dataSource.changeLifecycle(id, action)

    override suspend fun updateProject(id: ProjectId, info: ProjectInfo): Project = dataSource.updateProject(id, info)

    override suspend fun updateRole(projectId: ProjectId, roleId: RoleId, role: RoleDraft): Role =
        dataSource.updateRole(projectId, roleId, role)

    override suspend fun removeRole(projectId: ProjectId, roleId: RoleId): Project =
        dataSource.removeRole(projectId, roleId)

    override suspend fun removeMember(projectId: ProjectId, userId: UserId): Project =
        dataSource.removeMember(projectId, userId)

    override suspend fun getSpheres(): List<Sphere> = dataSource.getSpheres()
}
