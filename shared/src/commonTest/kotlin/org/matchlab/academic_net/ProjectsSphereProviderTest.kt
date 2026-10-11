package org.matchlab.academic_net

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.feature.projects.data.ProjectsRepositoryImpl
import org.matchlab.academic_net.feature.projects.data.mock.MockProjectsData
import org.matchlab.academic_net.feature.projects.data.mock.MockProjectsDataSource
import org.matchlab.academic_net.feature.projectsearch.data.MockProjectCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectsSphereProviderTest {
    private val provider = ProjectsSphereProvider(
        ProjectsRepositoryImpl(MockProjectsDataSource(latencyMillis = 0)),
    )

    @Test
    fun spheresComeFromTheProjectsFeature() = runTest {
        assertEquals(MockProjectsData.spheres, provider.getSpheres())
    }

    @Test
    fun everySphereOfTheSearchCatalogIsOneTheFilterOffers() = runTest {
        // The sphere filter sends a sphere id to the search server; a catalog project in a sphere the
        // filter does not list could never be found by it.
        val offered = provider.getSpheres().map { it.id }.toSet()
        val used = MockProjectCatalog.all.map { it.project.sphere.id }.toSet()

        assertTrue(offered.containsAll(used), "unknown spheres in the catalog: ${used - offered}")
    }
}
