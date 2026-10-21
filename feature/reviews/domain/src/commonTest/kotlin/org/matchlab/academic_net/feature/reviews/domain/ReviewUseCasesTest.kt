package org.matchlab.academic_net.feature.reviews.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.common.result.SubmitResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Instant

/** A [ReviewsRepository] that answers as the test says and records what it was asked. */
internal class FakeReviewsRepository : ReviewsRepository {
    val projectCalls = mutableListOf<UserId>()
    val sent = mutableListOf<Pair<UserId, ReviewDraft>>()
    var onGetReviewableProjects: suspend (UserId) -> List<ReviewableProject> = { emptyList() }
    var onSendReview: suspend (UserId, ReviewDraft) -> Unit = { _, _ -> }

    override suspend fun getReviewableProjects(targetId: UserId): List<ReviewableProject> {
        projectCalls += targetId
        return onGetReviewableProjects(targetId)
    }

    override suspend fun sendReview(targetId: UserId, draft: ReviewDraft) {
        sent += targetId to draft
        onSendReview(targetId, draft)
    }
}

class GetReviewableProjectsUseCaseTest {
    private val repository = FakeReviewsRepository()
    private val target = UserId("target-1")

    @Test
    fun theProjectsOfTheRepositoryComeInTheirOrder() = runTest {
        val projects = listOf(
            ReviewableProject(ProjectId("b"), "B", Instant.fromEpochSeconds(2)),
            ReviewableProject(ProjectId("a"), "A", null),
        )
        repository.onGetReviewableProjects = { projects }

        val result = GetReviewableProjectsUseCase(repository)(target)

        assertEquals(projects, result.getOrThrow())
        assertEquals(listOf(target), repository.projectCalls)
    }

    @Test
    fun noProjectsIsASuccessWithAnEmptyList() = runTest {
        val result = GetReviewableProjectsUseCase(repository)(target)

        assertEquals(emptyList(), result.getOrThrow())
    }

    @Test
    fun aFailedRequestIsAFailedResultWithTheSameCause() = runTest {
        val cause = RemoteFailure.NoConnection()
        repository.onGetReviewableProjects = { throw cause }

        val result = GetReviewableProjectsUseCase(repository)(target)

        assertSame(cause, result.exceptionOrNull())
    }

