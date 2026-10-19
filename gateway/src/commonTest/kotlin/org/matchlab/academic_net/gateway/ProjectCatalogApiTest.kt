package org.matchlab.academic_net.gateway

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.ProjectListDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `GET /api/v1/search/projects`, `GET /api/v1/projects` and `GET /api/v1/projects/{id}` as everybody's projects (02, 03,
 * 04, 06). The parameters are read back from the parsed URL of the request the engine got. `type`, `field` and `duration`
 * carry the codes of `docs/api-values.md` when set; `status`, `mine` and `profile_user_id` are never sent.
 */
class ProjectCatalogApiTest {
    private fun server(body: String = ProjectSpec.list(total = "0")) = ProjectSessionFixture { json(body) }

    private suspend fun ProjectSessionFixture.search(
        params: ProjectSearchParams = ProjectSearchParams(),
        page: PageRequest = PageRequest.First,
    ): ProjectListDto = session.projectCatalog.search(params, page)

    // ---- the request ----

    @Test
    fun aSearchIsAGetOfTheSearchPathWithTheBearerTokenAndThePage() = runTest {
        val server = server()

        server.search(page = PageRequest(offset = 40, limit = 20))

        val request = server.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/search/projects", request.url.encodedPath)
        assertEquals(listOf("Bearer ${ProjectSessionFixture.Token}"), request.authorizations)
        assertEquals("20", request.url.parameters["limit"])
        assertEquals("40", request.url.parameters["offset"])
        assertEquals("", request.bodyText())
    }

    @Test
    fun theLimitAndTheOffsetAreAlwaysSentAndNothingElseWithoutFilters() = runTest {
        val server = server()

        server.search(page = PageRequest(offset = 0, limit = 1))
        server.search(page = PageRequest(offset = 10_000, limit = 100))

        assertEquals(setOf("limit", "offset"), server.requests[0].url.parameters.names())
        assertEquals("1", server.requests[0].url.parameters["limit"])
        assertEquals("0", server.requests[0].url.parameters["offset"])
        assertEquals(setOf("limit", "offset"), server.requests[1].url.parameters.names())
        assertEquals("100", server.requests[1].url.parameters["limit"])
        assertEquals("10000", server.requests[1].url.parameters["offset"])
    }

    @Test
    fun theTextTheFieldAndTheTagsGoUnderTheirOwnNames() = runTest {
        val server = server()

        server.search(ProjectSearchParams(q = "компьютерное зрение", field = "1", tags = listOf("Python", "ROS")))

        val parameters = server.requests.single().url.parameters
        assertEquals("компьютерное зрение", parameters["q"])
        assertEquals("1", parameters["field"])
        assertEquals(listOf("Python", "ROS"), parameters.getAll("tag"))
    }

    @Test
    fun theTagsRepeatInTheGivenOrderAndABlankTagIsLeftOut() = runTest {
        val server = server()

        server.search(ProjectSearchParams(tags = listOf("b", "", "a", "  ", "b")))

        assertEquals(listOf("b", "a", "b"), server.requests.single().url.parameters.getAll("tag"))
    }

    @Test
    fun aBlankTextOrFieldIsNotSent() = runTest {
        val server = server()

        server.search(ProjectSearchParams(q = "", field = ""))
        server.search(ProjectSearchParams(q = "   ", field = "\t"))
        server.search(ProjectSearchParams(q = null, field = null))

        for (request in server.requests) {
            assertEquals(setOf("limit", "offset"), request.url.parameters.names())
        }
    }

    @Test
    fun theTextGoesAsGivenBecauseTheCutOfSpacesIsTheDataLayersJob() = runTest {
        val server = server()

        server.search(ProjectSearchParams(q = "  ML  "))

        assertEquals("  ML  ", server.requests.single().url.parameters["q"])
    }

