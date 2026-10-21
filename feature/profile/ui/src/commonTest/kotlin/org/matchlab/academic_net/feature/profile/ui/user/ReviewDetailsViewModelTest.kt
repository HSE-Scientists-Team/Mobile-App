package org.matchlab.academic_net.feature.profile.ui.user

import kotlinx.coroutines.CompletableDeferred
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.usecase.GetReviewUseCase
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

/** "09d Отзыв — подробно": the review comes from the pages already loaded or from a search (API-GAP G14). */
class ReviewDetailsViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val repository = FakeProfileRepository()

    private fun viewModel(review: String = "r7") =
        ReviewDetailsViewModel(SomeoneElse, ReviewId(review), GetReviewUseCase(repository))

    @Test
    fun theReviewIsAskedForByThePersonAndTheId() {
        var asked: Pair<UserId, ReviewId>? = null
        repository.onGetReview = { user, id ->
            asked = user to id
            review(id.value, text = "Полный текст")
        }

        val state = viewModel("r9").uiState.value

        assertEquals("Полный текст", assertIs<ReviewDetailsUiState.Content>(state).review.text)
        assertEquals(SomeoneElse to ReviewId("r9"), asked)
    }

    @Test
    fun theScreenIsLoadingUntilTheAnswerArrives() {
        val gate = CompletableDeferred<Unit>()
        repository.onGetReview = { _, id -> gate.await(); review(id.value) }

        val viewModel = viewModel()
        assertEquals(ReviewDetailsUiState.Loading, viewModel.uiState.value)
        gate.complete(Unit)

        assertIs<ReviewDetailsUiState.Content>(viewModel.uiState.value)
    }

    @Test
    fun aReviewThatIsNotFoundIsNotAnError() {
        repository.onGetReview = { _, _ -> null }

        assertEquals(ReviewDetailsUiState.NotFound, viewModel().uiState.value)
    }

    @Test
    fun aFailureIsAnErrorAndRetryAsksAgainOnlyFromThere() {
        val cause = IllegalStateException("no network")
        repository.onGetReview = { _, _ -> throw cause }
        val viewModel = viewModel()
        assertEquals(ReviewDetailsUiState.Error(cause), viewModel.uiState.value)

        repository.onGetReview = { _, id -> review(id.value) }
        viewModel.onRetry()
        assertIs<ReviewDetailsUiState.Content>(viewModel.uiState.value)
        viewModel.onRetry() // nothing to retry on a loaded screen

        assertEquals(2, repository.calls.size)
    }
}
