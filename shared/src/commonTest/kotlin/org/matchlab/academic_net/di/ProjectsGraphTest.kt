package org.matchlab.academic_net.di

import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLQueryComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.ProjectsRoleRequirementProvider
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.common.result.RemoteFailure
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
import org.matchlab.academic_net.feature.projects.domain.usecase.UnpublishProjectResult
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectDuration
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectFilters
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectSearchCapabilities
import org.matchlab.academic_net.common.model.Spheres
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The projects, their search and their page in the graph of a signed-in user over the Gateway (a mock engine): the wiring
 * that no layer's own tests see, from the use cases the screens hold down to the HTTP calls. The answers are the shapes
 * `docs/openapi.yaml` gives. Creating, "Мои проекты", unpublishing and the type and duration filters are on, with the
 * values the app defines (`docs/api-values.md`).
 */
class ProjectsGraphTest {
    private val viewer = ServerUserId
    private val session = Session(UserId(viewer), StudentEmail, AccessToken("token-1"))

    private fun project(id: String, owner: String, revision: Int = 3, roles: String = "[]", title: String = "Проект $id") =
        """{"id":"$id","owner_user_id":"$owner","details":{"title":"$title","type":"scientific","field":"Биология","problem":"Проблема","goal":"Цель","tags":["ML"],"planned_start":"2026-10-01","planned_end":"2027-03-31"},"status":"recruiting","revision":$revision,"roles":$roles}"""

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
                "GET /api/v1/users/$viewer/profile" -> json(profile(viewer, "МГУ"))
                "GET /api/v1/projects" ->
                    json("""{"items":[${project("p-1", "owner-a")},${project("m-1", viewer)},${project("p-2", "owner-b")},${project("m-2", viewer)}],"total":"4"}""")
                "POST /api/v1/projects" -> json(project("m-new", viewer, revision = 1), HttpStatusCode.Created)
                "POST /api/v1/projects/m-1/transitions" -> json(project("m-1", viewer, revision = 4).replace("\"status\":\"recruiting\"", "\"status\":\"unpublished\""))
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
    fun theProjectSourcesAreBuiltWithoutARequestAndAreOn() {
        val server = serverWithProjects()

        val dependencies = dependenciesOf(server)

        assertEquals(
            ProjectsCapabilities(canCreate = true, canListMine = true, canUnpublish = true, canChangeLifecycle = true),
            dependencies.projects.capabilities,
        )
        assertEquals(ProjectSearchCapabilities(typeFilter = true, durationFilter = true), dependencies.projectSearch.capabilities)
        assertEquals(emptyList(), server.calls)
    }

    @Test
    fun theFeaturesShareTheOneRepositoryOfProjectsOfTheSession() {
        val component = AppLaunch(serverWithProjects()).sessionGraph(session)

        assertEquals(component.projectsRepository.capabilities, component.dependencies.projects.capabilities)
        assertTrue(component.roleRequirements is ProjectsRoleRequirementProvider)
    }

    // ---- creating, "Я инициатор", unpublishing ----

    @Test
    fun creatingAProjectPostsTheTextsAndTheMonthsAndReturnsTheProject() = runTest {
        val server = serverWithProjects()
        val draft = ProjectDraft(
            title = "T", type = ProjectType.Practical, sphere = Spheres.all[11],
            problem = "P", goal = "G", tags = listOf("ML"),
            roles = listOf(RoleDraft("Дизайнер", listOf("Figma"))),
            start = YearMonth(2026, 10), end = YearMonth(2028, 2),
        )

        val result = dependenciesOf(server).projects.publishProject(draft)

        val project = assertIs<SubmitResult.Success<Project>>(result).value
        assertEquals(ProjectId("m-new"), project.id)
        assertTrue(project.viewerIsInitiator)
        val post = server.requests("POST /api/v1/projects").single()
        assertEquals(listOf("Bearer token-1"), post.authorization)
        for (part in listOf(
            "\"title\":\"T\"", "\"type\":\"practical\"", "\"field\":\"Дизайн\"", "\"planned_start\":\"2026-10-01\"",
            "\"planned_end\":\"2028-02-29\"", "\"slots\":1", "\"tags\":[\"ML\"]",
        )) {
            assertTrue(part in post.body, "$part in ${post.body}")
        }
    }

