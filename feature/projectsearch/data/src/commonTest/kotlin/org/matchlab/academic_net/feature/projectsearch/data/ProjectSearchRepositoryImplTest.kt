package org.matchlab.academic_net.feature.projectsearch.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.feature.projectsearch.domain.FoundProject
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectSearchCapabilities

class ProjectSearchRepositoryImplTest {
    private class RecordingSource : ProjectSearchDataSource {
        override var capabilities = ProjectSearchCapabilities(typeFilter = false, durationFilter = false)
        val asked = mutableListOf<Pair<ProjectQuery, PageRequest>>()
        val answer = Page<FoundProject>(emptyList(), total = 0, request = PageRequest.First)

        override suspend fun search(query: ProjectQuery, page: PageRequest): Page<FoundProject> {
            asked += query to page
            return answer
        }
    }

    @Test
    fun theSearchGoesToTheSourceWithTheQueryAndThePageAsTheyAre() = runTest {
        val source = RecordingSource()
        val query = ProjectQuery(text = "ML")

        val page = ProjectSearchRepositoryImpl(source).search(query, PageRequest(20, 20))

        assertSame(source.answer, page)
        assertEquals(listOf(query to PageRequest(20, 20)), source.asked)
    }

    @Test
    fun theCapabilitiesAreTheSourcesAtTheMomentOfAsking() {
        val source = RecordingSource()
        val repository = ProjectSearchRepositoryImpl(source)
        assertEquals(ProjectSearchCapabilities(false, false), repository.capabilities)

        source.capabilities = ProjectSearchCapabilities(typeFilter = true, durationFilter = true)

        assertEquals(ProjectSearchCapabilities(true, true), repository.capabilities)
    }
}
