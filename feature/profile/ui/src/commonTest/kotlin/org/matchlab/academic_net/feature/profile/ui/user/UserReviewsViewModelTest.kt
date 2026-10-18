package org.matchlab.academic_net.feature.profile.ui.user

import kotlinx.coroutines.CompletableDeferred
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewsPage
import org.matchlab.academic_net.feature.profile.domain.ReviewsSummary
import org.matchlab.academic_net.feature.profile.domain.UserReviews
import org.matchlab.academic_net.feature.profile.domain.usecase.GetReviewsPageUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserReviewsUseCase
import org.matchlab.academic_net.feature.profile.ui.FakeProfileRepository
import org.matchlab.academic_net.feature.profile.ui.SomeoneElse
import org.matchlab.academic_net.feature.profile.ui.installMainDispatcher
import org.matchlab.academic_net.feature.profile.ui.review
import org.matchlab.academic_net.feature.profile.ui.uninstallMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "09b Отзывы о пользователе": the numbers and the first page, and the following pages that the end of the list asks for
 * (more than once for the same end), one request at a time.
 */
class UserReviewsViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val repository = FakeProfileRepository()

    private fun reviews(count: Int, from: Int = 1): List<Review> = List(count) { review("r${from + it}") }

    private fun firstPage(items: List<Review>, hasMore: Boolean, summary: ReviewsSummary = ReviewsSummary(4.5, 12, 10)) {
        repository.onGetUserReviews = { UserReviews(summary, ReviewsPage(items, hasMore)) }
    }

    private fun viewModel() = UserReviewsViewModel(
        userId = SomeoneElse,
        getUserReviews = GetUserReviewsUseCase(repository),
        getReviewsPage = GetReviewsPageUseCase(repository),
    )

    private fun UserReviewsViewModel.content() = assertIs<UserReviewsUiState.Content>(uiState.value)

    // ---- the first page ----

    @Test
    fun theNumbersAndTheFirstPageAreShown() {
        firstPage(reviews(3), hasMore = true, summary = ReviewsSummary(4.5, 12, 10))

        val content = viewModel().content()

        assertEquals(ReviewsSummary(4.5, 12, 10), content.summary)
        assertEquals(listOf("r1", "r2", "r3"), content.items.map { it.id.value })
        assertTrue(content.hasMore)
        assertEquals(false, content.isLoadingMore)
        assertNull(content.loadMoreFailure)
    }

    @Test
    fun aPersonWithoutReviewsHasAnEmptyContentNotAnError() {
        firstPage(emptyList(), hasMore = false, summary = ReviewsSummary(null, 0, 0))

        val content = viewModel().content()

        assertEquals(emptyList(), content.items)
        assertNull(content.summary.average)
    }

    @Test
    fun aReviewThatCameTwiceIsShownOnce() {
        firstPage(listOf(review("r1"), review("r2"), review("r1")), hasMore = false)

        assertEquals(listOf("r1", "r2"), viewModel().content().items.map { it.id.value })
    }

    @Test
    fun aPersonTheServerDoesNotKnowIsNotFound() {
        repository.onGetUserReviews = { null }

        assertEquals(UserReviewsUiState.NotFound, viewModel().uiState.value)
    }

    @Test
    fun aFailureOfTheFirstPageIsAnErrorAndRetryAsksAgain() {
        val cause = IllegalStateException("no network")
        repository.onGetUserReviews = { throw cause }
        val viewModel = viewModel()
        assertEquals(UserReviewsUiState.Error(cause), viewModel.uiState.value)

        firstPage(reviews(2), hasMore = false)
        viewModel.onRetry()

        assertEquals(2, viewModel.content().items.size)
    }

    @Test
    fun retryOnAScreenWithoutAnErrorAsksNothing() {
        firstPage(reviews(2), hasMore = false)
        val viewModel = viewModel()

        viewModel.onRetry()

        assertEquals(listOf("getUserReviews"), repository.calls)
    }

    // ---- the following pages ----

    @Test
    fun theNextPageStartsWhereTheShownReviewsEnd() {
        firstPage(reviews(3), hasMore = true)
        repository.onGetReviewsPage = { _, offset -> ReviewsPage(reviews(2, from = offset + 1), hasMore = false) }
        val viewModel = viewModel()

        viewModel.onLoadMore()

        assertEquals(listOf(3), repository.reviewPageOffsets)
        val content = viewModel.content()
        assertEquals(listOf("r1", "r2", "r3", "r4", "r5"), content.items.map { it.id.value })
        assertEquals(false, content.hasMore)
        assertEquals(false, content.isLoadingMore)
    }

    @Test
    fun theFooterSaysLoadingWhileThePageIsOnItsWayAndTheEndIsNotAskedForTwice() {
        firstPage(reviews(3), hasMore = true)
        val gate = CompletableDeferred<Unit>()
        repository.onGetReviewsPage = { _, offset -> gate.await(); ReviewsPage(reviews(2, offset + 1), hasMore = true) }
        val viewModel = viewModel()

        viewModel.onLoadMore()
        assertTrue(viewModel.content().isLoadingMore)
        viewModel.onLoadMore() // the screen asks again for the same end
        viewModel.onLoadMore()
        gate.complete(Unit)

        assertEquals(listOf(3), repository.reviewPageOffsets)
        assertEquals(false, viewModel.content().isLoadingMore)
        assertTrue(viewModel.content().hasMore)
    }

    @Test
    fun nothingIsAskedForOnceTheListIsOver() {
        firstPage(reviews(3), hasMore = false)
        val viewModel = viewModel()

        viewModel.onLoadMore()

        assertEquals(emptyList(), repository.reviewPageOffsets)
    }

    @Test
    fun loadingMoreWithoutAContentAsksNothing() {
        repository.onGetUserReviews = { throw IllegalStateException("no network") }
        val viewModel = viewModel()

        viewModel.onLoadMore()
        viewModel.onRetryLoadMore()

        assertEquals(emptyList(), repository.reviewPageOffsets)
    }

    @Test
    fun aReviewThatShiftedIntoTheNextPageIsNotShownTwice() {
        firstPage(reviews(3), hasMore = true)
        // A new review arrived at the top meanwhile: the last one of the first page is the first of the second.
        repository.onGetReviewsPage = { _, _ -> ReviewsPage(reviews(3, from = 3), hasMore = false) }
        val viewModel = viewModel()

        viewModel.onLoadMore()

        assertEquals(listOf("r1", "r2", "r3", "r4", "r5"), viewModel.content().items.map { it.id.value })
    }

    // ---- a page that fails ----

    @Test
    fun aFailedPageKeepsTheListAndStopsAskingUntilRetry() {
        firstPage(reviews(3), hasMore = true)
        val cause = IllegalStateException("no network")
        repository.onGetReviewsPage = { _, _ -> throw cause }
        val viewModel = viewModel()

        viewModel.onLoadMore()

        val failed = viewModel.content()
        assertEquals(3, failed.items.size)
        assertEquals(cause, failed.loadMoreFailure)
        assertEquals(false, failed.isLoadingMore)
        assertTrue(failed.hasMore)

        // The end of the list is still near, and the screen asks again: nothing happens until "Повторить".
        viewModel.onLoadMore()
        assertEquals(listOf(3), repository.reviewPageOffsets)
    }

    @Test
    fun retryUnderTheListAsksTheSamePageAgain() {
        firstPage(reviews(3), hasMore = true)
        repository.onGetReviewsPage = { _, _ -> throw IllegalStateException("no network") }
        val viewModel = viewModel()
        viewModel.onLoadMore()

        repository.onGetReviewsPage = { _, offset -> ReviewsPage(reviews(2, offset + 1), hasMore = false) }
        viewModel.onRetryLoadMore()

        assertEquals(listOf(3, 3), repository.reviewPageOffsets)
        val content = viewModel.content()
        assertEquals(5, content.items.size)
        assertNull(content.loadMoreFailure)
    }

    @Test
    fun retryUnderTheListWhileAPageRunsOrWithoutAFailureDoesNothing() {
        firstPage(reviews(3), hasMore = true)
        val viewModel = viewModel()

        viewModel.onRetryLoadMore() // no failure to retry

        assertEquals(emptyList(), repository.reviewPageOffsets)
    }

    @Test
    fun aPersonWhoDisappearedMeanwhileEndsTheList() {
        firstPage(reviews(3), hasMore = true)
        repository.onGetReviewsPage = { _, _ -> null }
        val viewModel = viewModel()

        viewModel.onLoadMore()

        val content = viewModel.content()
        assertEquals(3, content.items.size)
        assertEquals(false, content.hasMore)
        assertEquals(false, content.isLoadingMore)
        assertNull(content.loadMoreFailure)
    }
}
