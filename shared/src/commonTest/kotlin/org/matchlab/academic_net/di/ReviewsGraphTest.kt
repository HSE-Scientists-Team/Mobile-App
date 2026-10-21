package org.matchlab.academic_net.di

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraft
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraftError
import org.matchlab.academic_net.feature.reviews.domain.ReviewRefusal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The review of a person (16) from its use cases down to the HTTP calls of the Gateway (a mock engine): the wiring of the session
 * graph that no layer's own tests see. The answers are the shapes `docs/openapi.yaml` gives.
 */
class ReviewsGraphTest {
    private val viewer = ServerUserId
    private val target = "a1b2c3d4-0000-4000-8000-000000000002"
    private val session = Session(UserId(viewer), StudentEmail, AccessToken("token-1"))

    private fun projectJson(id: String, owner: String, members: String, completedAt: String, title: String = "Проект $id") =
        """{"id":"$id","owner_user_id":"$owner","details":{"title":"$title"},"status":"completed","revision":3,""" +
            """"members":[$members],"completed_at_unix":$completedAt}"""

    private fun memberJson(user: String, status: String = "active") = """{"user_id":"$user","role_id":"r-1","status":"$status"}"""

    private fun listJson(vararg projects: String) = """{"items":[${projects.joinToString(",")}],"total":"${projects.size}"}"""

    private val reviewJson =
        """{"id":"rev-1","author_user_id":"$viewer","project_id":"p-4","target_user_id":"$target","title":"Был","text":"Текст","rating":5}"""

    /** A server where the viewer owns p-1 (shared) and p-3 (not), takes part in p-2 (the target owns it) and p-4 (reviewed already). */
    private fun server() = FakeServer().apply {
        onOther = { arrival ->
            when {
                arrival.call == "GET /api/v1/projects" && arrival.queryValues("mine") == listOf("owner") ->
                    json(
                        listJson(
                            projectJson("p-1", viewer, memberJson(target), "\"1750000000\""),
                            projectJson("p-3", viewer, memberJson("someone-else"), "\"1755000000\""),
                        ),
                    )
                arrival.call == "GET /api/v1/projects" && arrival.queryValues("mine") == listOf("participant") ->
                    json(
                        listJson(
                            projectJson("p-2", target, "", "1760000000", title = "Цифровой двойник"),
                            projectJson("p-4", target, "", "1740000000"),
                        ),
                    )
                arrival.call == "GET /api/v1/users/$target/reviews" -> json("""{"items":[$reviewJson]}""")
                arrival.call == "POST /api/v1/users/me/reviews" -> json(reviewJson, HttpStatusCode.Created)
                else -> jsonError(HttpStatusCode.NotFound, "not found")
            }
        }
    }

    private fun graph(server: FakeServer) = AppLaunch(server).sessionGraph(session)

    private val draft = ReviewDraft(ProjectId("p-1"), 5, "  Отлично ", "\nДовёл до демо ")

    // ---- building ----

    @Test
    fun theUseCasesAndTheRepositoryAreBuiltWithoutARequest() {
        val server = server()
        val graph = graph(server)

        graph.dependencies.reviews.getReviewableProjects
        graph.dependencies.reviews.sendReview

        assertEquals(emptyList(), server.calls)
    }

    @Test
    fun theFormGetsOneSetOfDependenciesForTheSession() {
        val graph = graph(server())

        // SessionDependencies is session-scoped, so every screen of the session shares the use cases of the form.
        assertSame(graph.dependencies.reviews, graph.dependencies.reviews)
        assertSame(graph.dependencies.reviews.getReviewableProjects, graph.dependencies.reviews.getReviewableProjects)
    }

    // ---- the projects of the form ----

    @Test
    fun theProjectsToReviewAreTheSharedCompletedOnesNotReviewedYetNewestFirst() = runTest {
        val server = server()

        val projects = graph(server).dependencies.reviews.getReviewableProjects(UserId(target)).getOrThrow()

        assertEquals(listOf("p-2", "p-1"), projects.map { it.id.value })
        assertEquals("Цифровой двойник", projects.first().title)
        assertEquals(Instant.fromEpochSeconds(1_760_000_000), projects.first().completedAt)
        assertEquals(Instant.fromEpochSeconds(1_750_000_000), projects.last().completedAt)
    }

    @Test
    fun theThreeReadsCarryTheTokenOfTheSessionAndTheFiltersOfTheSpec() = runTest {
        val server = server()

        graph(server).dependencies.reviews.getReviewableProjects(UserId(target)).getOrThrow()

        assertEquals(
            setOf("GET /api/v1/projects", "GET /api/v1/users/$target/reviews"),
            server.calls.toSet(),
        )
        assertEquals(3, server.arrivals.size)
        assertTrue(server.arrivals.all { it.authorization == listOf("Bearer token-1") })
        val lists = server.requests("GET /api/v1/projects")
        assertEquals(setOf(listOf("owner"), listOf("participant")), lists.map { it.queryValues("mine") }.toSet())
        for (request in lists) {
            assertEquals(listOf("completed"), request.queryValues("status"))
            assertEquals(listOf("100"), request.queryValues("limit"))
            assertEquals(listOf("0"), request.queryValues("offset"))
        }
        val reviews = server.requests("GET /api/v1/users/$target/reviews").single()
        assertEquals(listOf("100"), reviews.queryValues("limit"))
        assertEquals(listOf("0"), reviews.queryValues("offset"))
    }

