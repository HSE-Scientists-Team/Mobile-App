package org.matchlab.academic_net.feature.responses.ui

import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.FeatureUnavailableException
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.responses.domain.JoinMessageMaxLength
import org.matchlab.academic_net.feature.responses.domain.JoinRefusal
import org.matchlab.academic_net.feature.responses.domain.JoinRefusedException
import org.matchlab.academic_net.feature.responses.domain.usecase.GetInviteOptionsUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.InviteToProjectUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 09k–09q: the options, the automatic and the preselected choice, the button, the guard and the refusals. */
class InviteToProjectViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val projectJoins = FakeProjectJoins()
    private val projects = FakeProjectSource()

    private fun viewModel(preselected: InvitePreselection? = null) = InviteToProjectViewModel(
        UserId("cand"),
        preselected,
        GetInviteOptionsUseCase(projects),
        InviteToProjectUseCase(projectJoins),
    )

    private val InviteToProjectViewModel.state get() = uiState.value

    private fun mine(vararg list: org.matchlab.academic_net.common.model.Project) {
        projects.myProjects = list.toList()
    }

    @Test
    fun noProjectsIsAnEmptyOptionListAndNoButton() {
        val state = viewModel().state

        assertFalse(state.isLoading)
        assertEquals(emptyList(), state.options)
        assertFalse(state.canSubmit)
    }

    @Test
    fun aServerThatCannotListMineIsTheSameEmptySheet() {
        projects.myProjectsFailure = FeatureUnavailableException("G44")

        val state = viewModel().state

        assertEquals(emptyList(), state.options)
        assertNull(state.loadFailure)
    }

    @Test
    fun aFailureToReadTheOptionsIsRetriedByOnRetry() {
        projects.myProjectsFailure = RemoteFailure.NoConnection()
        mine(project("p1"))
        val viewModel = viewModel()
        assertIs<RemoteFailure.NoConnection>(viewModel.state.loadFailure)

        projects.myProjectsFailure = null
        viewModel.onRetry()

        assertNull(viewModel.state.loadFailure)
        assertEquals(1, viewModel.state.options.size)
    }

    @Test
    fun theOptionsAreOpenProjectsWithAFreeRole() {
        mine(
            project("open"),
            project("paused", status = ProjectStatus.Unpublished),
            project("full", roles = listOf(role("r1", 1, 1))),
        )

        assertEquals(listOf("open"), viewModel().state.options.map { it.project.id.value })
    }

    @Test
    fun oneProjectWithOneRoleIsChosenAtOnce() {
        mine(project("p1", listOf(role("r1"))))

        val state = viewModel().state

        assertEquals(ProjectId("p1"), state.selectedProject)
        assertEquals(RoleId("r1"), state.selectedRole)
        assertTrue(state.canSubmit)
    }

    @Test
    fun oneProjectWithSeveralRolesChoosesTheProjectButNotTheRole() {
        mine(project("p1", listOf(role("r1"), role("r2"))))

        val state = viewModel().state

        assertEquals(ProjectId("p1"), state.selectedProject)
        assertNull(state.selectedRole)
        assertFalse(state.canSubmit)
    }

    @Test
    fun severalProjectsChooseNothing() {
        mine(project("p1"), project("p2"))

        val state = viewModel().state

        assertNull(state.selectedProject)
        assertFalse(state.canSubmit)
    }

    @Test
    fun aPreselectedRoleIsChosenWhenItIsAmongTheOptions() {
        mine(project("p1"), project("p2", listOf(role("a"), role("b"))))

        val state = viewModel(InvitePreselection(ProjectId("p2"), RoleId("b"))).state

        assertEquals(ProjectId("p2"), state.selectedProject)
        assertEquals(RoleId("b"), state.selectedRole)
        assertEquals(listOf("a", "b"), state.roles.map { it.id.value })
    }

    @Test
    fun aPreselectionThatIsNotAnOptionIsIgnored() {
        mine(project("p1"), project("p2"))

        for (pre in listOf(
            InvitePreselection(ProjectId("p9"), RoleId("r1")),
            InvitePreselection(ProjectId("p1"), RoleId("gone")),
        )) {
            val state = viewModel(pre).state
            assertNull(state.selectedProject, "$pre")
            assertNull(state.selectedRole, "$pre")
        }
    }

    @Test
    fun aPreselectionOfAFullRoleFallsBackToTheAutomaticChoice() {
        mine(project("p1", listOf(role("free"), role("taken", 1, 1))))

        val state = viewModel(InvitePreselection(ProjectId("p1"), RoleId("taken"))).state

        assertEquals(ProjectId("p1"), state.selectedProject)
        assertEquals(RoleId("free"), state.selectedRole)
    }

    // ---- choosing ----

    @Test
    fun changingTheProjectDropsARoleThatIsNotItsOwn() {
        mine(project("p1", listOf(role("a"), role("b"))), project("p2", listOf(role("c"))))
        val viewModel = viewModel()
        viewModel.onProjectSelected(ProjectId("p1"))
        viewModel.onRoleSelected(RoleId("a"))
        assertTrue(viewModel.state.canSubmit)

        viewModel.onProjectSelected(ProjectId("p2"))

        assertEquals(ProjectId("p2"), viewModel.state.selectedProject)
        assertNull(viewModel.state.selectedRole)
        assertFalse(viewModel.state.canSubmit)
    }

    @Test
    fun aRoleOfAnotherProjectCannotBeChosen() {
        mine(project("p1", listOf(role("a"), role("b"))), project("p2", listOf(role("c"))))
        val viewModel = viewModel()
        viewModel.onProjectSelected(ProjectId("p1"))

        viewModel.onRoleSelected(RoleId("c"))

        assertNull(viewModel.state.selectedRole)
    }

    @Test
    fun theMessageIsCutAtTheLimit() {
        val viewModel = viewModel()

        viewModel.onMessageChange("x".repeat(JoinMessageMaxLength + 10))

        assertEquals(JoinMessageMaxLength, viewModel.state.message.length)
    }

    // ---- sending ----

    @Test
    fun theInvitationGoesToTheCandidateWithTheTrimmedMessage() {
        mine(project("p1", listOf(role("r1"))))
        val viewModel = viewModel()
        viewModel.onMessageChange("  Пойдём  ")

        viewModel.onSubmit()

        assertEquals(listOf("invite p1 r1 cand Пойдём"), projectJoins.calls)
        assertTrue(viewModel.state.isInvited)
    }

    @Test
    fun sendingWithoutAProjectAndARoleDoesNothing() {
        mine(project("p1", listOf(role("a"), role("b"))))
        val viewModel = viewModel()

        viewModel.onSubmit()

        assertTrue(projectJoins.calls.isEmpty())
        assertFalse(viewModel.state.isSubmitting)
    }

    @Test
    fun theGuardHoldsWhileSendingAndAfterTheSuccess() {
        mine(project("p1", listOf(role("r1"))))
        projectJoins.gate = Gate()
        val viewModel = viewModel()

        viewModel.onSubmit()
        viewModel.onSubmit()
        viewModel.onMessageChange("late")
        viewModel.onProjectSelected(ProjectId("p1"))
        assertEquals("", viewModel.state.message, "the form is frozen while sending")
        projectJoins.gate!!.open()
        viewModel.onSubmit()

        assertTrue(viewModel.state.isInvited)
        assertEquals(1, projectJoins.calls.size)
    }

    @Test
    fun theRefusalsHaveTheirOwnOutcomes() {
        val expected = mapOf(
            JoinRefusal.Conflict to InviteFailure.Conflict,
            JoinRefusal.NotAllowed to InviteFailure.NotAllowed,
            JoinRefusal.NotFound to InviteFailure.Gone,
        )
        mine(project("p1", listOf(role("r1"))))
        for ((refusal, failure) in expected) {
            projectJoins.failure = JoinRefusedException(refusal)
            val viewModel = viewModel()

            viewModel.onSubmit()

            assertEquals(failure, viewModel.state.failure, "$refusal")
            assertFalse(viewModel.state.isSubmitting)
            assertFalse(viewModel.state.isInvited)
        }
    }

    @Test
    fun anyOtherFailureKeepsItsCauseAndTheNextTryClearsIt() {
        mine(project("p1", listOf(role("r1"))))
        val cause = RemoteFailure.Timeout()
        projectJoins.failure = cause
        val viewModel = viewModel()

        viewModel.onSubmit()
        assertSame(cause, assertIs<InviteFailure.Failed>(viewModel.state.failure).cause)

        projectJoins.failure = null
        viewModel.onSubmit()
        assertNull(viewModel.state.failure)
        assertTrue(viewModel.state.isInvited)
    }
}
