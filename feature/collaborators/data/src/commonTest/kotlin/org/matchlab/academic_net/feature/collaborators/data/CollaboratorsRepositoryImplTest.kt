package org.matchlab.academic_net.feature.collaborators.data

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorFilters
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorQuery
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorsCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private class RecordingCollaboratorsDataSource : CollaboratorsDataSource {
    override var capabilities = CollaboratorsCapabilities(projectTypeFilter = false)

    val searches = mutableListOf<Pair<CollaboratorQuery, PageRequest>>()
    val counts = mutableListOf<CollaboratorQuery>()
    var page: Page<Collaborator> = Page(emptyList(), total = 0, request = PageRequest.First)
    var count = 0
    var failure: Throwable? = null

    override suspend fun search(query: CollaboratorQuery, page: PageRequest): Page<Collaborator> {
        searches += query to page
        failure?.let { throw it }
        return this.page
    }

    override suspend fun count(query: CollaboratorQuery): Int {
        counts += query
        failure?.let { throw it }
        return count
    }
}

/** The repository adds nothing of its own: all three members are the data source's. */
class CollaboratorsRepositoryImplTest {
    private val source = RecordingCollaboratorsDataSource()
    private val repository = CollaboratorsRepositoryImpl(source)

    private val query = CollaboratorQuery("kotlin", CollaboratorFilters(verifiedProfile = true, keywords = listOf("ML")))

    private fun collaborator(id: String) = Collaborator(UserSummary(UserId(id), id), "МГУ", Study("магистратура", 2), "physics")

    @Test
    fun aSearchIsTheSourcesSearchWithTheSameArgumentsAndTheSameAnswer() = runTest {
        val request = PageRequest(offset = 20, limit = 10)
        source.page = Page(listOf(collaborator("a")), total = 21, request = request)

        val page = repository.search(query, request)

        assertSame(source.page, page)
        assertSame(query, source.searches.single().first)
        assertSame(request, source.searches.single().second)
    }

    @Test
    fun aCountIsTheSourcesCount() = runTest {
        source.count = 64

        assertEquals(64, repository.count(query))
        assertSame(query, source.counts.single())
        assertEquals(emptyList(), source.searches)
    }

    @Test
    fun theCapabilitiesAreReadFromTheSourceEachTime() {
        assertEquals(false, repository.capabilities.projectTypeFilter)

        source.capabilities = CollaboratorsCapabilities(projectTypeFilter = true)

        assertEquals(true, repository.capabilities.projectTypeFilter)
        assertSame(source.capabilities, repository.capabilities)
    }

    @Test
    fun theFailuresOfTheSourceAreThrownUnchanged() = runTest {
        val cause = RemoteFailure.Http(500)
        source.failure = cause

        assertSame(cause, assertFailsWith<RemoteFailure.Http> { repository.search(query, PageRequest.First) })
        assertSame(cause, assertFailsWith<RemoteFailure.Http> { repository.count(query) })
    }
}
