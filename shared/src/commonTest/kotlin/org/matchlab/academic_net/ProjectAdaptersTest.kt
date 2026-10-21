package org.matchlab.academic_net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirement
import org.matchlab.academic_net.feature.projects.domain.NewProject
import org.matchlab.academic_net.feature.projects.domain.ProjectInfo
import org.matchlab.academic_net.feature.projects.domain.ProjectLifecycleAction
import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.RoleDraft

/** The two adapters that connect the features to each other, on a fake repository of the projects. */
class ProjectAdaptersTest {
    private class FakeRepository : ProjectsRepository {
        override val capabilities = ProjectsCapabilities(canCreate = false, canListMine = false, canUnpublish = false)
        val projects = mutableMapOf<ProjectId, Project>()
        val asked = mutableListOf<ProjectId>()
        var spheres: List<Sphere> = emptyList()
        var failure: Throwable? = null

        val loads = mutableListOf<ProjectId>()
        var loadFailure: Throwable? = null
        var mine: Flow<List<Project>> = emptyFlow()
        var observed: Flow<Project?> = emptyFlow()

        override fun observeMyProjects(): Flow<List<Project>> = mine
        override fun observeProject(id: ProjectId): Flow<Project?> = observed
        override suspend fun loadProject(id: ProjectId): Project? {
            loads += id
            loadFailure?.let { throw it }
            return projects[id]
        }
        override suspend fun removeMember(projectId: ProjectId, userId: UserId): Project = throw AssertionError()
        override suspend fun getProject(id: ProjectId): Project? {
            asked += id
            failure?.let { throw it }
            return projects[id]
        }
        override suspend fun publishProject(project: NewProject): Project = throw AssertionError()
        override suspend fun unpublishProject(id: ProjectId): Project = throw AssertionError()
        override suspend fun addRole(projectId: ProjectId, role: RoleDraft): Role = throw AssertionError()
        override suspend fun changeLifecycle(id: ProjectId, action: ProjectLifecycleAction): Project = throw AssertionError()
        override suspend fun updateProject(id: ProjectId, info: ProjectInfo): Project = throw AssertionError()
        override suspend fun updateRole(projectId: ProjectId, roleId: RoleId, role: RoleDraft): Role = throw AssertionError()
        override suspend fun removeRole(projectId: ProjectId, roleId: RoleId): Project = throw AssertionError()
        override suspend fun getParticipatingProjects(): List<Project> = throw AssertionError()
        override suspend fun getSpheres(): List<Sphere> {
            failure?.let { throw it }
            return spheres
        }
    }

    private val repository = FakeRepository()

    private fun project(vararg roles: Role) = Project(
        id = ProjectId("p1"),
        title = "AI-навигация",
        type = null,
        sphere = Sphere("Biology", "Biology"),
        problem = "P",
        goal = "G",
        tags = emptyList(),
        initiator = UserSummary(UserId("u"), "U"),
        roles = roles.toList(),
        status = null,
        period = null,
        viewerIsInitiator = true,
    )

    // ---- ProjectsRoleRequirementProvider ----

    @Test
    fun theRequirementOfARoleIsTheTitlesAndTheSkills() = runTest {
        repository.projects[ProjectId("p1")] = project(
            Role(RoleId("r1"), "ML Engineer", skills = listOf("Python", "PyTorch")),
            Role(RoleId("r2"), "Дизайнер", skills = listOf("Figma")),
        )

        val requirement = ProjectsRoleRequirementProvider(repository).getRoleRequirement(ProjectId("p1"), RoleId("r2"))

        assertEquals(RoleRequirement("AI-навигация", "Дизайнер", listOf("Figma")), requirement)
        assertEquals(listOf(ProjectId("p1")), repository.asked)
    }

    @Test
    fun aRoleWithoutSkillsHasAnEmptyRequirement() = runTest {
        repository.projects[ProjectId("p1")] = project(Role(RoleId("r1"), "Тестировщик"))

        val requirement = ProjectsRoleRequirementProvider(repository).getRoleRequirement(ProjectId("p1"), RoleId("r1"))

        assertEquals(emptyList(), requirement?.skills)
    }

