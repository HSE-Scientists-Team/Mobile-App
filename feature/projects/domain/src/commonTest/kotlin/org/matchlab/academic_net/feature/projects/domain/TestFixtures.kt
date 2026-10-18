package org.matchlab.academic_net.feature.projects.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.matchlab.academic_net.common.model.Project
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary

val TestSphere = Sphere("cs", "Computer Science")
val TestUser = UserSummary(UserId("me"), "Вы")

fun validDraft() = ProjectDraft(
    title = "AI-навигация по кампусу",
    type = ProjectType.Scientific,
    sphere = TestSphere,
    problem = "GPS не работает внутри зданий",
    goal = "Строить маршруты внутри корпусов",
    start = YearMonth(2026, 10),
    end = YearMonth(2027, 3),
)

fun testProject(
    id: String = "p1",
    status: ProjectStatus? = ProjectStatus.Recruiting,
    roles: List<Role> = emptyList(),
) = Project(
    id = ProjectId(id),
    title = "Project $id",
    type = ProjectType.Scientific,
    sphere = TestSphere,
    problem = "problem",
    goal = "goal",
    tags = emptyList(),
    initiator = TestUser,
    roles = roles,
    status = status,
    period = ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)),
    viewerIsInitiator = true,
)

/** Repository that keeps projects in memory and can be told to fail. */
class FakeProjectsRepository(
    initial: List<Project> = emptyList(),
    override val capabilities: ProjectsCapabilities = ProjectsCapabilities(
        canCreate = true,
        canListMine = true,
        canUnpublish = true,
    ),
) : ProjectsRepository {
    val projects = MutableStateFlow(initial)
    var failure: Throwable? = null
    var published: NewProject? = null
        private set

    /** The ids [loadProject] was asked for, in order. */
    val loaded = mutableListOf<ProjectId>()

    private fun failIfNeeded() {
        failure?.let { throw it }
    }

    override fun observeMyProjects(): Flow<List<Project>> = projects

    override fun observeProject(id: ProjectId): Flow<Project?> =
        projects.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun loadProject(id: ProjectId): Project? {
        loaded += id
        failIfNeeded()
        return projects.value.firstOrNull { it.id == id }
    }

    override suspend fun getProject(id: ProjectId): Project? {
        failIfNeeded()
        return projects.value.firstOrNull { it.id == id }
    }

    override suspend fun publishProject(project: NewProject): Project {
        failIfNeeded()
        published = project
        val created = testProject(id = "new").copy(
            title = project.title,
            type = project.type,
            sphere = project.sphere,
            problem = project.problem,
            goal = project.goal,
            tags = project.tags,
            period = project.period,
            roles = project.roles.mapIndexed { index, role -> Role(RoleId("r$index"), role.title, role.skills) },
        )
        projects.value = listOf(created) + projects.value
        return created
    }

    override suspend fun unpublishProject(id: ProjectId): Project {
        failIfNeeded()
        val current = projects.value.firstOrNull { it.id == id } ?: throw ProjectNotFoundException(id)
        val updated = current.copy(status = ProjectStatus.Unpublished)
        projects.value = projects.value.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun addRole(projectId: ProjectId, role: RoleDraft): Role {
        failIfNeeded()
        val current = projects.value.firstOrNull { it.id == projectId } ?: throw ProjectNotFoundException(projectId)
        val added = Role(RoleId("r${current.roles.size}"), role.title, role.skills)
        projects.value = projects.value.map { if (it.id == projectId) it.copy(roles = it.roles + added) else it }
        return added
    }

    override suspend fun getSpheres(): List<Sphere> {
        failIfNeeded()
        return listOf(TestSphere)
    }
}
