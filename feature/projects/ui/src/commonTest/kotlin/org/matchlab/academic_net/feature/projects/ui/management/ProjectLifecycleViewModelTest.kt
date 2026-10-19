package org.matchlab.academic_net.feature.projects.ui.management

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.domain.ProjectLifecycleAction
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.usecase.AddRoleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ChangeProjectLifecycleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.LoadProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UnpublishProjectUseCase
import org.matchlab.academic_net.feature.projects.ui.FakeProjectsRepository
import org.matchlab.academic_net.feature.projects.ui.Gate
import org.matchlab.academic_net.feature.projects.ui.caps
import org.matchlab.academic_net.feature.projects.ui.installMainDispatcher
import org.matchlab.academic_net.feature.projects.ui.keepCollecting
import org.matchlab.academic_net.feature.projects.ui.project
import org.matchlab.academic_net.feature.projects.ui.uninstallMainDispatcher

/** 07c: the moves of the project (start, finish, cancel), what is offered when, and the guards of the confirmation. */
class ProjectLifecycleViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val id = ProjectId("p1")
    private val repository = FakeProjectsRepository(caps(canUnpublish = true, canChangeLifecycle = true))
    private val cause = IllegalStateException("offline")

    private val start = ProjectLifecycleAction.Start
    private val complete = ProjectLifecycleAction.Complete
    private val cancel = ProjectLifecycleAction.Cancel

    private fun TestScope.viewModel(capabilities: ProjectsCapabilities = repository.capabilities): ProjectManagementViewModel {
        val viewModel = ProjectManagementViewModel(
            projectId = id,
            observeProject = ObserveProjectUseCase(repository),
            loadProject = LoadProjectUseCase(repository),
            unpublishProject = UnpublishProjectUseCase(repository),
            addRole = AddRoleUseCase(repository),
            changeLifecycle = ChangeProjectLifecycleUseCase(repository),
            capabilities = capabilities,
        )
        keepCollecting(viewModel.uiState)
        return viewModel
    }

    private val ProjectManagementViewModel.state get() = uiState.value

    private fun serve(status: ProjectStatus? = ProjectStatus.Recruiting, viewerIsInitiator: Boolean = true) {
        repository.server[id] = project("p1", status = status, viewerIsInitiator = viewerIsInitiator)
    }

    // ---- what is offered ----

    @Test
    fun theActionsFollowTheStatusTable() = runTest {
        val expected = mapOf(
            null to listOf(start, complete, cancel),
            ProjectStatus.Recruiting to listOf(start, cancel),
            ProjectStatus.Unpublished to listOf(start, cancel),
            ProjectStatus.Started to listOf(complete, cancel),
            ProjectStatus.Finished to emptyList(),
            ProjectStatus.Cancelled to emptyList(),
        )
        for ((status, actions) in expected) {
            serve(status)

            assertEquals(actions, viewModel().state.project?.lifecycleActions, "status $status")
        }
    }

    @Test
    fun noActionsWithoutTheCapabilityOrForAViewerWhoIsNotTheInitiator() = runTest {
        serve(ProjectStatus.Recruiting)
        assertEquals(emptyList(), viewModel(caps(canUnpublish = true, canChangeLifecycle = false)).state.project?.lifecycleActions)

        serve(ProjectStatus.Recruiting, viewerIsInitiator = false)
        assertEquals(emptyList(), viewModel().state.project?.lifecycleActions)
    }

    @Test
    fun unpublishingIsOfferedOnlyWhileRecruitingOrUnknown() = runTest {
        val expected = mapOf(
            null to true,
            ProjectStatus.Recruiting to true,
            ProjectStatus.Unpublished to false,
            ProjectStatus.Started to false,
            ProjectStatus.Finished to false,
            ProjectStatus.Cancelled to false,
        )
        for ((status, offered) in expected) {
            serve(status)

            assertEquals(offered, viewModel().state.project?.canUnpublish, "status $status")
        }
    }

    @Test
    fun thePencilIsHiddenForAFinishedOrCancelledProjectAndTheRowForEverythingButTheStartedOnes() = runTest {
        // canEdit: the pencil; offersEditAction: the row "Редактировать проект" of 07c.
        val expected = mapOf(
            null to (true to true),
            ProjectStatus.Recruiting to (true to true),
            ProjectStatus.Unpublished to (true to true),
            ProjectStatus.Started to (true to false),
            ProjectStatus.Finished to (false to false),
            ProjectStatus.Cancelled to (false to false),
        )
        for ((status, flags) in expected) {
            serve(status)

            val project = viewModel().state.project!!
            assertEquals(flags, project.canEdit to project.offersEditAction, "status $status")
        }
    }

    @Test
    fun aViewerWhoIsNotTheInitiatorCannotEditAtAll() = runTest {
        for (status in listOf(null) + ProjectStatus.entries) {
            serve(status, viewerIsInitiator = false)

            val project = viewModel().state.project!!
            assertFalse(project.canEdit, "status $status")
            assertFalse(project.offersEditAction, "status $status")
        }
    }

    // ---- the dialog ----

    @Test
    fun anActionOpensItsDialogAndDismissingClosesItWithoutARequest() = runTest {
        serve()
        val viewModel = viewModel()

        viewModel.onLifecycleClick(cancel)
        assertEquals(cancel, viewModel.state.lifecycleDialog)
        viewModel.onLifecycleDismiss()

        assertNull(viewModel.state.lifecycleDialog)
        assertEquals(emptyList(), repository.lifecycleMoves)
    }

    @Test
    fun confirmingWithoutTheDialogDoesNothing() = runTest {
        serve()
        val viewModel = viewModel()

        viewModel.onLifecycleConfirm()

        assertEquals(emptyList(), repository.lifecycleMoves)
        assertFalse(viewModel.state.isChangingLifecycle)
    }

    @Test
    fun confirmingMovesTheProjectAndTheNewStatusArrivesThroughTheCache() = runTest {
        serve(ProjectStatus.Recruiting)
        val viewModel = viewModel()
        viewModel.onLifecycleClick(start)

        viewModel.onLifecycleConfirm()

        assertEquals(listOf(id to start), repository.lifecycleMoves)
        with(viewModel.state) {
            assertNull(lifecycleDialog)
            assertFalse(isChangingLifecycle)
            assertNull(lifecycleFailure)
            assertEquals(ProjectStatus.Started, project?.status)
            assertEquals(listOf(complete, cancel), project?.lifecycleActions)
        }
    }

    @Test
    fun aSecondConfirmWhileTheRequestIsOnItsWayMovesNothingTwice() = runTest {
        serve()
        repository.lifecycleGate = Gate()
        val viewModel = viewModel()
        viewModel.onLifecycleClick(start)

        viewModel.onLifecycleConfirm()
        assertTrue(viewModel.state.isChangingLifecycle)
        // The button is tapped again as the dialog fades out; another dialog is asked for meanwhile.
        viewModel.onLifecycleConfirm()
        viewModel.onLifecycleClick(cancel)
        viewModel.onLifecycleConfirm()

        assertEquals(1, repository.lifecycleMoves.size)
        assertNull(viewModel.state.lifecycleDialog, "no other dialog while a move is on its way")
        repository.lifecycleGate!!.open()
        assertFalse(viewModel.state.isChangingLifecycle)
    }

    @Test
    fun theDialogCannotBeConfirmedTwiceAfterTheMoveHasEnded() = runTest {
        serve()
        val viewModel = viewModel()
        viewModel.onLifecycleClick(start)

        viewModel.onLifecycleConfirm()
        viewModel.onLifecycleConfirm()

        assertEquals(1, repository.lifecycleMoves.size)
    }

    @Test
    fun aMoveAndAnUnpublishingBlockEachOther() = runTest {
        serve()
        repository.unpublishGate = Gate()
        val viewModel = viewModel()
        viewModel.onUnpublishClick()
        viewModel.onUnpublishConfirm()
        assertTrue(viewModel.state.isUnpublishing)

        viewModel.onLifecycleClick(start)
        viewModel.onLifecycleConfirm()

        assertNull(viewModel.state.lifecycleDialog)
        assertEquals(emptyList(), repository.lifecycleMoves)
        repository.unpublishGate!!.open()

        // The other way round.
        serve()
        val other = viewModel()
        repository.lifecycleGate = Gate()
        other.onLifecycleClick(start)
        other.onLifecycleConfirm()
        assertTrue(other.state.isChangingLifecycle)

        other.onUnpublishClick()

        assertFalse(other.state.isUnpublishConfirmVisible)
        repository.lifecycleGate!!.open()
    }

    @Test
    fun aDialogOpenedBeforeAnUnpublishingStartedDoesNotConfirmDuringIt() = runTest {
        serve()
        repository.unpublishGate = Gate()
        val viewModel = viewModel()
        viewModel.onLifecycleClick(start)
        // Both dialogs can be open: the unpublishing is confirmed first, so the move sends nothing.
        viewModel.onUnpublishClick()
        viewModel.onUnpublishConfirm()

        viewModel.onLifecycleConfirm()

        assertEquals(emptyList(), repository.lifecycleMoves)
        repository.unpublishGate!!.open()
    }

    // ---- outcomes ----

    @Test
    fun aMoveTheServerRefusedIsNotAllowedAndTheScreenStays() = runTest {
        serve()
        val viewModel = viewModel()
        repository.lifecycleFailure = ProjectConflictException(id)
        viewModel.onLifecycleClick(start)

        viewModel.onLifecycleConfirm()

        assertEquals(LifecycleFailure.NotAllowed, viewModel.state.lifecycleFailure)
        assertFalse(viewModel.state.isChangingLifecycle)
        assertEquals("Проект p1", viewModel.state.project?.title, "the screen stays")
    }

    @Test
    fun anActionNotOfferedForTheStatusIsNotAllowedAndSendsNothing() = runTest {
        serve(ProjectStatus.Finished)
        val viewModel = viewModel()
        viewModel.onLifecycleClick(start)

        viewModel.onLifecycleConfirm()

        assertEquals(LifecycleFailure.NotAllowed, viewModel.state.lifecycleFailure)
        assertEquals(emptyList(), repository.lifecycleMoves)
    }

    @Test
    fun aProjectThatIsGoneIsTheNotFoundScreen() = runTest {
        serve()
        val viewModel = viewModel()
        repository.lifecycleFailure = ProjectNotFoundException(id)
        viewModel.onLifecycleClick(cancel)

        viewModel.onLifecycleConfirm()

        with(viewModel.state) {
            assertNull(project)
            assertFalse(isLoading)
            assertNull(loadFailure)
            assertFalse(isChangingLifecycle)
        }
    }

    @Test
    fun anotherFailureIsToldByItsCause() = runTest {
        serve()
        val viewModel = viewModel()
        repository.lifecycleFailure = cause
        viewModel.onLifecycleClick(cancel)

        viewModel.onLifecycleConfirm()

        val failure = assertIs<LifecycleFailure.Failed>(viewModel.state.lifecycleFailure)
        assertSame(cause, failure.cause)
        assertEquals(ProjectStatus.Recruiting, viewModel.state.project?.status)
    }

    @Test
    fun theNextActionClearsTheFailureAndATryAfterItIsPossible() = runTest {
        serve()
        val viewModel = viewModel()
        repository.lifecycleFailure = cause
        viewModel.onLifecycleClick(start)
        viewModel.onLifecycleConfirm()
        assertIs<LifecycleFailure.Failed>(viewModel.state.lifecycleFailure)

        viewModel.onLifecycleClick(start)
        assertNull(viewModel.state.lifecycleFailure)
        repository.lifecycleFailure = null
        viewModel.onLifecycleConfirm()

        assertEquals(2, repository.lifecycleMoves.size)
        assertEquals(ProjectStatus.Started, viewModel.state.project?.status)
    }

    @Test
    fun theUnpublishDialogOpenedAfterAFailedMoveClearsTheFailure() = runTest {
        serve()
        val viewModel = viewModel()
        repository.lifecycleFailure = cause
        viewModel.onLifecycleClick(start)
        viewModel.onLifecycleConfirm()

        viewModel.onUnpublishClick()

        assertNull(viewModel.state.lifecycleFailure)
    }

    @Test
    fun theLifecycleStatusesGiveTheirActions() = runTest {
        serve(ProjectStatus.Recruiting)
        val viewModel = viewModel()
        for ((action, status) in listOf(start to ProjectStatus.Started, complete to ProjectStatus.Finished)) {
            viewModel.onLifecycleClick(action)
            viewModel.onLifecycleConfirm()
            assertEquals(status, viewModel.state.project?.status)
        }
        assertEquals(emptyList(), viewModel.state.project?.lifecycleActions)
        assertFalse(viewModel.state.project!!.canEdit)
    }
}