    @Test
    fun aRejectedCreationIsAFailureAndIsNotRepeated() = runTest {
        val server = serverWithProjects().apply {
            val inner = onOther
            onOther = { arrival ->
                if (arrival.call == "POST /api/v1/projects") jsonError(HttpStatusCode.BadRequest, "bad") else inner(arrival)
            }
        }
        val draft = ProjectDraft(
            title = "T", type = ProjectType.Scientific, sphere = Spheres.all[0], problem = "P", goal = "G",
            start = YearMonth(2026, 10), end = YearMonth(2027, 3),
        )

        val result = dependenciesOf(server).projects.publishProject(draft)

        assertEquals(400, assertIs<RemoteFailure.Http>(assertIs<SubmitResult.Failure>(result).cause).status)
        assertEquals(1, server.requests("POST /api/v1/projects").size)
    }

    @Test
    fun anInvalidDraftSendsNothing() = runTest {
        val server = serverWithProjects()

        val result = dependenciesOf(server).projects.publishProject(ProjectDraft())

        assertIs<SubmitResult.Invalid<*>>(result)
        assertEquals(emptyList(), server.calls)
    }

    @Test
    fun myProjectsAreTheCatalogsProjectsOfTheViewerInTheServersOrder() = runTest {
        val server = serverWithProjects()

        val mine = dependenciesOf(server).projects.observeMyProjects().first()

        assertEquals(listOf("m-1", "m-2"), mine.map { it.id.value })
        assertTrue(mine.all { it.viewerIsInitiator })
        val request = server.requests("GET /api/v1/projects").single()
        assertEquals(setOf("limit", "offset", "mine"), request.queryNames, "no profile_user_id, no status")
        assertEquals(listOf("owner"), request.queryValues("mine"))
        assertEquals(listOf("Bearer token-1"), request.authorization)
        assertEquals(listOf("100"), request.queryValues("limit"))
        assertEquals(listOf("0"), request.queryValues("offset"))
        assertEquals(ProjectType.Scientific, mine.first().type)
        assertEquals(Spheres.all[6], mine.first().sphere)
    }

    @Test
    fun aCreatedProjectIsFirstInMyProjects() = runTest {
        val dependencies = dependenciesOf(serverWithProjects())
        val draft = ProjectDraft(
            title = "T", type = ProjectType.Scientific, sphere = Spheres.all[0], problem = "P", goal = "G",
            start = YearMonth(2026, 10), end = YearMonth(2027, 3),
        )
        dependencies.projects.publishProject(draft)

        val mine = dependencies.projects.observeMyProjects().first()

        assertEquals(listOf("m-new", "m-1", "m-2"), mine.map { it.id.value })
    }

    @Test
    fun aFailureOfTheCatalogGoesToTheCollectorAndCollectingAgainReadsAgain() = runTest {
        var fail = true
        val server = serverWithProjects().apply {
            val inner = onOther
            onOther = { arrival ->
                if (arrival.call == "GET /api/v1/projects" && fail) jsonError(HttpStatusCode.InternalServerError, "oops") else inner(arrival)
            }
        }
        val dependencies = dependenciesOf(server)

        assertFailsWith<RemoteFailure.Http> { dependencies.projects.observeMyProjects().first() }
        fail = false

        assertEquals(listOf("m-1", "m-2"), dependencies.projects.observeMyProjects().first().map { it.id.value })
    }

