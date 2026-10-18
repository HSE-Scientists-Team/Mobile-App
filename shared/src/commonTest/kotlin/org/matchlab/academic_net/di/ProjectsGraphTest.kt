package org.matchlab.academic_net.di

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.ProjectsRoleRequirementProvider
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.FeatureUnavailableException
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorQuery
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirement
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetailsResult
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.domain.ProjectDraft
import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectDuration
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectFilters
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectSearchCapabilities
import org.matchlab.academic_net.common.model.Sphere
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The projects, their search and their page in the graph of a signed-in user over the Gateway (a mock engine): the wiring
 * that no layer's own tests see, from the use cases the screens hold down to the HTTP calls. The answers are the shapes
 * `docs/openapi.yaml` gives. API-GAP G40-G45: creating, "Мои проекты", unpublishing and the type and duration filters are
 * off, so nothing for them is ever sent.
 */
class ProjectsGraphTest {
    private val viewer = ServerUserId
    private val session = Session(UserId(viewer), StudentEmail, AccessToken("token-1"))

    private fun project(id: String, owner: String, revision: Int = 3, roles: String = "[]", title: String = "Проект $id") =
        """{"id":"$id","owner_user_id":"$owner","details":{"title":"$title","field":"Biology","problem":"Проблема","goal":"Цель","tags":["ML"]},"status":"published","revision":$revision,"roles":$roles}"""

    private fun profile(id: String, university: String) =
        """{"user_id":"$id","details":{"display_name":"Имя $id","university":"$university"},"verified":true,"rating":4.5,"review_count":2,"completed_project_count":7}"""

    /** A server with two projects owned by `owner-a` and `owner-b` (the second owner has no profile for the viewer). */
    private fun serverWithProjects() = FakeServer().apply {
        var roles = "[]"
        var revision = 3
        onOther = { arrival ->
            when (arrival.call) {
                "GET /api/v1/search/projects" ->
                    json("""{"items":[${project("p-1", "owner-a")},${project("p-2", "owner-b")}],"total":"2"}""")
                "GET /api/v1/users/owner-a/profile" -> json(profile("owner-a", "НИУ ВШЭ"))
                "GET /api/v1/users/owner-b/profile" -> jsonError(HttpStatusCode.NotFound, "no such user")
                "GET /api/v1/projects/p-1" -> json(project("p-1", "owner-a", revision, roles))
                "POST /api/v1/projects/p-1/roles" -> {
                    revision += 1
                    roles = """[{"id":"r-new","title":"Дизайнер","skills":["Figma"],"slots":1,"occupied":0}]"""
                    json(project("p-1", "owner-a", revision, roles))
                }
                "GET /api/v1/search/people" ->
                    json("""{"items":[{"user_id":"u-1","display_name":"Анна"}],"total":"1"}""")
                else -> jsonError(HttpStatusCode.NotFound, "not found")
            }
        }
    }

    private fun dependenciesOf(server: FakeServer) = AppLaunch(server).sessionGraph(session).dependencies

    // ---- building ----

    @Test
    fun theProjectSourcesAreBuiltWithoutARequestAndSayWhatTheyCannotDo() {
        val server = serverWithProjects()

        val dependencies = dependenciesOf(server)

        assertEquals(ProjectsCapabilities(canCreate = false, canListMine = false, canUnpublish = false), dependencies.projects.capabilities)
        assertEquals(ProjectSearchCapabilities(typeFilter = false, durationFilter = false), dependencies.projectSearch.capabilities)
        assertEquals(emptyList(), server.calls)
    }

    @Test
    fun theFeaturesShareTheOneRepositoryOfProjectsOfTheSession() {
        val component = AppLaunch(serverWithProjects()).sessionGraph(session)

        assertEquals(component.projectsRepository.capabilities, component.dependencies.projects.capabilities)
        assertTrue(component.roleRequirements is ProjectsRoleRequirementProvider)
    }

    // ---- what the Gateway cannot do ----

    @Test
    fun creatingAProjectIsRefusedByTheGatewaySourceAndSendsNothing() = runTest {
        val server = serverWithProjects()
        val draft = ProjectDraft(
            title = "T", type = ProjectType.Scientific, sphere = Sphere("Biology", "Biology"),
            problem = "P", goal = "G",
            start = YearMonth(2026, 10), end = YearMonth(2027, 3),
        )

        val result = dependenciesOf(server).projects.publishProject(draft)

        assertEquals("G40", assertIs<FeatureUnavailableException>(assertIs<SubmitResult.Failure>(result).cause).gap)
        assertEquals(emptyList(), server.calls)
    }

    // ---- the search ----

    @Test
    fun aSearchAsksForTheListAndThenForTheProfileOfEachOwnerOnce() = runTest {
        val server = serverWithProjects()
        val query = ProjectQuery(text = "  ml ", filters = ProjectFilters(sphere = Sphere("Biology", "Biology"), keywords = listOf("ROS")))

        val page = dependenciesOf(server).projectSearch.searchProjects(query, PageRequest.First).getOrThrow()

        assertEquals(listOf("p-1", "p-2"), page.items.map { it.project.id.value })
        assertEquals(listOf("НИУ ВШЭ", ""), page.items.map { it.initiatorUniversity })
        assertEquals(listOf("Имя owner-a", ""), page.items.map { it.project.initiator.name })
        assertEquals(2, page.total)
        assertEquals(
            setOf("GET /api/v1/search/projects", "GET /api/v1/users/owner-a/profile", "GET /api/v1/users/owner-b/profile"),
            server.calls.toSet(),
        )
        assertEquals(3, server.calls.size, "the owners are asked for once each")
        assertTrue(server.arrivals.all { it.authorization == listOf("Bearer token-1") })
    }

