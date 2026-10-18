package org.matchlab.academic_net.feature.profile.domain.usecase

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.feature.profile.domain.FakeProfileRepository
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.ReviewsPage
import org.matchlab.academic_net.feature.profile.domain.UserWorks
import org.matchlab.academic_net.feature.profile.domain.review
import org.matchlab.academic_net.feature.profile.domain.userProfile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** What the screens of other people's profiles get from the repository: a result with a state for each case. */
class ProfileUseCasesTest {
    private val repository = FakeProfileRepository()
    private val someone = UserId("someone")

    @Test
    fun aProfileTheServerHasIsFound() = runTest {
        val profile = userProfile()
        repository.onGetProfile = { profile }

        assertEquals(LoadResult.Found(profile), GetUserProfileUseCase(repository)(someone))
    }

    @Test
    fun aPersonTheServerDoesNotKnowIsNotFoundAndNotAFailure() = runTest {
        repository.onGetProfile = { null }
        repository.onGetWorks = { null }
        repository.onGetUserReviews = { null }
        repository.onGetReviewsPage = { _, _ -> null }
        repository.onGetReview = { _, _ -> null }

        assertEquals(LoadResult.NotFound, GetUserProfileUseCase(repository)(someone))
        assertEquals(LoadResult.NotFound, GetUserWorksUseCase(repository)(someone))
        assertEquals(LoadResult.NotFound, GetUserReviewsUseCase(repository)(someone))
        assertEquals(LoadResult.NotFound, GetReviewsPageUseCase(repository)(someone, offset = 20))
        assertEquals(LoadResult.NotFound, GetReviewUseCase(repository)(someone, ReviewId("r")))
    }

    @Test
    fun aFailedRequestIsAFailureWithItsCause() = runTest {
        val cause = IllegalStateException("no network")
        repository.onGetProfile = { throw cause }
        repository.onGetWorks = { throw cause }
        repository.onGetUserReviews = { throw cause }
        repository.onGetReviewsPage = { _, _ -> throw cause }
        repository.onGetReview = { _, _ -> throw cause }

        assertEquals(LoadResult.Failure(cause), GetUserProfileUseCase(repository)(someone))
        assertEquals(LoadResult.Failure(cause), GetUserWorksUseCase(repository)(someone))
        assertEquals(LoadResult.Failure(cause), GetUserReviewsUseCase(repository)(someone))
        assertEquals(LoadResult.Failure(cause), GetReviewsPageUseCase(repository)(someone, offset = 0))
        assertEquals(LoadResult.Failure(cause), GetReviewUseCase(repository)(someone, ReviewId("r")))
    }

    @Test
    fun aCancelledRequestIsNotAFailure() = runTest {
        repository.onGetProfile = { throw CancellationException("screen left") }

        assertFailsWith<CancellationException> { GetUserProfileUseCase(repository)(someone) }
    }

    @Test
    fun theWorksAndTheReviewsAreAskedForTheRightPerson() = runTest {
        var worksOf: UserId? = null
        var pageOf: Pair<UserId, Int>? = null
        repository.onGetWorks = { id ->
            worksOf = id
            UserWorks(emptyList(), isViewer = true)
        }
        repository.onGetReviewsPage = { id, offset ->
            pageOf = id to offset
            ReviewsPage(listOf(review("r7")), hasMore = true)
        }

        val works = GetUserWorksUseCase(repository)(someone)
        val page = GetReviewsPageUseCase(repository)(someone, offset = 40)

        assertEquals(LoadResult.Found(UserWorks(emptyList(), isViewer = true)), works)
        assertEquals(someone, worksOf)
        assertEquals(someone to 40, pageOf)
        assertEquals(LoadResult.Found(ReviewsPage(listOf(review("r7")), hasMore = true)), page)
    }
}
