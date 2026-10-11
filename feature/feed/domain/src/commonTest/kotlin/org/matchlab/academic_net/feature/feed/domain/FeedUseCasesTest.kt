package org.matchlab.academic_net.feature.feed.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.feed.domain.usecase.GetRecommendedPeopleUseCase
import org.matchlab.academic_net.feature.feed.domain.usecase.GetViewerUseCase

private class FakeFeedRepository : FeedRepository {
    val viewer = UserSummary(UserId("me"), "Вы")
    var people = listOf(Collaborator(UserSummary(UserId("u1"), "Вера"), "МГУ", "магистрант", "физика"))
    var failure: Throwable? = null

    override suspend fun getViewer(): UserSummary {
        failure?.let { throw it }
        return viewer
    }

    override suspend fun getRecommendedPeople(): List<Collaborator> {
        failure?.let { throw it }
        return people
    }
}

class FeedUseCasesTest {
    private val repository = FakeFeedRepository()

    @Test
    fun viewerIsPassedThrough() = runTest {
        assertEquals(repository.viewer, GetViewerUseCase(repository)().getOrThrow())
    }

    @Test
    fun recommendedPeopleKeepTheServersOrder() = runTest {
        assertEquals(repository.people, GetRecommendedPeopleUseCase(repository)().getOrThrow())
    }

    @Test
    fun repositoryErrorsBecomeFailures() = runTest {
        val error = IllegalStateException("offline")
        repository.failure = error

        assertSame(error, GetViewerUseCase(repository)().exceptionOrNull())
        assertSame(error, GetRecommendedPeopleUseCase(repository)().exceptionOrNull())
    }
}
