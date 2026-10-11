package org.matchlab.academic_net.feature.profile.domain.usecase

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.feature.profile.domain.FakeProfileRepository
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.SomeProfile
import org.matchlab.academic_net.feature.profile.domain.SomeReview
import org.matchlab.academic_net.feature.profile.domain.SomeWork
import org.matchlab.academic_net.feature.profile.domain.UserReviews
import org.matchlab.academic_net.feature.profile.domain.NoReviews

private val UserA = UserId("u-1")

class GetUserProfileUseCaseTest {
    private val repository = FakeProfileRepository()
    private val getProfile = GetUserProfileUseCase(repository)

    @Test
    fun foundProfileComesAsIs() = runTest {
        val result = getProfile(UserA)

        assertSame(SomeProfile, assertIs<LoadResult.Found<*>>(result).value)
        assertEquals(listOf(UserA), repository.requestedUsers)
    }

    @Test
    fun unknownPersonIsNotFound() = runTest {
        repository.profile = null

        assertEquals(LoadResult.NotFound, getProfile(UserA))
    }

    @Test
    fun failureIsAResultWithTheCause() = runTest {
        val cause = IllegalStateException("offline")
        repository.failure = cause

        assertSame(cause, assertIs<LoadResult.Failure>(getProfile(UserA)).cause)
    }

    @Test
    fun cancellationIsNotAFailure() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { getProfile(UserA) }
    }
}

class GetUserWorksUseCaseTest {
    private val repository = FakeProfileRepository()
    private val getWorks = GetUserWorksUseCase(repository)

    @Test
    fun foundWorksComeAsIs() = runTest {
        val result = getWorks(UserA)

        assertEquals(listOf(SomeWork), assertIs<LoadResult.Found<*>>(result).value)
        assertEquals(listOf(UserA), repository.requestedUsers)
    }

    @Test
    fun personWithoutWorksIsFoundWithAnEmptyList() = runTest {
        repository.works = emptyList()

        assertEquals(LoadResult.Found(emptyList()), getWorks(UserA))
    }

    @Test
    fun unknownPersonIsNotFound() = runTest {
        repository.works = null

        assertEquals(LoadResult.NotFound, getWorks(UserA))
    }

    @Test
    fun failureIsAResultWithTheCause() = runTest {
        val cause = IllegalStateException("offline")
        repository.failure = cause

        assertSame(cause, assertIs<LoadResult.Failure>(getWorks(UserA)).cause)
    }

    @Test
    fun cancellationIsNotAFailure() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { getWorks(UserA) }
    }
}

class GetUserReviewsUseCaseTest {
    private val repository = FakeProfileRepository()
    private val getReviews = GetUserReviewsUseCase(repository)

    @Test
    fun foundReviewsComeAsIs() = runTest {
        val reviews = UserReviews(NoReviews, listOf(SomeReview))
        repository.reviews = reviews

        val result = getReviews(UserA)

        assertSame(reviews, assertIs<LoadResult.Found<*>>(result).value)
        assertEquals(listOf(UserA), repository.requestedUsers)
    }

    @Test
    fun unknownPersonIsNotFound() = runTest {
        repository.reviews = null

        assertEquals(LoadResult.NotFound, getReviews(UserA))
    }

    @Test
    fun failureIsAResultWithTheCause() = runTest {
        val cause = IllegalStateException("offline")
        repository.failure = cause

        assertSame(cause, assertIs<LoadResult.Failure>(getReviews(UserA)).cause)
    }

    @Test
    fun cancellationIsNotAFailure() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { getReviews(UserA) }
    }
}

class GetReviewUseCaseTest {
    private val repository = FakeProfileRepository()
    private val getReview = GetReviewUseCase(repository)

    @Test
    fun foundReviewComesAsIs() = runTest {
        val result = getReview(ReviewId("r-1"))

        assertSame(SomeReview, assertIs<LoadResult.Found<*>>(result).value)
        assertEquals(listOf(ReviewId("r-1")), repository.requestedReviews)
    }

    @Test
    fun unknownReviewIsNotFound() = runTest {
        repository.review = null

        assertEquals(LoadResult.NotFound, getReview(ReviewId("nope")))
    }

    @Test
    fun failureIsAResultWithTheCause() = runTest {
        val cause = IllegalStateException("offline")
        repository.failure = cause

        assertSame(cause, assertIs<LoadResult.Failure>(getReview(ReviewId("r-1"))).cause)
    }

    @Test
    fun failureDoesNotTurnIntoNotFound() = runTest {
        repository.failure = RuntimeException("boom")
        repository.review = null

        assertIs<LoadResult.Failure>(getReview(ReviewId("r-1")))
    }

    @Test
    fun cancellationIsNotAFailure() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { getReview(ReviewId("r-1")) }
    }
}
