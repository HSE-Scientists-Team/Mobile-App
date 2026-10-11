package org.matchlab.academic_net.feature.projects.data

import kotlin.test.Test
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectPeriod
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.feature.projects.data.mock.MockProjectsData
import org.matchlab.academic_net.feature.projects.data.mock.MockProjectsDataSource
import org.matchlab.academic_net.feature.projects.domain.NewProject

class ProjectsRepositoryImplTest {
    private val repository = ProjectsRepositoryImpl(MockProjectsDataSource(latencyMillis = 0))

    @Test
    fun publishedProjectShowsUpInMyProjects() = runTest {
        val before = repository.observeMyProjects().first().size

        val project = repository.publishProject(
            NewProject(
                "Проект", ProjectType.Scientific, MockProjectsData.spheres.first(), "p", "g", emptyList(), emptyList(),
                ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)),
            ),
        )

        val after = repository.observeMyProjects().first()
        assertEquals(before + 1, after.size)
        assertEquals(project, after.first())
    }

    @Test
    fun unpublishIsVisibleThroughObservedProject() = runTest {
        val id = repository.observeMyProjects().first().first().id

        repository.unpublishProject(id)

        assertEquals(ProjectStatus.Unpublished, repository.observeProject(id).first()?.status)
    }
}
