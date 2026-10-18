package org.matchlab.academic_net.feature.collaborators.domain

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.collaborators.domain.usecase.CountCollaboratorsUseCase
import org.matchlab.academic_net.feature.collaborators.domain.usecase.GetRoleRequirementUseCase
import org.matchlab.academic_net.feature.collaborators.domain.usecase.SearchCollaboratorsUseCase
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A repository that remembers what it was asked and answers (or fails) as the test says. */
private class StubCollaboratorsRepository : CollaboratorsRepository {
    override val capabilities = CollaboratorsCapabilities(projectTypeFilter = false)

    val searches = mutableListOf<Pair<CollaboratorQuery, PageRequest>>()
    val counts = mutableListOf<CollaboratorQuery>()

    var onSearch: suspend (CollaboratorQuery, PageRequest) -> Page<Collaborator> = { _, page ->
        Page(emptyList(), total = 0, request = page)
    }
    var onCount: suspend (CollaboratorQuery) -> Int = { 0 }

    override suspend fun search(query: CollaboratorQuery, page: PageRequest): Page<Collaborator> {
        searches += query to page
        return onSearch(query, page)
    }

    override suspend fun count(query: CollaboratorQuery): Int {
        counts += query
        return onCount(query)
    }
}

/** "Искать людей": one page of the server's answer, as a result. */
class SearchCollaboratorsUseCaseTest {
    private val repository = StubCollaboratorsRepository()
    private val search = SearchCollaboratorsUseCase(repository)

    private val query = CollaboratorQuery(
        text = "Python, Kotlin",
        filters = CollaboratorFilters(verifiedProfile = true, university = "МГУ", keywords = listOf("ML")),
    )

    @Test
    fun theQueryAndThePageGoToTheRepositoryAsTheyAre() = runTest {
        val page = PageRequest(offset = 40, limit = 10)

        search(query, page)

        assertEquals(1, repository.searches.size)
        assertSame(query, repository.searches.single().first)
        assertSame(page, repository.searches.single().second)
    }

    @Test
    fun theAnswerOfTheRepositoryIsTheSuccess() = runTest {
        val answer = Page(listOf(collaborator("Анна"), collaborator("Борис")), total = 37, request = PageRequest(0, 20))
        repository.onSearch = { _, _ -> answer }

        val result = search(query, PageRequest.First)

        assertSame(answer, result.getOrThrow())
    }

    @Test
    fun aFailureOfTheRepositoryIsAFailedResultWithTheSameCause() = runTest {
        val cause = RemoteFailure.Http(503)
        repository.onSearch = { _, _ -> throw cause }

        val result = search(query, PageRequest.First)

        assertSame(cause, result.exceptionOrNull())
    }

    @Test
    fun anyExceptionIsAFailedResultNotAThrow() = runTest {
        repository.onSearch = { _, _ -> throw IllegalStateException("boom") }

        val result = search(query, PageRequest.First)

        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun aCancellationPassesThroughAndIsNotAFailure() = runTest {
        repository.onSearch = { _, _ -> throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { search(query, PageRequest.First) }
    }
}

/** "Показать N": how many people the filters being edited would show, as a result. */
class CountCollaboratorsUseCaseTest {
    private val repository = StubCollaboratorsRepository()
    private val count = CountCollaboratorsUseCase(repository)

    private val query = CollaboratorQuery("kotlin", CollaboratorFilters(hasPublications = true))

    @Test
    fun theQueryGoesToTheRepositoryAsItIs() = runTest {
        count(query)

        assertEquals(1, repository.counts.size)
        assertSame(query, repository.counts.single())
        assertEquals(emptyList(), repository.searches)
    }

    @Test
    fun theNumberIsTheSuccess() = runTest {
        repository.onCount = { 128 }

        assertEquals(128, count(query).getOrThrow())
    }

    @Test
    fun zeroIsAnAnswerNotAnError() = runTest {
        repository.onCount = { 0 }

        val result = count(query)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun aFailureOfTheRepositoryIsAFailedResultWithTheSameCause() = runTest {
        val cause = RemoteFailure.Timeout()
        repository.onCount = { throw cause }

        assertSame(cause, count(query).exceptionOrNull())
    }

    @Test
    fun aCancellationPassesThroughAndIsNotAFailure() = runTest {
        repository.onCount = { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { count(query) }
    }
}

/** What the screen needs of the provider of the role besides the cases of [GetRoleRequirementUseCaseTest]. */
class RoleRequirementFailuresTest {
    private val projectId = ProjectId("p1")
    private val roleId = RoleId("r1")

    @Test
    fun theNotFoundFailureNamesTheProjectAndTheRole() = runTest {
        val get = GetRoleRequirementUseCase(FakeRoleRequirementProvider(requirement = null))

        val failure = assertIs<RoleNotFoundException>(get(projectId, roleId).exceptionOrNull())

        assertEquals(projectId, failure.projectId)
        assertEquals(roleId, failure.roleId)
    }

    @Test
    fun aFailureOfTheProviderIsAFailedResult() = runTest {
        val cause = IllegalStateException("projects are not loaded")
        val get = GetRoleRequirementUseCase(object : RoleRequirementProvider {
            override suspend fun getRoleRequirement(projectId: ProjectId, roleId: RoleId): RoleRequirement? = throw cause
        })

        assertSame(cause, get(projectId, roleId).exceptionOrNull())
    }

    @Test
    fun aCancellationOfTheProviderPassesThrough() = runTest {
        val get = GetRoleRequirementUseCase(object : RoleRequirementProvider {
            override suspend fun getRoleRequirement(projectId: ProjectId, roleId: RoleId): RoleRequirement? =
                throw CancellationException("cancelled")
        })

        assertFailsWith<CancellationException> { get(projectId, roleId) }
    }
}
