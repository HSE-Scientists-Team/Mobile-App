package org.matchlab.academic_net.feature.projects.ui.list

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.datetime.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.feature.projects.data.mock.MockProjectsData
import org.matchlab.academic_net.feature.projects.domain.ProjectDraft
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.feature.projects.ui.TestGraph
import org.matchlab.academic_net.feature.projects.ui.installMainDispatcher
import org.matchlab.academic_net.feature.projects.ui.keepCollecting
import org.matchlab.academic_net.feature.projects.ui.uninstallMainDispatcher

class MyProjectsViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    @Test
    fun showsProjectsOfTheInitiatorWithCardData() = runTest {
        val graph = TestGraph()
        val viewModel = MyProjectsViewModel(graph.observeMyProjects)
        keepCollecting(viewModel.uiState)

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.hasError)
        val first = state.projects.first()
        assertEquals("AI-навигация по кампусу", first.title)
        assertEquals(ProjectType.Scientific, first.type)
        assertEquals(ProjectStatus.Recruiting, first.status)
        assertEquals("Computer Science", first.sphere)
        assertEquals(4, first.rolesCount)
        assertEquals(ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)), first.period)
        assertTrue(first.isInitiatorVerified)
        assertEquals(MockProjectsData.currentUser.id.value, first.avatarKey)
    }

    @Test
    fun startsInLoadingState() = runTest {
        val viewModel = MyProjectsViewModel(TestGraph().observeMyProjects)

        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun publishedProjectAppearsFirst() = runTest {
        val graph = TestGraph()
        val viewModel = MyProjectsViewModel(graph.observeMyProjects)
        keepCollecting(viewModel.uiState)
        val before = viewModel.uiState.value.projects.size

        graph.publishProject(
            ProjectDraft(
                title = "Новый",
                type = ProjectType.Practical,
                sphere = MockProjectsData.spheres.first(),
                problem = "p",
                goal = "g",
                roles = listOf(RoleDraft("Dev"), RoleDraft("QA")),
                start = YearMonth(2026, 11),
                end = YearMonth(2027, 2),
            ),
        )

        val projects = viewModel.uiState.value.projects
        assertEquals(before + 1, projects.size)
        assertEquals("Новый", projects.first().title)
        assertEquals(2, projects.first().rolesCount)
        assertEquals(ProjectPeriod(YearMonth(2026, 11), YearMonth(2027, 2)), projects.first().period)
    }

    @Test
    fun unpublishedProjectChangesStatusInList() = runTest {
        val graph = TestGraph()
        val viewModel = MyProjectsViewModel(graph.observeMyProjects)
        keepCollecting(viewModel.uiState)

        graph.unpublishProject(graph.firstProjectId)

        val item = viewModel.uiState.value.projects.first { it.id == graph.firstProjectId }
        assertEquals(ProjectStatus.Unpublished, item.status)
    }
}