    @Test
    fun aProjectTheViewerCannotSeeHasNoRequirement() = runTest {
        assertNull(ProjectsRoleRequirementProvider(repository).getRoleRequirement(ProjectId("missing"), RoleId("r1")))
    }

    @Test
    fun aRoleThatIsNotInTheProjectHasNoRequirement() = runTest {
        repository.projects[ProjectId("p1")] = project(Role(RoleId("r1"), "ML Engineer"))

        assertNull(ProjectsRoleRequirementProvider(repository).getRoleRequirement(ProjectId("p1"), RoleId("r9")))
    }

    @Test
    fun aFailureOfTheRepositoryIsThrownAndNotTurnedIntoNoRequirement() = runTest {
        val failure = IllegalStateException("offline")
        repository.failure = failure

        val thrown = assertFailsWith<IllegalStateException> {
            ProjectsRoleRequirementProvider(repository).getRoleRequirement(ProjectId("p1"), RoleId("r1"))
        }

        assertSame(failure, thrown)
    }

    // ---- ProjectsSphereProvider ----

    @Test
    fun theSpheresAreThoseOfTheProjectsFeatureInTheirOrder() = runTest {
        repository.spheres = listOf(Sphere("b", "Б"), Sphere("a", "А"))

        assertEquals(repository.spheres, ProjectsSphereProvider(repository).getSpheres())
    }

    @Test
    fun aFailureOfTheSpheresIsThrown() = runTest {
        val failure = IllegalStateException("offline")
        repository.failure = failure

        assertSame(failure, assertFailsWith<IllegalStateException> { ProjectsSphereProvider(repository).getSpheres() })
    }

    // ---- ProjectsResponsesProjectSource ----

    @Test
    fun theResponsesReadProjectsThroughTheCacheOfTheProjectsFeature() = runTest {
        repository.projects[ProjectId("p1")] = project()
        val source = ProjectsResponsesProjectSource(repository)

        assertEquals(ProjectId("p1"), source.getProject(ProjectId("p1"))?.id)
        assertNull(source.getProject(ProjectId("missing")))
        assertEquals(emptyList(), repository.loads, "getProject does not read again")
    }

    @Test
    fun refreshingAProjectReadsItAgainAndNeverThrowsItsFailure() = runTest {
        val source = ProjectsResponsesProjectSource(repository)

        source.refreshProject(ProjectId("p1"))
        repository.loadFailure = IllegalStateException("offline")
        source.refreshProject(ProjectId("p2"))

        assertEquals(listOf(ProjectId("p1"), ProjectId("p2")), repository.loads)
    }

    @Test
    fun aCancellationWhileRefreshingAProjectIsNotSwallowed() = runTest {
        repository.loadFailure = kotlinx.coroutines.CancellationException("cancelled")

        assertFailsWith<kotlinx.coroutines.CancellationException> {
            ProjectsResponsesProjectSource(repository).refreshProject(ProjectId("p1"))
        }
    }

    @Test
    fun theViewersProjectsAreTheFirstValueOfTheListAndItsFailureIsThrown() = runTest {
        repository.mine = kotlinx.coroutines.flow.flowOf(listOf(project()))
        assertEquals(listOf(ProjectId("p1")), ProjectsResponsesProjectSource(repository).getMyProjects().map { it.id })

        val unavailable = org.matchlab.academic_net.common.result.FeatureUnavailableException("G44")
        repository.mine = kotlinx.coroutines.flow.flow { throw unavailable }
        assertSame(unavailable, assertFailsWith<org.matchlab.academic_net.common.result.FeatureUnavailableException> {
            ProjectsResponsesProjectSource(repository).getMyProjects()
        })
    }

    @Test
    fun theProjectIsObservedFromTheProjectsFeature() = runTest {
        repository.observed = kotlinx.coroutines.flow.flowOf(project())

        assertEquals(ProjectId("p1"), ProjectsResponsesProjectSource(repository).observeProject(ProjectId("p1")).first()?.id)
    }
}