    @Test
    fun theViewerOfTheSessionIsTheAuthorWhoseReviewsAreHidden() = runTest {
        // p-4 is reviewed by the user of the session (author_user_id == ServerUserId): p-4 is not offered.
        val projects = graph(server()).dependencies.reviews.getReviewableProjects(UserId(target)).getOrThrow()

        assertTrue(projects.none { it.id.value == "p-4" })
    }

    @Test
    fun askingAboutTheUserOfTheSessionIsAnEmptyListWithoutARequest() = runTest {
        val server = server()

        val projects = graph(server).dependencies.reviews.getReviewableProjects(UserId(viewer)).getOrThrow()

        assertEquals(emptyList(), projects)
        assertEquals(emptyList(), server.calls)
    }

    @Test
    fun aFailedReadIsAFailedResultWithTheRemoteFailure() = runTest {
        val server = server().apply {
            val before = onOther
            onOther = { arrival ->
                if (arrival.call == "GET /api/v1/users/$target/reviews") jsonError(HttpStatusCode.InternalServerError) else before(arrival)
            }
        }

        val result = graph(server).dependencies.reviews.getReviewableProjects(UserId(target))

        assertEquals(500, assertIs<RemoteFailure.Http>(result.exceptionOrNull()).status)
    }

    @Test
    fun aClosedSessionAsksNothingAndIsCancelled() = runTest {
        val server = server()
        val graph = graph(server)
        graph.close()

        assertFailsWith<CancellationException> { graph.dependencies.reviews.getReviewableProjects(UserId(target)) }
        assertFailsWith<CancellationException> { graph.dependencies.reviews.sendReview(UserId(target), draft) }

        assertEquals(emptyList(), server.calls, "the session client of a closed session sends nothing")
    }

    // ---- sending ----

    @Test
    fun aReviewIsPostedTrimmedWithTheFiveFieldsAndTheToken() = runTest {
        val server = server()

        val result = graph(server).dependencies.reviews.sendReview(UserId(target), draft)

        assertEquals(SubmitResult.Success(Unit), result)
        val post = server.requests("POST /api/v1/users/me/reviews").single()
        assertEquals(listOf("Bearer token-1"), post.authorization)
        assertEquals(
            """{"project_id":"p-1","target_user_id":"$target","title":"Отлично","text":"Довёл до демо","rating":5}""",
            post.body,
        )
        assertEquals(1, server.arrivals.size, "sending reads nothing first")
    }

    @Test
    fun anInvalidReviewSendsNothingAndNamesItsFields() = runTest {
        val server = server()

        val result = graph(server).dependencies.reviews.sendReview(UserId(target), ReviewDraft(projectId = ProjectId("p-1"), rating = 0, title = " ", text = "x"))

        assertEquals(SubmitResult.Invalid(setOf(ReviewDraftError.RatingMissing, ReviewDraftError.TitleBlank)), result)
        assertEquals(emptyList(), server.calls)
    }

    @Test
    fun a409IsAnAlreadyReviewedRefusalAndIsNotRepeated() = runTest {
        val server = serverAnsweringThePostWith(HttpStatusCode.Conflict)

        val result = graph(server).dependencies.reviews.sendReview(UserId(target), draft)

        assertIs<ReviewRefusal.AlreadyReviewed>(assertIs<SubmitResult.Failure>(result).cause)
        assertEquals(1, server.requests("POST /api/v1/users/me/reviews").size)
    }

    @Test
    fun a403A404AndA400AreNotAllowed() = runTest {
        for (status in listOf(HttpStatusCode.Forbidden, HttpStatusCode.NotFound, HttpStatusCode.BadRequest)) {
            val server = serverAnsweringThePostWith(status)

            val result = graph(server).dependencies.reviews.sendReview(UserId(target), draft)

            assertIs<ReviewRefusal.NotAllowed>(assertIs<SubmitResult.Failure>(result).cause, "status $status")
        }
    }

    @Test
    fun aFailureOfTheServerIsTheRemoteFailureAndMayBeRepeated() = runTest {
        val server = serverAnsweringThePostWith(HttpStatusCode.ServiceUnavailable)
        val graph = graph(server)

        val first = graph.dependencies.reviews.sendReview(UserId(target), draft)
        val second = graph.dependencies.reviews.sendReview(UserId(target), draft)

        assertEquals(503, assertIs<RemoteFailure.Http>(assertIs<SubmitResult.Failure>(first).cause).status)
        assertIs<SubmitResult.Failure>(second)
        assertEquals(2, server.requests("POST /api/v1/users/me/reviews").size, "each press of the user is one request")
    }

    private fun serverAnsweringThePostWith(status: HttpStatusCode) = server().apply {
        val before = onOther
        onOther = { arrival ->
            if (arrival.call == "POST /api/v1/users/me/reviews") jsonError(status) else before(arrival)
        }
    }
}
