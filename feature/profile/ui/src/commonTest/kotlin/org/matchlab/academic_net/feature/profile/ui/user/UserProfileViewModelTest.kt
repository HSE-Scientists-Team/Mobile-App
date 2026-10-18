package org.matchlab.academic_net.feature.profile.ui.user

import kotlinx.coroutines.CompletableDeferred
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.profile.domain.ReviewsSummary
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserProfileUseCase
import org.matchlab.academic_net.feature.profile.ui.FakeProfileRepository
import org.matchlab.academic_net.feature.profile.ui.SomeoneElse
import org.matchlab.academic_net.feature.profile.ui.installMainDispatcher
import org.matchlab.academic_net.feature.profile.ui.person
import org.matchlab.academic_net.feature.profile.ui.review
import org.matchlab.academic_net.feature.profile.ui.uninstallMainDispatcher
import org.matchlab.academic_net.feature.profile.ui.userProfile
import org.matchlab.academic_net.feature.profile.ui.work
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "09 Профиль коллаборатора": loads once, tells the three outcomes apart and retries only after a failure. */
class UserProfileViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val repository = FakeProfileRepository()

    private fun viewModel(id: UserId = SomeoneElse) = UserProfileViewModel(id, GetUserProfileUseCase(repository))

    @Test
    fun theProfileIsLoadedWhenTheScreenIsCreated() {
        repository.onGetProfile = { userProfile(person = person(name = "Елена"), isViewer = false) }

        val state = viewModel().uiState.value

        assertEquals("Елена", assertIs<UserProfileUiState.Content>(state).profile.user.name)
        assertEquals(listOf("getProfile"), repository.calls)
    }

    @Test
    fun theProfileIsAskedForByTheIdOfTheScreen() {
        var asked: UserId? = null
        repository.onGetProfile = { id ->
            asked = id
            userProfile(isViewer = false)
        }

        viewModel(UserId("elena"))

        assertEquals(UserId("elena"), asked)
    }

    @Test
    fun theScreenIsLoadingWhileTheRequestRuns() {
        val gate = CompletableDeferred<Unit>()
        repository.onGetProfile = { gate.await(); userProfile(isViewer = false) }

        val viewModel = viewModel()
        assertEquals(UserProfileUiState.Loading, viewModel.uiState.value)

        gate.complete(Unit)
        assertIs<UserProfileUiState.Content>(viewModel.uiState.value)
    }

    @Test
    fun aPersonTheServerDoesNotKnowIsNotFound() {
        repository.onGetProfile = { null }

        assertEquals(UserProfileUiState.NotFound, viewModel().uiState.value)
    }

    @Test
    fun aFailureIsAnErrorWithItsCauseAndRetryAsksAgain() {
        val cause = IllegalStateException("no network")
        repository.onGetProfile = { throw cause }
        val viewModel = viewModel()
        assertEquals(UserProfileUiState.Error(cause), viewModel.uiState.value)

        repository.onGetProfile = { userProfile(isViewer = false) }
        viewModel.onRetry()

        assertIs<UserProfileUiState.Content>(viewModel.uiState.value)
        assertEquals(2, repository.calls.size)
    }

    @Test
    fun retryOnAnythingButAnErrorAsksNothing() {
        val viewModel = viewModel()

        viewModel.onRetry()

        assertEquals(1, repository.calls.size)
    }

    @Test
    fun retryShowsTheLoadingAgainWhileItRuns() {
        repository.onGetProfile = { throw IllegalStateException("no network") }
        val viewModel = viewModel()
        val gate = CompletableDeferred<Unit>()
        repository.onGetProfile = { gate.await(); userProfile(isViewer = false) }

        viewModel.onRetry()
        assertEquals(UserProfileUiState.Loading, viewModel.uiState.value)
        viewModel.onRetry() // a second tap while loading does nothing
        gate.complete(Unit)

        assertEquals(2, repository.calls.size)
    }

    @Test
    fun theMessageButtonIsOnlyForSomebodyElsesProfile() {
        repository.onGetProfile = { userProfile(isViewer = false) }
        assertTrue(assertIs<UserProfileUiState.Content>(viewModel().uiState.value).profile.canMessage)

        repository.onGetProfile = { userProfile(isViewer = true) }
        assertFalse(assertIs<UserProfileUiState.Content>(viewModel().uiState.value).profile.canMessage)
    }

    @Test
    fun theModelHidesWhatTheServerDoesNotGive() {
        repository.onGetProfile = {
            userProfile(
                works = listOf(work("w1"), work("w2"), work("w3")),
                worksCount = 7,
                isViewer = false,
                reviews = ReviewsSummary(average = null, count = 0, positiveCount = 0),
                latestReviews = emptyList(),
            )
        }

        val profile = assertIs<UserProfileUiState.Content>(viewModel().uiState.value).profile

        // The works are a preview and the profile says there are more: "Все".
        assertTrue(profile.hasMoreWorks)
        assertNull(profile.rating)
        assertEquals(0, profile.reviewCount)
        assertEquals(emptySet(), profile.interestTypes)
    }

    @Test
    fun theReviewsAreThePreviewOfTheProfile() {
        repository.onGetProfile = {
            userProfile(
                isViewer = false,
                reviews = ReviewsSummary(average = 4.5, count = 12, positiveCount = 10),
                latestReviews = listOf(review("r1"), review("r2")),
            )
        }

        val profile = assertIs<UserProfileUiState.Content>(viewModel().uiState.value).profile

        assertEquals(4.5, profile.rating)
        assertEquals(12, profile.reviewCount)
        assertEquals(listOf("r1", "r2"), profile.reviews.map { it.id.value })
        assertFalse(profile.hasMoreWorks)
    }
}
