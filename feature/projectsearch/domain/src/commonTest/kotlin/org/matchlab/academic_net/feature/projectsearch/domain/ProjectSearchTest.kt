package org.matchlab.academic_net.feature.projectsearch.domain

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.paging.PageRequest

class ProjectFiltersTest {
    @Test
    fun noFiltersMeansEmpty() {
        assertTrue(ProjectFilters().isEmpty)
        assertEquals(0, ProjectFilters().activeCount)
    }

    @Test
    fun typesCountAsOneGroupHoweverManyAreChosen() {
        val filters = ProjectFilters(types = ProjectType.entries.toSet())

        assertFalse(filters.isEmpty)
        assertEquals(1, filters.activeCount)
    }

    @Test
    fun sphereCountsAsOne() {
        val filters = ProjectFilters(sphere = ComputerScience)

        assertFalse(filters.isEmpty)
        assertEquals(1, filters.activeCount)
    }

    @Test
    fun durationsCountAsOneGroup() {
        val filters = ProjectFilters(durations = setOf(ProjectDuration.UpTo3Months, ProjectDuration.Over6Months))

        assertFalse(filters.isEmpty)
        assertEquals(1, filters.activeCount)
    }

    @Test
    fun keywordsCountAsOneGroup() {
        val filters = ProjectFilters(keywords = listOf("Python", "ROS"))

        assertFalse(filters.isEmpty)
        assertEquals(1, filters.activeCount)
    }

    @Test
    fun everyGroupAddsToTheCount() {
        val filters = ProjectFilters(
            types = setOf(ProjectType.Practical),
            sphere = Biology,
            durations = setOf(ProjectDuration.From3To6Months),
            keywords = listOf("Python"),
        )

        assertFalse(filters.isEmpty)
        assertEquals(4, filters.activeCount)
    }

    @Test
    fun theTextIsNotAFilter() {
        assertTrue(ProjectQuery(text = "Python").filters.isEmpty)
    }
}

class SearchProjectsUseCaseTest {
    private val answer = listOf(foundProject("Alpha"), foundProject("Beta"))
    private val repository = FakeProjectSearchRepository(answer)
    private val search = SearchProjectsUseCase(repository)

    @Test
    fun sendsTheQueryAsIsAndKeepsTheServersOrder() = runTest {
        val query = ProjectQuery(
            text = "Python, Kotlin",
            filters = ProjectFilters(types = setOf(ProjectType.Scientific), sphere = ComputerScience),
        )

        val result = search(query, PageRequest.First).getOrThrow()

        assertEquals(query, repository.lastQuery)
        assertEquals(answer, result.items)
        assertEquals(2, result.total)
    }

    @Test
    fun theRequestedPageReachesTheRepositoryAndTheAnswerKeepsIt() = runTest {
        repository.results = List(5) { foundProject("P$it") }

        val page = search(ProjectQuery(), PageRequest(offset = 2, limit = 2)).getOrThrow()

        assertEquals(PageRequest(2, 2), repository.lastPage)
        assertEquals(listOf("P2", "P3"), page.items.map { it.project.title })
        assertEquals(PageRequest(2, 2), page.request)
        assertEquals(PageRequest(4, 2), page.next())
    }

    @Test
    fun theLastPageHasNoNext() = runTest {
        repository.results = List(5) { foundProject("P$it") }

        val page = search(ProjectQuery(), PageRequest(offset = 4, limit = 2)).getOrThrow()

        assertEquals(listOf("P4"), page.items.map { it.project.title })
        assertNull(page.next())
    }

    @Test
    fun emptyAnswerIsASuccess() = runTest {
        repository.results = emptyList()

        val page = search(ProjectQuery(), PageRequest.First).getOrThrow()

        assertEquals(emptyList(), page.items)
        assertEquals(0, page.total)
        assertFalse(page.hasMore)
    }

    @Test
    fun repositoryErrorBecomesFailure() = runTest {
        val error = IllegalStateException("offline")
        repository.failure = error

        assertSame(error, search(ProjectQuery(), PageRequest.First).exceptionOrNull())
    }

    @Test
    fun cancellationIsNotTurnedIntoFailure() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { search(ProjectQuery(), PageRequest.First) }
    }
}

class GetSearchSpheresUseCaseTest {
    private val provider = FakeSphereProvider(listOf(ComputerScience, Biology))
    private val getSpheres = GetSearchSpheresUseCase(provider)

    @Test
    fun returnsTheProvidersSpheresInOrder() = runTest {
        assertEquals(listOf(ComputerScience, Biology), getSpheres().getOrThrow())
    }

    @Test
    fun providerErrorBecomesFailure() = runTest {
        val error = IllegalStateException("offline")
        provider.failure = error

        assertSame(error, getSpheres().exceptionOrNull())
    }

    @Test
    fun cancellationIsNotTurnedIntoFailure() = runTest {
        provider.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { getSpheres() }
    }
}