    @Test
    fun aSearchNeverSendsTheParametersTheApiDoesNotDefine() = runTest {
        val server = serverWithProjects()
        val filters = ProjectFilters(
            types = setOf(ProjectType.Scientific),
            durations = setOf(ProjectDuration.UpTo3Months),
        )

        dependenciesOf(server).projectSearch.searchProjects(ProjectQuery("ml", filters), PageRequest(20, 20)).getOrThrow()

        val request = server.requests("GET /api/v1/search/projects").single()
        assertEquals(setOf("limit", "offset", "q"), request.queryNames, "no type, duration, status, mine or profile_user_id")
        assertEquals(listOf("20"), request.queryValues("limit"))
        assertEquals(listOf("20"), request.queryValues("offset"))
        assertEquals(listOf("ml"), request.queryValues("q"))
    }

    @Test
    fun theSpheresOfTheFilterComeFromTheProjectsFeatureWithoutARequest() = runTest {
        val server = serverWithProjects()

        val spheres = dependenciesOf(server).projectSearch.getSpheres().getOrThrow()

        assertEquals(12, spheres.size)
        assertEquals(emptyList(), server.calls)
    }

    // ---- the page of a project ----

    @Test
    fun thePageOfAProjectIsTheProjectAndTheFactsOfItsOwner() = runTest {
        val server = serverWithProjects()

        val result = dependenciesOf(server).projectDetails.getProjectDetails(ProjectId("p-1"))

        val details = assertIs<ProjectDetailsResult.Found>(result).details
        assertEquals("Проект p-1", details.project.title)
        assertEquals("НИУ ВШЭ", details.initiator.university)
        assertEquals(4.5, details.initiator.rating)
        assertEquals(7, details.initiator.completedProjects)
        assertEquals(setOf("GET /api/v1/projects/p-1", "GET /api/v1/users/owner-a/profile"), server.calls.toSet())
    }

    @Test
    fun aProjectTheServerDoesNotKnowIsNotFound() = runTest {
        val result = dependenciesOf(serverWithProjects()).projectDetails.getProjectDetails(ProjectId("missing"))

        assertEquals(ProjectDetailsResult.NotFound, result)
    }

    // ---- search, page, role, people ----

    @Test
    fun fromTheSearchToThePageToANewRoleToTheSearchOfPeopleForIt() = runTest {
        val server = serverWithProjects()
        val dependencies = dependenciesOf(server)

        // 02: the project is found.
        val found = dependencies.projectSearch.searchProjects(ProjectQuery("ml"), PageRequest.First).getOrThrow().items.first()
        assertEquals(ProjectId("p-1"), found.project.id)

        // 04: its page.
        assertIs<ProjectDetailsResult.Found>(dependencies.projectDetails.getProjectDetails(found.project.id))

        // 07: the screen of the project reads it, and a role is added with the revision that was read.
        assertEquals("Проект p-1", assertIs<LoadResult.Found<Project>>(dependencies.projects.loadProject(found.project.id)).value.title)
        val added = dependencies.projects.addRole(found.project.id, RoleDraft("Дизайнер", listOf("Figma")))
        val role = assertIs<SubmitResult.Success<Role>>(added).value
        assertEquals(RoleId("r-new"), role.id)
        val post = server.requests("POST /api/v1/projects/p-1/roles").single()
        assertTrue("\"expected_revision\":3" in post.body, post.body)
        assertTrue("\"slots\":1" in post.body, post.body)

        // 08: the search of people starts from the skills of that role, read from the cache with no request of its own.
        val callsBefore = server.calls.size
        val requirement = dependencies.collaborators.getRoleRequirement(found.project.id, role.id).getOrThrow()
        assertEquals(RoleRequirement("Проект p-1", "Дизайнер", listOf("Figma")), requirement)
        assertEquals(callsBefore, server.calls.size, "the requirement comes from the project the session has read")
        val people = dependencies.collaborators
            .searchCollaborators(CollaboratorQuery(requirement.skills.joinToString(", ")), PageRequest.First)
            .getOrThrow()

        assertEquals(listOf("u-1"), people.items.map { it.user.id.value })
        // The text of the bar is what the server is asked: the skills of the role, as typed (API-GAP G31).
        assertEquals(listOf("Figma"), server.requests("GET /api/v1/search/people").single().queryValues("q"))
    }

    @Test
    fun aStaleRevisionIsAConflictTheScreenCanTellApart() = runTest {
        val server = serverWithProjects().apply {
            val inner = onOther
            onOther = { arrival ->
                if (arrival.call == "POST /api/v1/projects/p-1/roles") jsonError(HttpStatusCode.Conflict, "stale") else inner(arrival)
            }
        }
        val dependencies = dependenciesOf(server)
        dependencies.projects.loadProject(ProjectId("p-1"))

        val result = dependencies.projects.addRole(ProjectId("p-1"), RoleDraft("Дизайнер"))

        assertIs<ProjectConflictException>(assertIs<SubmitResult.Failure>(result).cause)
        assertEquals(2, server.requests("GET /api/v1/projects/p-1").size, "the project is read again after the conflict")
        assertEquals(1, server.requests("POST /api/v1/projects/p-1/roles").size, "no automatic retry")
    }

    @Test
    fun aRoleOfAProjectThatIsNotThereGivesNoRequirement() = runTest {
        val dependencies = dependenciesOf(serverWithProjects())

        val result = dependencies.collaborators.getRoleRequirement(ProjectId("missing"), RoleId("r-1"))

        assertTrue(result.isFailure)
        assertNull(result.getOrNull())
    }
}
