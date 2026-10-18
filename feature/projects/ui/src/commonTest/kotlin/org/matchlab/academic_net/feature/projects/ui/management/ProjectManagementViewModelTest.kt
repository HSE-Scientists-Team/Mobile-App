package org.matchlab.academic_net.feature.projects.ui.management

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.projects.data.mock.MockProjectsData
import org.matchlab.academic_net.feature.projects.data.mock.MockProjectsDataSource
import org.matchlab.academic_net.feature.projects.domain.RoleDraftError
import org.matchlab.academic_net.feature.projects.ui.CountingDataSource
import org.matchlab.academic_net.feature.projects.ui.FailingDataSource
import org.matchlab.academic_net.feature.projects.ui.TestGraph
import org.matchlab.academic_net.feature.projects.ui.installMainDispatcher
import org.matchlab.academic_net.feature.projects.ui.keepCollecting
import org.matchlab.academic_net.feature.projects.ui.uninstallMainDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectManagementViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private fun viewModel(graph: TestGraph, id: ProjectId = graph.firstProjectId) = ProjectManagementViewModel(
        projectId = id,
        observeProject = graph.observeProject,
        unpublishProject = graph.unpublishProject,
        addRole = graph.addRole,
    )

    @Test
    fun startsLoading() = runTest {
        val graph = TestGraph()

        assertTrue(viewModel(graph).uiState.value.isLoading)
    }

    @Test
    fun splitsRolesIntoOccupiedAndVacant() = runTest {
        val viewModel = viewModel(TestGraph())
        keepCollecting(viewModel.uiState)

        val project = assertNotNull(viewModel.uiState.value.project)

        assertEquals("AI-навигация по кампусу", project.title)
        assertEquals(ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)), project.period)
        assertEquals(listOf("ML Engineer", "Data Analyst"), project.occupiedRoles.map { it.roleTitle })
        assertEquals("Арджун Мехта", project.occupiedRoles.first().memberName)
        assertEquals(4.8, project.occupiedRoles.first().rating)
        assertEquals(3, project.occupiedRoles.first().completedProjects)
        assertEquals(listOf("Mobile Developer", "UX Researcher"), project.vacantRoles.map { it.title })
        assertEquals(RoleId("role-mobile-developer"), project.vacantRoles.first().id)
        assertEquals(listOf("Flutter", "Dart"), project.vacantRoles.first().skills)
        assertEquals(4, project.rolesCount)
    }

    @Test
    fun initiatorCanLookForPeopleForEveryVacantRole() = runTest {
        val viewModel = viewModel(TestGraph())
        keepCollecting(viewModel.uiState)

        val project = assertNotNull(viewModel.uiState.value.project)

        assertTrue(project.vacantRoles.isNotEmpty())
        assertTrue(project.vacantRoles.all { it.canFindPeople })
    }

    @Test
    fun whoIsNotTheInitiatorGetsNoPeopleSearch() = runTest {
        val stranger = UserSummary(UserId("someone-else"), "Кто-то")
        val foreign = MockProjectsData.myProjects().first()
            .copy(initiator = stranger, viewerIsInitiator = false)
        val graph = TestGraph(MockProjectsDataSource(initialProjects = listOf(foreign), latencyMillis = 0))
        val viewModel = viewModel(graph, foreign.id)
        keepCollecting(viewModel.uiState)

        val project = assertNotNull(viewModel.uiState.value.project)

        assertTrue(project.vacantRoles.isNotEmpty())
        assertTrue(project.vacantRoles.none { it.canFindPeople })
    }

    @Test
    fun unknownProjectIsLoadedButEmpty() = runTest {
        val viewModel = viewModel(TestGraph(), ProjectId("missing"))
        keepCollecting(viewModel.uiState)

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertNull(state.project)
    }

    @Test
    fun unpublishAsksForConfirmationFirst() = runTest {
        val graph = TestGraph()
        val viewModel = viewModel(graph)
        keepCollecting(viewModel.uiState)

        viewModel.onUnpublishClick()

        assertTrue(viewModel.uiState.value.isUnpublishConfirmVisible)
        assertEquals(ProjectStatus.Recruiting, viewModel.uiState.value.project?.status)

        viewModel.onUnpublishDismiss()

        assertFalse(viewModel.uiState.value.isUnpublishConfirmVisible)
        assertEquals(ProjectStatus.Recruiting, viewModel.uiState.value.project?.status)
    }

    @Test
    fun confirmedUnpublishHidesTheProject() = runTest {
        val graph = TestGraph()
        val viewModel = viewModel(graph)
        keepCollecting(viewModel.uiState)
        assertTrue(assertNotNull(viewModel.uiState.value.project).canUnpublish)

        viewModel.onUnpublishClick()
        viewModel.onUnpublishConfirm()

        val state = viewModel.uiState.value
        val project = assertNotNull(state.project)
        assertEquals(ProjectStatus.Unpublished, project.status)
        assertFalse(project.canUnpublish)
        assertFalse(state.isUnpublishConfirmVisible)
        assertFalse(state.isUnpublishing)
        assertFalse(state.actionFailed)
    }

    @Test
    fun failedUnpublishKeepsStatusAndReportsError() = runTest {
        val viewModel = viewModel(TestGraph(FailingDataSource()))
        keepCollecting(viewModel.uiState)

        viewModel.onUnpublishClick()
        viewModel.onUnpublishConfirm()

        val state = viewModel.uiState.value
        assertTrue(state.actionFailed)
        assertFalse(state.isUnpublishing)
        assertEquals(ProjectStatus.Recruiting, state.project?.status)
    }

    @Test
    fun declaredRoleAppearsAsVacant() = runTest {
        val viewModel = viewModel(TestGraph())
        keepCollecting(viewModel.uiState)

        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("DevOps")
        viewModel.onRoleSkillAdd("Docker")
        viewModel.onRoleSave()

        val state = viewModel.uiState.value
        val project = assertNotNull(state.project)
        assertEquals(5, project.rolesCount)
        assertEquals("DevOps", project.vacantRoles.last().title)
        assertEquals(listOf("Docker"), project.vacantRoles.last().skills)
        assertNull(state.roleEditor)
        assertFalse(state.isSavingRole)
    }

    @Test
    fun roleWithoutTitleIsRejectedAndEditorStaysOpen() = runTest {
        val viewModel = viewModel(TestGraph())
        keepCollecting(viewModel.uiState)

        viewModel.onAddRoleClick()
        viewModel.onRoleSave()

        val state = viewModel.uiState.value
        assertEquals(setOf(RoleDraftError.TitleEmpty), state.roleEditor?.errors)
        assertEquals(4, state.project?.rolesCount)
    }

    @Test
    fun failedRoleSaveKeepsTheEditor() = runTest {
        val viewModel = viewModel(TestGraph(FailingDataSource()))
        keepCollecting(viewModel.uiState)

        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("DevOps")
        viewModel.onRoleSave()

        val state = viewModel.uiState.value
        assertTrue(state.actionFailed)
        val editor = assertNotNull(state.roleEditor)
        assertEquals("DevOps", editor.draft.title)
    }

    @Test
    fun secondConfirmWhileUnpublishingUnpublishesOnce() = runTest {
        val source = CountingDataSource(delayMillis = 300)
        val viewModel = viewModel(TestGraph(source))
        keepCollecting(viewModel.uiState)

        viewModel.onUnpublishClick()
        viewModel.onUnpublishConfirm()
        assertTrue(viewModel.uiState.value.isUnpublishing)
        viewModel.onUnpublishConfirm()
        advanceUntilIdle()

        assertEquals(1, source.unpublished)
        assertEquals(ProjectStatus.Unpublished, viewModel.uiState.value.project?.status)
    }

    @Test
    fun dialogAskedForAgainWhileUnpublishingCannotStartASecondRequest() = runTest {
        val source = CountingDataSource(delayMillis = 300)
        val viewModel = viewModel(TestGraph(source))
        keepCollecting(viewModel.uiState)
        viewModel.onUnpublishClick()
        viewModel.onUnpublishConfirm()
        assertTrue(viewModel.uiState.value.isUnpublishing)

        // The dialog is open again (visible) while the request of the first confirmation runs.
        viewModel.onUnpublishClick()
        assertTrue(viewModel.uiState.value.isUnpublishConfirmVisible)
        viewModel.onUnpublishConfirm()
        advanceUntilIdle()

        assertEquals(1, source.unpublished)
    }

    @Test
    fun confirmAfterTheDialogIsGoneUnpublishesNothingMore() = runTest {
        // The button of a dialog that is fading out can still be tapped, after the request has ended.
        val source = CountingDataSource()
        val viewModel = viewModel(TestGraph(source))
        keepCollecting(viewModel.uiState)
        viewModel.onUnpublishClick()
        viewModel.onUnpublishConfirm()
        assertFalse(viewModel.uiState.value.isUnpublishing)

        viewModel.onUnpublishConfirm()

        assertEquals(1, source.unpublished)
        assertFalse(viewModel.uiState.value.actionFailed)
    }

    @Test
    fun confirmWithoutAskingFirstDoesNothing() = runTest {
        val source = CountingDataSource()
        val viewModel = viewModel(TestGraph(source))
        keepCollecting(viewModel.uiState)

        viewModel.onUnpublishConfirm()

        assertEquals(0, source.unpublished)
        assertEquals(ProjectStatus.Recruiting, viewModel.uiState.value.project?.status)
    }

    @Test
    fun dismissedDialogCannotBeConfirmedLater() = runTest {
        val source = CountingDataSource()
        val viewModel = viewModel(TestGraph(source))
        keepCollecting(viewModel.uiState)
        viewModel.onUnpublishClick()
        viewModel.onUnpublishDismiss()

        viewModel.onUnpublishConfirm()

        assertEquals(0, source.unpublished)
    }

    @Test
    fun secondSaveWhileTheRoleIsBeingAddedAddsItOnce() = runTest {
        val source = CountingDataSource(delayMillis = 300)
        val viewModel = viewModel(TestGraph(source))
        keepCollecting(viewModel.uiState)
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("DevOps")

        viewModel.onRoleSave()
        assertTrue(viewModel.uiState.value.isSavingRole)
        viewModel.onRoleSave()
        advanceUntilIdle()

        assertEquals(1, source.rolesAdded)
        assertEquals(5, viewModel.uiState.value.project?.rolesCount)
        assertNull(viewModel.uiState.value.roleEditor)
    }

    @Test
    fun saveAfterTheEditorIsClosedAddsNothingMore() = runTest {
        val source = CountingDataSource()
        val viewModel = viewModel(TestGraph(source))
        keepCollecting(viewModel.uiState)
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("DevOps")
        viewModel.onRoleSave()
        assertNull(viewModel.uiState.value.roleEditor)

        // The button of the sheet that is sliding down is tapped once more.
        viewModel.onRoleSave()

        assertEquals(1, source.rolesAdded)
        assertEquals(5, viewModel.uiState.value.project?.rolesCount)
    }
}
