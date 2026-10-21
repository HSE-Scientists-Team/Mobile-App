package org.matchlab.academic_net.feature.reviews.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraft
import org.matchlab.academic_net.feature.reviews.domain.ReviewRefusal
import org.matchlab.academic_net.gateway.dto.UserReviewInputDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Sending a review (16): what goes to `POST /users/me/reviews`, and how the answers of the server are told apart. */
class GatewaySendReviewTest {
    private val catalog = FakeProjectCatalog()
    private val reviews = ScriptedReviewsApi()
    private val source = GatewayReviewsDataSource(catalog, reviews, UserId(ViewerId))
    private val target = UserId(TargetId)
    private val draft = ReviewDraft(ProjectId("p-1"), 4, "Отлично", "Довёл до демо")

    // ---- the request ----

    @Test
    fun theDraftGoesAsTheFiveFieldsOfTheApi() = runTest {
        source.sendReview(target, draft)

        assertEquals(listOf(UserReviewInputDto("p-1", TargetId, "Отлично", "Довёл до демо", 4)), reviews.created)
    }

    @Test
    fun theTitleAndTheTextGoTrimmed() = runTest {
        source.sendReview(target, draft.copy(title = "  Отлично\n", text = "\t Довёл до демо  \n"))

        val sent = reviews.created.single()
        assertEquals("Отлично", sent.title)
        assertEquals("Довёл до демо", sent.text)
    }

    @Test
    fun theLineBreaksInsideTheTextStay() = runTest {
        source.sendReview(target, draft.copy(text = "Первая\n\nВторая  строка"))

        assertEquals("Первая\n\nВторая  строка", reviews.created.single().text)
    }

    @Test
    fun everyRatingFromOneToFiveGoes() = runTest {
        for (rating in 1..5) source.sendReview(target, draft.copy(rating = rating))

        assertEquals((1..5).toList(), reviews.created.map { it.rating })
    }

    @Test
    fun theTargetIsTheOneAskedForNotTheViewer() = runTest {
        source.sendReview(UserId("somebody"), draft)

        assertEquals("somebody", reviews.created.single().targetUserId)
    }

    @Test
    fun theSuccessReturnsNothingAndSendsExactlyOneRequest() = runTest {
        source.sendReview(target, draft)

        assertEquals(1, reviews.created.size)
        assertEquals(emptyList(), catalog.listCalls)
        assertEquals(emptyList(), reviews.reads)
    }

    @Test
    fun aDraftWithoutAProjectIsABugOfTheCallerAndSendsNothing() = runTest {
        assertFailsWith<IllegalArgumentException> { source.sendReview(target, draft.copy(projectId = null)) }

        assertEquals(emptyList(), reviews.created)
    }

    @Test
    fun anInvalidDraftIsABugOfTheCallerAndSendsNothing() = runTest {
        val invalid = listOf(
            draft.copy(rating = 0),
            draft.copy(rating = 6),
            draft.copy(title = "   "),
            draft.copy(text = ""),
            draft.copy(title = "a".repeat(256)),
            draft.copy(text = "a".repeat(10_001)),
        )

        for (bad in invalid) assertFailsWith<IllegalArgumentException> { source.sendReview(target, bad) }

        assertEquals(emptyList(), reviews.created)
    }

    // ---- what the server refuses ----

    private suspend fun refusedWith(status: Int, message: String? = "причина"): Throwable {
        reviews.onCreate = { throw RemoteFailure.Http(status, message) }
        return assertFailsWith<Throwable> { source.sendReview(target, draft) }
    }

    @Test
    fun a409IsAnAlreadyWrittenReviewAndKeepsTheHttpFailureAsItsCause() = runTest {
        val refusal = assertIs<ReviewRefusal.AlreadyReviewed>(refusedWith(409))

        val cause = assertIs<RemoteFailure.Http>(refusal.cause)
        assertEquals(409, cause.status)
        assertEquals("причина", cause.serverMessage)
    }

    @Test
    fun a403A404AndA400AreNotAllowedAndKeepTheirCause() = runTest {
        for (status in listOf(403, 404, 400)) {
            val refusal = assertIs<ReviewRefusal.NotAllowed>(refusedWith(status), "status $status")

            assertEquals(status, assertIs<RemoteFailure.Http>(refusal.cause).status)
        }
    }

    @Test
    fun theTextOfTheServerIsNotInTheMessageOfARefusal() = runTest {
        val refusal = assertIs<ReviewRefusal>(refusedWith(403, "internal: user is not a member of project p-1"))

        assertTrue("internal" !in refusal.message.orEmpty() && "p-1" !in refusal.message.orEmpty())
    }

    @Test
    fun otherStatusesStayTheRemoteFailureOfTheServer() = runTest {
        for (status in listOf(401, 402, 405, 408, 410, 422, 429, 500, 502, 503, 504)) {
            val failure = RemoteFailure.Http(status, "x")
            reviews.onCreate = { throw failure }

            val thrown = assertFailsWith<RemoteFailure.Http>("status $status") { source.sendReview(target, draft) }

            assertSame(failure, thrown, "status $status is the failure itself, not a refusal")
        }
    }

    @Test
    fun noConnectionATimeoutAndAMalformedAnswerAreNotRefusals() = runTest {
        for (failure in listOf<RemoteFailure>(RemoteFailure.NoConnection(), RemoteFailure.Timeout(), RemoteFailure.Malformed())) {
            reviews.onCreate = { throw failure }

            val thrown = assertFailsWith<RemoteFailure> { source.sendReview(target, draft) }

            assertSame(failure, thrown)
        }
    }

    @Test
    fun anythingElseThrownIsLeftAlone() = runTest {
        val bug = IllegalStateException("bug")
        reviews.onCreate = { throw bug }

        assertSame(bug, assertFailsWith<IllegalStateException> { source.sendReview(target, draft) })
    }

    @Test
    fun aCancellationPassesThroughUntouched() = runTest {
        reviews.onCreate = { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { source.sendReview(target, draft) }
    }

    @Test
    fun aFailedSendIsNeverRepeatedByTheSourceItself() = runTest {
        reviews.onCreate = { throw RemoteFailure.Http(503) }

        assertFailsWith<RemoteFailure.Http> { source.sendReview(target, draft) }

        assertEquals(1, reviews.created.size, "a repeat of a lost answer would be a 409; the user decides to repeat")
    }

    @Test
    fun theUserMayRepeatAFailedSendAndItGoesThroughAgain() = runTest {
        var attempts = 0
        reviews.onCreate = { if (attempts++ == 0) throw RemoteFailure.NoConnection() else it.toReview() }

        assertFailsWith<RemoteFailure.NoConnection> { source.sendReview(target, draft) }
        source.sendReview(target, draft)

        assertEquals(2, reviews.created.size)
        assertEquals(reviews.created[0], reviews.created[1])
    }

    private fun UserReviewInputDto.toReview() =
        org.matchlab.academic_net.gateway.dto.UserReviewDto(projectId = projectId, targetUserId = targetUserId, rating = rating)
}
