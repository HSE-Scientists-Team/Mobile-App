package org.matchlab.academic_net.gateway

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.gateway.contract.ProjectMineFilter
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.internal.GatewayJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * `mine` and `status` of `GET /api/v1/projects` (the viewer's completed projects of the review form) and
 * `completed_at_unix` of a project. The search sends neither filter; the catalog list sends them when they are set.
 */
class ProjectViewerFiltersApiTest {
    private fun server(body: String = ProjectSpec.list(total = "0")) = ProjectSessionFixture { json(body) }

    // ---- GET /projects ----

    @Test
    fun theCatalogSendsMineAndStatusWhenSet() = runTest {
        val server = server()

        server.session.projectCatalog.list(
            ProjectSearchParams(mine = ProjectMineFilter.Owner, status = "completed"),
            PageRequest(offset = 100, limit = 100),
        )

        val request = server.requests.single()
        assertEquals("/api/v1/projects", request.url.encodedPath)
        assertEquals(setOf("limit", "offset", "mine", "status"), request.url.parameters.names())
        assertEquals("owner", request.url.parameters["mine"])
        assertEquals("completed", request.url.parameters["status"])
        assertEquals("100", request.url.parameters["limit"])
        assertEquals("100", request.url.parameters["offset"])
        assertEquals(listOf("Bearer ${ProjectSessionFixture.Token}"), request.authorizations)
    }

    @Test
    fun theParticipantFilterGoesAsItsWord() = runTest {
        val server = server()

        server.session.projectCatalog.list(ProjectSearchParams(mine = ProjectMineFilter.Participant), PageRequest.First)

        assertEquals("participant", server.requests.single().url.parameters["mine"])
        assertFalse("status" in server.requests.single().url.parameters.names())
    }

    @Test
    fun theWordsOfTheFilterAreThoseOfTheSpec() {
        assertEquals("owner", ProjectMineFilter.Owner)
        assertEquals("participant", ProjectMineFilter.Participant)
    }

    @Test
    fun aStatusAloneIsSentWithoutMine() = runTest {
        val server = server()

        server.session.projectCatalog.list(ProjectSearchParams(status = "completed"), PageRequest.First)

        assertEquals(setOf("limit", "offset", "status"), server.requests.single().url.parameters.names())
    }

    @Test
    fun nullAndBlankFiltersAreNotSent() = runTest {
        val server = server()

        server.session.projectCatalog.list(ProjectSearchParams(mine = null, status = null), PageRequest.First)
        server.session.projectCatalog.list(ProjectSearchParams(mine = "", status = ""), PageRequest.First)
        server.session.projectCatalog.list(ProjectSearchParams(mine = "  ", status = "\t"), PageRequest.First)

        for (request in server.requests) assertEquals(setOf("limit", "offset"), request.url.parameters.names())
    }

    @Test
    fun theFiltersTravelWithTheOtherParametersOfTheCatalog() = runTest {
        val server = server()

        server.session.projectCatalog.list(
            ProjectSearchParams(q = "ML", type = "scientific", field = "Biology", duration = "up_to_3_months", tags = listOf("a", "b"), mine = "owner", status = "completed"),
            PageRequest(20, 20),
        )

        val parameters = server.requests.single().url.parameters
        assertEquals(setOf("limit", "offset", "q", "type", "field", "duration", "tag", "mine", "status"), parameters.names())
        assertEquals(listOf("a", "b"), parameters.getAll("tag"))
    }

    @Test
    fun theSearchNeverSendsMineOrStatusWhateverTheParamsHold() = runTest {
        val server = server()

        server.session.projectCatalog.search(
            ProjectSearchParams(q = "ML", mine = "owner", status = "completed"),
            PageRequest.First,
        )

        val names = server.requests.single().url.parameters.names()
        assertEquals("/api/v1/search/projects", server.requests.single().url.encodedPath)
        assertEquals(setOf("limit", "offset", "q"), names)
    }

    @Test
    fun theLimitsOfThePageStillHold() = runTest {
        val server = server()

        assertFailsWith<IllegalArgumentException> { PageRequest(offset = 0, limit = 101) }
        server.session.projectCatalog.list(ProjectSearchParams(mine = "owner"), PageRequest(offset = 10_000, limit = 100))

        assertEquals("10000", server.requests.single().url.parameters["offset"])
    }

    // ---- completed_at_unix ----

    private fun read(json: String): ProjectDto = GatewayJson.decodeFromString(json)

    @Test
    fun theCompletionTimeIsReadFromAString() {
        assertEquals(1_750_000_000L, read("""{"id":"p","completed_at_unix":"1750000000"}""").completedAtUnix)
    }

    @Test
    fun theCompletionTimeIsReadFromANumber() {
        assertEquals(1_750_000_000L, read("""{"id":"p","completed_at_unix":1750000000}""").completedAtUnix)
    }

    @Test
    fun aProjectWithoutACompletionTimeHasZero() {
        assertEquals(0L, read("""{"id":"p"}""").completedAtUnix)
        assertEquals(0L, read("""{"id":"p","completed_at_unix":null}""").completedAtUnix)
    }

    @Test
    fun theCompletionTimeComesWithAProjectOfAListAndOfAGet() = runTest {
        val project = """{"id":"p-1","owner_user_id":"o","details":{"title":"T"},"completed_at_unix":"1750000000"}"""
        val server = ProjectSessionFixture { json(if (it.url.encodedPath.endsWith("/p-1")) project else ProjectSpec.list("1", project)) }

        val listed = server.session.projectCatalog.list(ProjectSearchParams(mine = "owner"), PageRequest.First).items.single()
        val got = server.session.projectCatalog.get("p-1")

        assertEquals(1_750_000_000L, listed.completedAtUnix)
        assertEquals("T", listed.details.title)
        assertEquals(1_750_000_000L, got.completedAtUnix)
    }
}