    @Test
    fun theTypeAndTheDurationGoAsTheirCodesWhenSet() = runTest {
        val server = server()

        server.search(ProjectSearchParams(type = "2", duration = "3"))

        val parameters = server.requests.single().url.parameters
        assertEquals("2", parameters["type"])
        assertEquals("3", parameters["duration"])
        assertEquals(setOf("limit", "offset", "type", "duration"), parameters.names())
    }

    @Test
    fun aBlankTypeOrDurationIsNotSent() = runTest {
        val server = server()

        server.search(ProjectSearchParams(type = "", duration = "  "))
        server.search(ProjectSearchParams(type = "  ", duration = ""))

        for (request in server.requests) assertEquals(setOf("limit", "offset"), request.url.parameters.names())
    }

    @Test
    fun theParametersTheAppHasNoUseForAreNeverSent() = runTest {
        val server = server()

        server.search(
            ProjectSearchParams(q = "ML", type = "1", field = "7", duration = "2", tags = listOf("a")),
            PageRequest(20, 20),
        )

        val names = server.requests.single().url.parameters.names()
        assertEquals(setOf("limit", "offset", "q", "type", "field", "duration", "tag"), names)
        for (unused in listOf("status", "mine", "profile_user_id")) assertFalse(unused in names, unused)
    }

    // ---- GET /projects (the catalog) ----

    @Test
    fun theCatalogIsAGetOfTheProjectsPathWithTheBearerTokenAndThePage() = runTest {
        val server = server()

        server.session.projectCatalog.list(ProjectSearchParams(), PageRequest(offset = 100, limit = 100))

        val request = server.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/projects", request.url.encodedPath)
        assertEquals(listOf("Bearer ${ProjectSessionFixture.Token}"), request.authorizations)
        assertEquals("100", request.url.parameters["limit"])
        assertEquals("100", request.url.parameters["offset"])
        assertEquals(setOf("limit", "offset"), request.url.parameters.names())
        assertEquals("", request.bodyText())
    }

    @Test
    fun theCatalogSendsTheFiltersButNeverMineOrTheProfileOrTheStatus() = runTest {
        val server = server()

        server.session.projectCatalog.list(
            ProjectSearchParams(q = "ML", type = "1", field = "12", duration = "1", tags = listOf("a", "b")),
            PageRequest.First,
        )

        val parameters = server.requests.single().url.parameters
        assertEquals(setOf("limit", "offset", "q", "type", "field", "duration", "tag"), parameters.names())
        assertEquals("12", parameters["field"])
        assertEquals(listOf("a", "b"), parameters.getAll("tag"))
        for (unused in listOf("status", "mine", "profile_user_id")) assertFalse(unused in parameters.names(), unused)
    }

    @Test
    fun theCatalogReadsTheSameListAsTheSearch() = runTest {
        val server = server(ProjectSpec.list("\"2\"", ProjectSpec.project("b"), ProjectSpec.project("a")))

        val answer = server.session.projectCatalog.list(ProjectSearchParams(), PageRequest.First)

        assertEquals(listOf("b", "a"), answer.items.map { it.id })
        assertEquals(2L, answer.total)
    }

    @Test
    fun theCatalogFailsLikeTheSearch() = runTest {
        for (status in listOf(400, 403, 500)) {
            val server = ProjectSessionFixture { jsonError(HttpStatusCode.fromValue(status), "причина") }

            val failure = failureOf<RemoteFailure.Http> { server.session.projectCatalog.list(ProjectSearchParams(), PageRequest.First) }

            assertEquals(status, failure.status)
            assertEquals(1, server.requests.size)
        }
        failureOf<RemoteFailure.Malformed> { server("[]").session.projectCatalog.list(ProjectSearchParams(), PageRequest.First) }
        val refused = ProjectSessionFixture { jsonError(HttpStatusCode.Unauthorized, "expired") }
        failureOf<RemoteFailure.Http> { refused.session.projectCatalog.list(ProjectSearchParams(), PageRequest.First) }
        assertEquals(listOf(ProjectSessionFixture.Token), refused.rejected)
        assertTrue(refused.requests.single().url.encodedPath.endsWith("/projects"))
    }

