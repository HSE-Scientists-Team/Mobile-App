package org.matchlab.academic_net.feature.profile.ui.user

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.ReviewerRole
import org.matchlab.academic_net.feature.profile.domain.VerificationStatus
import org.matchlab.academic_net.feature.profile.ui.Arjun
import org.matchlab.academic_net.feature.profile.ui.FlakySource
import org.matchlab.academic_net.feature.profile.ui.Me
import org.matchlab.academic_net.feature.profile.ui.TestGraph
import org.matchlab.academic_net.feature.profile.ui.installMainDispatcher
import org.matchlab.academic_net.feature.profile.ui.mockSource
import org.matchlab.academic_net.feature.profile.ui.uninstallMainDispatcher

class UserProfileViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    @Test
    fun showsTheProfileOfAnotherPerson() = runTest {
        val graph = TestGraph(mockSource())

        val viewModel = UserProfileViewModel(Arjun, graph.getUserProfile)

        val profile = assertIs<UserProfileUiState.Content>(viewModel.uiState.value).profile
        assertEquals("Арджун Мехта", profile.user.name)
        assertTrue(profile.user.isVerified)
        assertEquals("Stanford · 3 курс бакалавриата · Computer Science", profile.subtitle)
        assertEquals(setOf(ProjectType.Scientific), profile.interestTypes)
        assertEquals(listOf("Python", "TensorFlow", "PyTorch", "OpenCV"), profile.skills)
        assertTrue(profile.canMessage)
    }

    @Test
    fun previewsWorksAndReviewsAndSaysThereIsMore() = runTest {
        val graph = TestGraph(mockSource())

        val viewModel = UserProfileViewModel(Arjun, graph.getUserProfile)

        val profile = assertIs<UserProfileUiState.Content>(viewModel.uiState.value).profile
        assertEquals(3, profile.works.size)
        assertTrue(profile.hasMoreWorks)
        assertEquals(2, profile.reviews.size)
        assertEquals(12, profile.reviewCount)
        assertEquals(4.75, profile.rating)
    }

    @Test
    fun otherPeopleSeeNoPendingState() = runTest {
        val graph = TestGraph(mockSource())

        val viewModel = UserProfileViewModel(Arjun, graph.getUserProfile)

        val profile = assertIs<UserProfileUiState.Content>(viewModel.uiState.value).profile
        val statuses = profile.works.map { it.status } + profile.documents.map { it.status }
        assertTrue(statuses.none { it == VerificationStatus.Pending || it == VerificationStatus.Rejected })
    }

    @Test
    fun ownProfileCannotBeMessaged() = runTest {
        val graph = TestGraph(mockSource())

        val viewModel = UserProfileViewModel(Me, graph.getUserProfile)

        val profile = assertIs<UserProfileUiState.Content>(viewModel.uiState.value).profile
        assertFalse(profile.canMessage)
    }

    @Test
    fun unknownPersonIsNotFound() = runTest {
        val graph = TestGraph(mockSource())

        val viewModel = UserProfileViewModel(UserId("user-nobody"), graph.getUserProfile)

        assertEquals(UserProfileUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun failureIsAnErrorAndRetryLoadsAgain() = runTest {
        val source = FlakySource(mockSource()).apply { failReads = true }
        val graph = TestGraph(source)
        val viewModel = UserProfileViewModel(Arjun, graph.getUserProfile)
        assertEquals(UserProfileUiState.Error, viewModel.uiState.value)

        source.failReads = false
        viewModel.onRetry()

        assertIs<UserProfileUiState.Content>(viewModel.uiState.value)
        assertEquals(2, source.profileRequests)
    }

    @Test
    fun retryDoesNothingOutsideTheErrorState() = runTest {
        val source = FlakySource(mockSource())
        val viewModel = UserProfileViewModel(Arjun, TestGraph(source).getUserProfile)

        viewModel.onRetry()

        assertIs<UserProfileUiState.Content>(viewModel.uiState.value)
        assertEquals(1, source.profileRequests)
    }
}

class UserWorksViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    @Test
    fun showsAllWorksNewestFirst() = runTest {
        val graph = TestGraph(mockSource())

        val viewModel = UserWorksViewModel(Arjun, graph.getUserWorks)

        val works = assertIs<UserWorksUiState.Content>(viewModel.uiState.value).works
        assertEquals(4, works.size)
        assertEquals("Localized CV Pipelines on Edge Devices", works.first().title)
        assertEquals(ProjectType.Scientific, works.first().type)
    }

    @Test
    fun unknownPersonIsNotFound() = runTest {
        val graph = TestGraph(mockSource())

        val viewModel = UserWorksViewModel(UserId("user-nobody"), graph.getUserWorks)

        assertEquals(UserWorksUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun failureIsAnErrorAndRetryLoadsAgain() = runTest {
        val source = FlakySource(mockSource()).apply { failReads = true }
        val viewModel = UserWorksViewModel(Arjun, TestGraph(source).getUserWorks)
        assertEquals(UserWorksUiState.Error, viewModel.uiState.value)

        source.failReads = false
        viewModel.onRetry()

        assertIs<UserWorksUiState.Content>(viewModel.uiState.value)
    }
}

class UserReviewsViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    @Test
    fun showsTheNumbersAndTheWholeList() = runTest {
        val graph = TestGraph(mockSource())

        val viewModel = UserReviewsViewModel(Arjun, graph.getUserReviews)

        val content = assertIs<UserReviewsUiState.Content>(viewModel.uiState.value)
        assertEquals(12, content.summary.count)
        assertEquals(11, content.summary.positiveCount)
        assertEquals(4.75, content.summary.average)
        assertEquals(12, content.items.size)
        assertEquals("Довёл модели до демо", content.items.first().title)
    }

    @Test
    fun unknownPersonIsNotFound() = runTest {
        val graph = TestGraph(mockSource())

        val viewModel = UserReviewsViewModel(UserId("user-nobody"), graph.getUserReviews)

        assertEquals(UserReviewsUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun failureIsAnErrorAndRetryLoadsAgain() = runTest {
        val source = FlakySource(mockSource()).apply { failReads = true }
        val viewModel = UserReviewsViewModel(Arjun, TestGraph(source).getUserReviews)
        assertEquals(UserReviewsUiState.Error, viewModel.uiState.value)

        source.failReads = false
        viewModel.onRetry()

        assertIs<UserReviewsUiState.Content>(viewModel.uiState.value)
    }
}

class ReviewDetailsViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    @Test
    fun showsTheReviewInFull() = runTest {
        val graph = TestGraph(mockSource())

        val viewModel = ReviewDetailsViewModel(ReviewId("review-me-1"), graph.getReview)

        val review = assertIs<ReviewDetailsUiState.Content>(viewModel.uiState.value).review
        assertEquals("Проф. Елена Васкес", review.author.name)
        assertEquals(ReviewerRole.Initiator, review.authorRole)
        assertEquals(5, review.rating)
        assertTrue(review.text.length > 200)
    }

    @Test
    fun unknownReviewIsNotFound() = runTest {
        val graph = TestGraph(mockSource())

        val viewModel = ReviewDetailsViewModel(ReviewId("review-nobody"), graph.getReview)

        assertEquals(ReviewDetailsUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun failureIsAnErrorAndRetryLoadsAgain() = runTest {
        val source = FlakySource(mockSource()).apply { failReads = true }
        val viewModel = ReviewDetailsViewModel(ReviewId("review-me-1"), TestGraph(source).getReview)
        assertEquals(ReviewDetailsUiState.Error, viewModel.uiState.value)

        source.failReads = false
        viewModel.onRetry()

        assertIs<ReviewDetailsUiState.Content>(viewModel.uiState.value)
    }
}
