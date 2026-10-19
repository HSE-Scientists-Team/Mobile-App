package org.matchlab.academic_net.di

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.Backend
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorFilters
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorQuery
import org.matchlab.academic_net.gateway.Gateway
import org.matchlab.academic_net.securestore.InMemorySecureStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The people search and the feed in the graph of a signed-in user over the Gateway (a mock engine): they are built
 * without a request, the feed knows the viewer from the
 * session, and both talk through the one session client with the token of the session. The search offers the filter by the
 * type of works (the app's own codes, `docs/api-values.md`).
 */
class PeopleAndFeedGraphTest {
    private val viewer = UserId("user-me")
    private val token = "people-token"

    /** The process built over a mock engine, and the graph of the session of [viewer]. */
    private inner class Graph(handler: MockRequestHandler) {
        val engine = MockEngine(handler)

        /** How many Gateway clients the process made. */
        var clients = 0
            private set

        val component: SessionComponent = createSessionComponent(
            createAppComponent(
                InMemorySecureStore(),
                Backend.Gateway(),
                GatewayFactory { config ->
                    clients++
                    Gateway(config, engine)
                },
            ),
            Session(viewer, "student@hse.ru", AccessToken(token)),
        )

        val requests: List<HttpRequestData> get() = engine.requestHistory
    }

    private fun graphAnswering(body: String) = Graph {
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    // ---- building ----

    @Test
    fun theSearchAndTheFeedAreBuiltWithoutARequest() {
        val graph = Graph { error("No request is expected") }

        val dependencies = graph.component.dependencies

        assertNotNull(dependencies.collaborators)
        assertNotNull(dependencies.feed)
        assertEquals(emptyList(), graph.requests)
    }

    @Test
    fun theSearchHasTheFilterByTheTypeOfWorks() {
        val graph = Graph { error("No request is expected") }

        assertTrue(graph.component.dependencies.collaborators.capabilities.projectTypeFilter)
    }

    @Test
    fun theFeedKnowsTheViewerFromTheSession() {
        val graph = Graph { error("No request is expected") }

        assertEquals(viewer, graph.component.dependencies.feed.viewerId)
    }

    // ---- the calls ----

    @Test
    fun aSearchReachesTheServerWithTheTokenOfTheSession() = runTest {
        val graph = graphAnswering("""{"items":[{"user_id":"u-1","display_name":"Анна"}],"total":"1"}""")
        val query = CollaboratorQuery("kotlin", CollaboratorFilters(verifiedProfile = true, keywords = listOf("ML")))

        val page = graph.component.dependencies.collaborators.searchCollaborators(query, PageRequest(0, 20)).getOrThrow()

        assertEquals(listOf("u-1"), page.items.map { it.user.id.value })
        val request = graph.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/search/people", request.url.encodedPath)
        assertEquals(listOf("Bearer $token"), request.headers.getAll(HttpHeaders.Authorization))
        val parameters = request.url.parameters
        assertEquals("kotlin", parameters["q"])
        assertEquals(listOf("ML"), parameters.getAll("skill"))
        assertEquals("true", parameters["verified_only"])
        assertEquals("20", parameters["limit"])
        assertEquals("0", parameters["offset"])
        assertFalse("project_type" in parameters.names(), "no type chosen: the parameter is not sent")
    }

    @Test
    fun oneChosenTypeOfWorksReachesTheServerAsItsCode() = runTest {
        val graph = graphAnswering("""{"items":[],"total":"0"}""")

        graph.component.dependencies.collaborators.searchCollaborators(
            CollaboratorQuery(filters = CollaboratorFilters(workTypes = setOf(ProjectType.Practical))),
            PageRequest(0, 20),
        ).getOrThrow()

        assertEquals("2", graph.requests.single().url.parameters["project_type"])
    }

    @Test
    fun bothTypesOfWorksSendNoProjectType() = runTest {
        val graph = graphAnswering("""{"items":[],"total":"0"}""")

        graph.component.dependencies.collaborators.searchCollaborators(
            CollaboratorQuery(filters = CollaboratorFilters(workTypes = ProjectType.entries.toSet())),
            PageRequest(0, 20),
        ).getOrThrow()

        assertFalse("project_type" in graph.requests.single().url.parameters.names())
    }

    @Test
    fun aCountAsksForTheSmallestPage() = runTest {
        val graph = graphAnswering("""{"items":[{"user_id":"u-1"}],"total":"57"}""")

        val count = graph.component.dependencies.collaborators.countCollaborators(CollaboratorQuery("kotlin")).getOrThrow()

        assertEquals(57, count)
        val parameters = graph.requests.single().url.parameters
        assertEquals("1", parameters["limit"])
        assertEquals("0", parameters["offset"])
    }

    @Test
    fun theFeedAsksForTenPeopleAndDropsTheViewerAndTheRecordsWithoutAnId() = runTest {
        val graph = graphAnswering(
            """{"items":[{"user_id":"u-1"},{"user_id":"user-me"},{"display_name":"без id"},{"user_id":"u-2"},{"user_id":"u-1"}]}""",
        )

        val people = graph.component.dependencies.feed.getRecommendedPeople().getOrThrow()

        assertEquals(listOf("u-1", "u-2"), people.map { it.user.id.value })
        val request = graph.requests.single()
        assertEquals("/api/v1/recommendations/people", request.url.encodedPath)
        assertEquals("10", request.url.parameters["limit"])
        assertEquals(listOf("Bearer $token"), request.headers.getAll(HttpHeaders.Authorization))
    }

    @Test
    fun theSearchAndTheFeedShareOneSessionClient() = runTest {
        val graph = graphAnswering("""{"items":[],"total":"0"}""")
        val dependencies = graph.component.dependencies

        dependencies.collaborators.searchCollaborators(CollaboratorQuery(), PageRequest.First).getOrThrow()
        dependencies.feed.getRecommendedPeople().getOrThrow()

        assertEquals(1, graph.clients)
        assertEquals(2, graph.requests.size)
        assertEquals(
            listOf("/api/v1/search/people", "/api/v1/recommendations/people"),
            graph.requests.map { it.url.encodedPath },
        )
    }
}