    @Test
    fun theCharactersOfTheTextAreEscapedInTheQuery() = runTest {
        val server = server()

        server.search(ProjectSearchParams(q = "a&b=c d#e"))

        // Read back from the parsed URL, so what arrived is what was typed.
        assertEquals("a&b=c d#e", server.requests.single().url.parameters["q"])
        assertEquals(setOf("limit", "offset", "q"), server.requests.single().url.parameters.names())
    }

    // ---- the answer ----

    @Test
    fun thePageIsReadInTheOrderOfTheServerWithTheTotalAsAString() = runTest {
        val server = server(ProjectSpec.list("\"42\"", ProjectSpec.project("b"), ProjectSpec.FullProject, ProjectSpec.project("a")))

        val answer = server.search()

        assertEquals(listOf("b", "p-1", "a"), answer.items.map { it.id })
        assertEquals(42L, answer.total)
    }

    @Test
    fun theTotalMayBeANumberOrMissing() = runTest {
        assertEquals(7L, server(ProjectSpec.list("7")).search().total)
        assertEquals(0L, server(ProjectSpec.list(null)).search().total)
        assertEquals(0L, server("{}").search().total)
    }

    @Test
    fun nullInsteadOfTheItemsIsNoItems() = runTest {
        assertEquals(ProjectListDto(items = emptyList(), total = 0), server("""{"items":null,"total":null}""").search())
    }

    @Test
    fun aTotalThatIsNotANumberIsMalformed() = runTest {
        failureOf<RemoteFailure.Malformed> { server("""{"items":[],"total":"many"}""").search() }
        failureOf<RemoteFailure.Malformed> { server("""{"items":[],"total":12.5}""").search() }
    }

    @Test
    fun anAnswerThatIsNotAProjectListIsMalformed() = runTest {
        failureOf<RemoteFailure.Malformed> { server("""{"items":{"p-1":true}}""").search() }
        failureOf<RemoteFailure.Malformed> { server("[]").search() }
    }

    // ---- failures ----

    @Test
    fun everyErrorStatusIsAnHttpFailureWithItsStatus() = runTest {
        for (status in listOf(400, 403, 404, 500, 503)) {
            val server = ProjectSessionFixture { jsonError(HttpStatusCode.fromValue(status), "причина") }

            val failure = failureOf<RemoteFailure.Http> { server.search() }

            assertEquals(status, failure.status)
            assertEquals(1, server.requests.size)
        }
    }

    @Test
    fun aRefusedTokenIsReportedExactlyOnce() = runTest {
        val server = ProjectSessionFixture { jsonError(HttpStatusCode.Unauthorized, "expired") }

        failureOf<RemoteFailure.Http> { server.search() }

        assertEquals(listOf(ProjectSessionFixture.Token), server.rejected)
    }

    // ---- the page of a project ----

    @Test
    fun theCatalogReadsAProjectFromTheSamePathAsTheOwnCalls() = runTest {
        val server = ProjectSessionFixture { json(ProjectSpec.FullProject) }

        val viaCatalog = server.session.projectCatalog.get("p-1")
        val viaOwn = server.session.projects.get("p-1")

        assertEquals(viaOwn, viaCatalog)
        assertEquals(listOf("/api/v1/projects/p-1", "/api/v1/projects/p-1"), server.requests.map { it.url.encodedPath })
        assertEquals(listOf(HttpMethod.Get, HttpMethod.Get), server.requests.map { it.method })
    }

    @Test
    fun anIdWithSpecialCharactersIsEscapedInTheCatalogToo() = runTest {
        val server = ProjectSessionFixture { json(ProjectSpec.FullProject) }

        server.session.projectCatalog.get("../x?y")

        assertEquals("/api/v1/projects/..%2Fx%3Fy", server.requests.single().url.encodedPath)
    }
}