    @Test
    fun unpublishingPostsTheUnpublishActionWithTheRevisionOfTheList() = runTest {
        val server = serverWithProjects()
        val dependencies = dependenciesOf(server)
        dependencies.projects.observeMyProjects().first()

        val result = dependencies.projects.unpublishProject(ProjectId("m-1"))

        val unpublished = assertIs<UnpublishProjectResult.Unpublished>(result).project
        assertEquals(ProjectStatus.Unpublished, unpublished.status)
        val post = server.requests("POST /api/v1/projects/m-1/transitions").single()
        assertTrue("\"action\":\"unpublish\"" in post.body, post.body)
        assertTrue("\"expected_revision\":3" in post.body, post.body)
        assertEquals(
            listOf(ProjectStatus.Unpublished, ProjectStatus.Recruiting),
            dependencies.projects.observeMyProjects().first().map { it.status },
        )
    }

    @Test
    fun unpublishingAProjectThatIsAlreadyUnpublishedSendsNothing() = runTest {
        val server = serverWithProjects()
        val dependencies = dependenciesOf(server)
        dependencies.projects.observeMyProjects().first()
        dependencies.projects.unpublishProject(ProjectId("m-1"))
        val posts = server.requests("POST /api/v1/projects/m-1/transitions").size

        val again = dependencies.projects.unpublishProject(ProjectId("m-1"))

        assertEquals(UnpublishProjectResult.NotPublished, again)
        assertEquals(posts, server.requests("POST /api/v1/projects/m-1/transitions").size)
    }

    // ---- the search ----

