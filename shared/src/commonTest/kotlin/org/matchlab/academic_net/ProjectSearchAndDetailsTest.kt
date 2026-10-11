package org.matchlab.academic_net

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.feature.projectdetails.data.MockProjectDetailsDataSource
import org.matchlab.academic_net.feature.projectsearch.data.MockProjectSearchDataSource
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The two mock servers answer from one catalog. Features do not know each other, so the check that
 * a card found by the search opens as a project page lives here, where they are assembled.
 */
class ProjectSearchAndDetailsTest {
    @Test
    fun everyProjectTheSearchReturnsIsFoundByItsId() = runTest {
        val search = MockProjectSearchDataSource(latencyMillis = 0)
        val details = MockProjectDetailsDataSource(latencyMillis = 0)

        val found = search.search(ProjectQuery())

        assertTrue(found.isNotEmpty())
        for (hit in found) {
            val page = details.getProjectDetails(hit.project.id)

            assertNotNull(page, "not found: ${hit.project.id}")
            assertEquals(hit.project, page.project)
            assertEquals(hit.initiatorUniversity, page.initiator.university)
        }
    }
}
