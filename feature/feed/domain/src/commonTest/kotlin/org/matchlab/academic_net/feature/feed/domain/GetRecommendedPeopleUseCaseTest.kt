package org.matchlab.academic_net.feature.feed.domain

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.feed.domain.usecase.GetRecommendedPeopleUseCase
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class StubFeedRepository : FeedRepository {
    var calls = 0
    var answer: suspend () -> List<Collaborator> = { emptyList() }

    override suspend fun getRecommendedPeople(): List<Collaborator> {
        calls++
        return answer()
    }
}

private fun person(id: String) = Collaborator(UserSummary(UserId(id), "Имя $id"), "МГУ", Study("магистратура", 2), "physics")

/** "Получать рекомендации людей": the people block of the feed, as a result. */
class GetRecommendedPeopleUseCaseTest {
    private val repository = StubFeedRepository()
    private val get = GetRecommendedPeopleUseCase(repository)

    @Test
    fun thePeopleOfTheRepositoryAreTheSuccessInTheirOrder() = runTest {
        val people = listOf(person("c"), person("a"), person("b"))
        repository.answer = { people }

        val result = get()

        assertSame(people, result.getOrThrow())
        assertEquals(1, repository.calls)
    }

    @Test
    fun nobodyIsASuccessNotAFailure() = runTest {
        val result = get()

        assertTrue(result.isSuccess)
        assertEquals(emptyList(), result.getOrThrow())
    }

    @Test
    fun aFailureOfTheRepositoryIsAFailedResultWithTheSameCause() = runTest {
        val cause = RemoteFailure.Http(503)
        repository.answer = { throw cause }

        val result = get()

        assertSame(cause, result.exceptionOrNull())
    }

    @Test
    fun anyExceptionIsAFailedResultNotAThrow() = runTest {
        repository.answer = { throw IllegalStateException("boom") }

        assertIs<IllegalStateException>(get().exceptionOrNull())
    }

    @Test
    fun aCancellationPassesThroughAndIsNotAFailure() = runTest {
        repository.answer = { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { get() }
    }

    @Test
    fun everyCallAsksTheRepositoryAgain() = runTest {
        get()
        get()

        assertEquals(2, repository.calls)
    }
}