    @Test
    fun aSearchAsksForTheListAndThenForTheProfileOfEachOwnerOnce() = runTest {
        val server = serverWithProjects()
        val query = ProjectQuery(text = "  ml ", filters = ProjectFilters(sphere = Spheres.all[6], keywords = listOf("ROS")))

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
    fun aSearchSendsTheTextsOfOneChosenTypeDurationAndTheSphere() = runTest {
        val server = serverWithProjects()
        val filters = ProjectFilters(
            types = setOf(ProjectType.Scientific),
            sphere = Spheres.all[2],
            durations = setOf(ProjectDuration.UpTo3Months),
        )

        dependenciesOf(server).projectSearch.searchProjects(ProjectQuery("ml", filters), PageRequest(20, 20)).getOrThrow()

        val request = server.requests("GET /api/v1/search/projects").single()
        assertEquals(setOf("limit", "offset", "q", "type", "field", "duration"), request.queryNames, "no status, mine or profile_user_id")
        assertEquals(listOf("20"), request.queryValues("limit"))
        assertEquals(listOf("20"), request.queryValues("offset"))
        assertEquals(listOf("ml"), request.queryValues("q"))
        assertEquals(listOf("scientific"), request.queryValues("type"))
        assertEquals(listOf("Инженерия"), request.queryValues("field").map { it.decodeURLQueryComponent() })
        assertEquals(listOf("up_to_3m"), request.queryValues("duration"))
    }

    @Test
    fun aSearchWithBothTypesAndTwoDurationsSendsNeitherParameter() = runTest {
        val server = serverWithProjects()
        val filters = ProjectFilters(
            types = ProjectType.entries.toSet(),
            durations = setOf(ProjectDuration.UpTo3Months, ProjectDuration.Over6Months),
        )

        dependenciesOf(server).projectSearch.searchProjects(ProjectQuery("ml", filters), PageRequest(20, 20)).getOrThrow()

        val request = server.requests("GET /api/v1/search/projects").single()
        assertEquals(setOf("limit", "offset", "q"), request.queryNames)
    }

    @Test
    fun aProjectOfValuesTheAppDoesNotKnowIsFoundAllTheSame() = runTest {
        val server = serverWithProjects().apply {
            val inner = onOther
            onOther = { arrival ->
                if (arrival.call == "GET /api/v1/search/projects") {
                    json("""{"items":[{"id":"x-1","owner_user_id":"owner-a","details":{"title":"Чужой","type":"1","field":"Biology"},"status":"published","revision":1}],"total":"1"}""")
                } else {
                    inner(arrival)
                }
            }
        }

        val page = dependenciesOf(server).projectSearch.searchProjects(ProjectQuery("x"), PageRequest.First).getOrThrow()

        val project = page.items.single().project
        assertEquals("Чужой", project.title)
        assertNull(project.type)
        assertNull(project.status)
        assertNull(project.sphere)
        assertNull(project.period)
    }

    @Test
    fun theSpheresOfTheFilterComeFromTheProjectsFeatureWithoutARequest() = runTest {
        val server = serverWithProjects()

        val spheres = dependenciesOf(server).projectSearch.getSpheres().getOrThrow()

        assertEquals(Spheres.all, spheres)
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

    // ---- sprint 2: the lifecycle, the editing and the team ----

    private fun projectWithTeam(id: String = "t-1", revision: Int = 5) =
        """{"id":"$id","owner_user_id":"owner-a","details":{"title":"Команда","type":"scientific","field":"Биология","problem":"П","goal":"Ц","planned_start":"2026-10-01","planned_end":"2027-03-31"},"status":"recruiting","revision":$revision,"roles":[{"id":"r-1","title":"Dev","slots":2,"occupied":2}],"members":[{"user_id":"u-1","role_id":"r-1"},{"user_id":"u-2","role_id":"r-1"},{"user_id":"owner-a","role_id":"r-1"}]}"""

    private fun serverWithTeam() = serverWithProjects().apply {
        val inner = onOther
        onOther = { arrival ->
            when (arrival.call) {
                "GET /api/v1/projects/t-1" -> json(projectWithTeam())
                "GET /api/v1/users/u-1/profile" -> json(profile("u-1", "МГУ"))
                "GET /api/v1/users/u-2/profile" -> jsonError(HttpStatusCode.NotFound, "no such user")
                else -> inner(arrival)
            }
        }
    }

    @Test
    fun theLifecycleMovesPostTheTextsWithTheRevisionOfTheList() = runTest {
        val server = serverWithProjects().apply {
            val inner = onOther
            onOther = { arrival ->
                if (arrival.call == "POST /api/v1/projects/m-1/transitions") {
                    json(project("m-1", viewer, revision = 4).replace("\"status\":\"recruiting\"", "\"status\":\"active\""))
                } else {
                    inner(arrival)
                }
            }
        }
        val dependencies = dependenciesOf(server)
        dependencies.projects.observeMyProjects().first()

        val result = dependencies.projects.changeLifecycle(ProjectId("m-1"), org.matchlab.academic_net.feature.projects.domain.ProjectLifecycleAction.Start)

        val changed = assertIs<org.matchlab.academic_net.feature.projects.domain.usecase.ChangeLifecycleResult.Changed>(result)
        assertEquals(ProjectStatus.Started, changed.project.status)
        val post = server.requests("POST /api/v1/projects/m-1/transitions").single()
        assertTrue("\"action\":\"start\"" in post.body, post.body)
        assertTrue("\"expected_revision\":3" in post.body, post.body)
    }

    @Test
    fun aRefusedMoveIsNotAllowedAndTheProjectIsReadAgain() = runTest {
        val server = serverWithProjects().apply {
            val inner = onOther
            onOther = { arrival ->
                when (arrival.call) {
                    "POST /api/v1/projects/m-1/transitions" -> jsonError(HttpStatusCode.Conflict, "no")
                    "GET /api/v1/projects/m-1" -> json(project("m-1", viewer, revision = 9))
                    else -> inner(arrival)
                }
            }
        }
        val dependencies = dependenciesOf(server)
        dependencies.projects.observeMyProjects().first()

        val result = dependencies.projects.changeLifecycle(ProjectId("m-1"), org.matchlab.academic_net.feature.projects.domain.ProjectLifecycleAction.Cancel)

        assertEquals(org.matchlab.academic_net.feature.projects.domain.usecase.ChangeLifecycleResult.NotAllowed, result)
        assertEquals(1, server.requests("GET /api/v1/projects/m-1").size)
        assertEquals(1, server.requests("POST /api/v1/projects/m-1/transitions").size, "no retry")
    }

    @Test
    fun editingPutsTheDescriptionWithoutRolesAndDeletingARoleSendsTheRevisionInTheQuery() = runTest {
        val server = serverWithProjects().apply {
            val inner = onOther
            onOther = { arrival ->
                when (arrival.call) {
                    "PUT /api/v1/projects/m-1" -> json(project("m-1", viewer, revision = 4, roles = """[{"id":"r-1","title":"Dev","slots":1,"occupied":0}]"""))
                    "DELETE /api/v1/projects/m-1/roles/r-1" -> json(project("m-1", viewer, revision = 5))
                    else -> inner(arrival)
                }
            }
        }
        val dependencies = dependenciesOf(server)
        dependencies.projects.observeMyProjects().first()
        val draft = org.matchlab.academic_net.feature.projects.domain.ProjectInfoDraft(
            title = "Новое", type = ProjectType.Practical, sphere = Spheres.all[2], problem = "П", goal = "Ц",
            tags = listOf("ML"), start = YearMonth(2026, 10), end = YearMonth(2027, 3),
        )

        val saved = dependencies.projects.updateProject(ProjectId("m-1"), draft)
        assertIs<SubmitResult.Success<Project>>(saved)
        val removed = dependencies.projects.removeRole(ProjectId("m-1"), RoleId("r-1"))

        assertIs<org.matchlab.academic_net.feature.projects.domain.usecase.RemoveRoleResult.Removed>(removed)
        val put = server.requests("PUT /api/v1/projects/m-1").single()
        assertEquals(listOf("Bearer token-1"), put.authorization)
        assertTrue("\"expected_revision\":3" in put.body, put.body)
        assertTrue("\"type\":\"practical\"" in put.body && "\"field\":\"Инженерия\"" in put.body, put.body)
        assertTrue("\"planned_end\":\"2027-03-31\"" in put.body, put.body)
        assertTrue("roles" !in put.body, "G63: the roles are never sent: ${put.body}")
        val delete = server.requests("DELETE /api/v1/projects/m-1/roles/r-1").single()
        assertEquals("expected_revision=4", delete.query)
    }

    @Test
    fun theTeamIsReadThroughOneDirectoryPerSession() = runTest {
        val server = serverWithTeam()
        val dependencies = dependenciesOf(server)

        // 07b: the projects feature reads one project with its people.
        val project = assertIs<LoadResult.Found<Project>>(dependencies.projects.loadProject(ProjectId("t-1"))).value
        assertEquals(listOf("Имя u-1", ""), project.roles.single().members.map { it.user.name }.take(2))
        assertEquals(listOf(true, false), project.roles.single().members.take(2).map { it.hasProfile })
        val afterProjects = server.calls.count { it.startsWith("GET /api/v1/users/") }

        // 04: the page of the project asks for the team: the people are in the one cache of the session.
        val team = dependencies.projectDetails.getProjectTeam(project)

        assertEquals(listOf("u-1", "u-2"), team.map { it.person.user.id.value })
        assertEquals("Имя u-1", team[0].person.user.name)
        assertEquals(false, team[1].person.hasProfile)
        assertEquals(
            afterProjects,
            server.calls.count { it.startsWith("GET /api/v1/users/") },
            "u-1 and u-2 were read once: the features share the directory of the session",
        )
        assertEquals(1, server.requests("GET /api/v1/users/u-1/profile").size)
        assertEquals(1, server.requests("GET /api/v1/users/u-2/profile").size)
    }

    @Test
    fun theTeamNeverFailsWhateverTheServerSaysAboutAPerson() = runTest {
        val server = serverWithTeam().apply {
            val inner = onOther
            onOther = { arrival ->
                if (arrival.call == "GET /api/v1/users/u-1/profile") jsonError(HttpStatusCode.InternalServerError, "oops") else inner(arrival)
            }
        }
        val dependencies = dependenciesOf(server)
        val project = assertIs<LoadResult.Found<Project>>(dependencies.projects.loadProject(ProjectId("t-1"))).value

        val team = dependencies.projectDetails.getProjectTeam(project)

        assertEquals(listOf(false, false), team.map { it.person.hasProfile })
    }
}