    @Test
    fun anyOtherExceptionIsAFailedResultToo() = runTest {
        repository.onGetReviewableProjects = { throw IllegalStateException("broken") }

        val result = GetReviewableProjectsUseCase(repository)(target)

        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun aCancellationIsRethrownAndNotAResult() = runTest {
        repository.onGetReviewableProjects = { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { GetReviewableProjectsUseCase(repository)(target) }
    }

    @Test
    fun everyCallAsksTheRepositoryAgain() = runTest {
        val useCase = GetReviewableProjectsUseCase(repository)

        useCase(target)
        useCase(UserId("other"))

        assertEquals(listOf(target, UserId("other")), repository.projectCalls)
    }
}

class SendReviewUseCaseTest {
    private val repository = FakeReviewsRepository()
    private val target = UserId("target-1")
    private val draft = ReviewDraft(ProjectId("p-1"), 5, "Отлично", "Довёл до демо")

    @Test
    fun aValidDraftIsSentAndSucceeds() = runTest {
        val result = SendReviewUseCase(repository)(target, draft)

        assertEquals(SubmitResult.Success(Unit), result)
        assertEquals(listOf(target to draft), repository.sent)
    }

    @Test
    fun theDraftIsSentTrimmed() = runTest {
        SendReviewUseCase(repository)(target, draft.copy(title = "  Отлично \n", text = "\tТекст  "))

        assertEquals(listOf(target to draft.copy(title = "Отлично", text = "Текст")), repository.sent)
    }

    @Test
    fun anInvalidDraftNamesItsMistakesAndSendsNothing() = runTest {
        val result = SendReviewUseCase(repository)(target, ReviewDraft())

        assertEquals(
            SubmitResult.Invalid(
                setOf(ReviewDraftError.ProjectMissing, ReviewDraftError.RatingMissing, ReviewDraftError.TitleBlank, ReviewDraftError.TextBlank),
            ),
            result,
        )
        assertEquals(emptyList(), repository.sent)
    }

    @Test
    fun aDraftThatIsBlankOnlyAfterTrimmingIsInvalid() = runTest {
        val result = SendReviewUseCase(repository)(target, draft.copy(title = "   ", text = "\n"))

        assertEquals(SubmitResult.Invalid(setOf(ReviewDraftError.TitleBlank, ReviewDraftError.TextBlank)), result)
        assertEquals(emptyList(), repository.sent)
    }

    @Test
    fun aDraftThatIsTooLongIsInvalidAndSendsNothing() = runTest {
        val result = SendReviewUseCase(repository)(target, draft.copy(title = "a".repeat(256), text = "b".repeat(10_001)))

        assertEquals(SubmitResult.Invalid(setOf(ReviewDraftError.TitleTooLong, ReviewDraftError.TextTooLong)), result)
        assertEquals(emptyList(), repository.sent)
    }

    @Test
    fun theLimitsThemselvesAreSent() = runTest {
        val edge = draft.copy(rating = 1, title = "a".repeat(255), text = "b".repeat(10_000))

        val result = SendReviewUseCase(repository)(target, edge)

        assertEquals(SubmitResult.Success(Unit), result)
        assertEquals(1, repository.sent.size)
    }

    @Test
    fun aRefusalOfTheServerIsAFailureWithTheRefusalAsItsCause() = runTest {
        val refusal = ReviewRefusal.AlreadyReviewed(RemoteFailure.Http(409, "duplicate"))
        repository.onSendReview = { _, _ -> throw refusal }

        val result = SendReviewUseCase(repository)(target, draft)

        assertSame(refusal, assertIs<SubmitResult.Failure>(result).cause)
    }

    @Test
    fun aFailureOfTheNetworkIsAFailureWithTheRemoteFailureItself() = runTest {
        val failure = RemoteFailure.Http(503)
        repository.onSendReview = { _, _ -> throw failure }

        val result = SendReviewUseCase(repository)(target, draft)

        assertSame(failure, assertIs<SubmitResult.Failure>(result).cause)
    }

    @Test
    fun aFailedSendCanBeRepeatedAndTheSecondTryMaySucceed() = runTest {
        var attempts = 0
        repository.onSendReview = { _, _ -> if (attempts++ == 0) throw RemoteFailure.NoConnection() }
        val useCase = SendReviewUseCase(repository)

        val first = useCase(target, draft)
        val second = useCase(target, draft)

        assertIs<SubmitResult.Failure>(first)
        assertEquals(SubmitResult.Success(Unit), second)
        assertEquals(2, repository.sent.size)
    }

    @Test
    fun aCancellationIsRethrownAndNotAFailure() = runTest {
        repository.onSendReview = { _, _ -> throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { SendReviewUseCase(repository)(target, draft) }
    }
}

class ReviewRefusalTest {
    @Test
    fun aRefusalKeepsTheFailureItCameFrom() {
        val http = RemoteFailure.Http(403, "not a member")

        assertSame(http, ReviewRefusal.NotAllowed(http).cause)
        assertSame(http, ReviewRefusal.AlreadyReviewed(http).cause)
    }

    @Test
    fun aRefusalWithoutACauseHasNone() {
        assertNull(ReviewRefusal.NotAllowed().cause)
        assertNull(ReviewRefusal.AlreadyReviewed().cause)
    }

    @Test
    fun aRefusalNeverCarriesTheServersText() {
        val http = RemoteFailure.Http(409, "секретная причина сервера")

        val refusal = ReviewRefusal.AlreadyReviewed(http)

        assertIs<ReviewRefusal>(refusal)
        assertEquals(false, refusal.message.orEmpty().contains("секретная"), "the text of the server is a diagnostic, not a message")
    }
}
