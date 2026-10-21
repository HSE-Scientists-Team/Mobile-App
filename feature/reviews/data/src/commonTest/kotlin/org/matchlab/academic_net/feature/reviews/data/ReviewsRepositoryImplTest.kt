package org.matchlab.academic_net.feature.reviews.data

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraft
import org.matchlab.academic_net.feature.reviews.domain.ReviewRefusal
import org.matchlab.academic_net.feature.reviews.domain.ReviewableProject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Instant

/** The repository adds nothing to its data source: what it gets goes through, and so does what goes wrong. */
class ReviewsRepositoryImplTest {
    private val dataSource = FakeReviewsDataSource()
    private val repository = ReviewsRepositoryImpl(dataSource)
    private val target = UserId(TargetId)
    private val draft = ReviewDraft(ProjectId("p-1"), 5, "Отлично", "Текст")

    @Test
    fun theReviewableProjectsComeFromTheDataSourceInItsOrder() = runTest {
        val projects = listOf(
            ReviewableProject(ProjectId("b"), "B", Instant.fromEpochSeconds(2)),
            ReviewableProject(ProjectId("a"), "A", null),
        )
        dataSource.onGetReviewableProjects = { projects }

        assertEquals(projects, repository.getReviewableProjects(target))
        assertEquals(listOf(target), dataSource.projectCalls)
    }

    @Test
    fun aFailureOfTheDataSourceIsThrownAsItIs() = runTest {
        val failure = RemoteFailure.NoConnection()
        dataSource.onGetReviewableProjects = { throw failure }
        dataSource.onSendReview = { _, _ -> throw failure }

        assertSame(failure, assertFailsWith<RemoteFailure.NoConnection> { repository.getReviewableProjects(target) })
        assertSame(failure, assertFailsWith<RemoteFailure.NoConnection> { repository.sendReview(target, draft) })
    }

    @Test
    fun theDraftIsSentToTheDataSourceUnchanged() = runTest {
        val untrimmed = draft.copy(title = "  x ")

        repository.sendReview(target, untrimmed)

        assertEquals(listOf(target to untrimmed), dataSource.sent, "trimming is the data source's and the use case's job, not the repository's")
    }

    @Test
    fun aRefusalPassesThroughUntouched() = runTest {
        val refusal = ReviewRefusal.AlreadyReviewed(RemoteFailure.Http(409))
        dataSource.onSendReview = { _, _ -> throw refusal }

        assertSame(refusal, assertFailsWith<ReviewRefusal.AlreadyReviewed> { repository.sendReview(target, draft) })
    }
}
