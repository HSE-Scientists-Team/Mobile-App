package org.matchlab.academic_net.feature.responses.ui

import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.responses.domain.JoinMessageMaxLength
import org.matchlab.academic_net.feature.responses.domain.JoinRefusal
import org.matchlab.academic_net.feature.responses.domain.JoinRefusedException
import org.matchlab.academic_net.feature.responses.domain.usecase.ApplyToRoleUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.GetRoleToApplyUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 04s–04w: the role of the sheet, the counter of 500, the guard of the send and the outcomes of the refusals. */
class ApplyToRoleViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val joins = FakeMyJoins(emptyList())
    private val projects = FakeProjectSource(listOf(project("p1", listOf(role("r1", skills = listOf("Kotlin"))))))

    private fun viewModel(roleId: String = "r1", projectId: String = "p1") = ApplyToRoleViewModel(
        ProjectId(projectId),
        RoleId(roleId),
        GetRoleToApplyUseCase(projects),
        ApplyToRoleUseCase(joins),
    )

    private val ApplyToRoleViewModel.state get() = uiState.value

    @Test
    fun theFormShowsTheRoleAndItsSkills() {
        val state = viewModel().state

        assertTrue(state.hasForm)
        assertEquals("Роль r1", state.roleTitle)
        assertEquals(listOf("Kotlin"), state.skills)
    }

    @Test
    fun aMissingRoleOrProjectIsGoneInsteadOfTheForm() {
        for (state in listOf(viewModel(roleId = "zzz").state, viewModel(projectId = "hidden").state)) {
            assertTrue(state.isGone)
            assertFalse(state.hasForm)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun theFormIsLoadingUntilTheRoleComes() {
        val gate = Gate()
        projects.getFailures.clear()
        val viewModel = ApplyToRoleViewModel(
            ProjectId("p1"),
            RoleId("r1"),
            GetRoleToApplyUseCase(object : org.matchlab.academic_net.feature.responses.domain.ResponsesProjectSource by projects {
                override suspend fun getProject(id: ProjectId) = projects.getProject(id).also { gate.await() }
            }),
            ApplyToRoleUseCase(joins),
        )

        assertTrue(viewModel.state.isLoading)
        assertFalse(viewModel.state.hasForm)
        gate.open()
        assertTrue(viewModel.state.hasForm)
    }

    @Test
    fun aFailureToReadTheRoleIsRetriedByOnRetryOnly() {
        projects.getFailures[ProjectId("p1")] = RemoteFailure.NoConnection()
        val viewModel = viewModel()
        assertIs<RemoteFailure.NoConnection>(viewModel.state.loadFailure)
        assertFalse(viewModel.state.hasForm)

        projects.getFailures.clear()
        viewModel.onRetry()

        assertNull(viewModel.state.loadFailure)
        assertTrue(viewModel.state.hasForm)
        val calls = projects.getCalls.size
        viewModel.onRetry()
        assertEquals(calls, projects.getCalls.size, "retry without an error does nothing")
    }

    // ---- the field ----

    @Test
    fun theMessageIsCutAtTheLimit() {
        val viewModel = viewModel()

        viewModel.onMessageChange("x".repeat(JoinMessageMaxLength))
        assertEquals(JoinMessageMaxLength, viewModel.state.message.length)
        viewModel.onMessageChange("y".repeat(JoinMessageMaxLength + 37))

        assertEquals(JoinMessageMaxLength, viewModel.state.message.length)
        assertEquals("y", viewModel.state.message.take(1))
    }

    // ---- sending ----

    @Test
    fun theMessageIsSentTrimmedAndTheSheetIsApplied() {
        val viewModel = viewModel()
        viewModel.onMessageChange("  Привет  ")

        viewModel.onSubmit()

        assertEquals(listOf("apply p1 r1 Привет"), joins.calls)
        assertTrue(viewModel.state.isApplied)
    }

    @Test
    fun anEmptyMessageIsSentWithoutOne() {
        val viewModel = viewModel()

        viewModel.onSubmit()

        assertEquals(listOf("apply p1 r1 <none>"), joins.calls)
    }

    @Test
    fun theGuardHoldsWhileSendingAndAfterTheSuccess() {
        joins.gate = Gate()
        val viewModel = viewModel()
        viewModel.onMessageChange("Привет")

        viewModel.onSubmit()
        assertTrue(viewModel.state.isSubmitting)
        viewModel.onSubmit()
        viewModel.onMessageChange("changed")
        assertEquals("Привет", viewModel.state.message, "the field is frozen")
        joins.gate!!.open()

        assertTrue(viewModel.state.isApplied)
        assertTrue(viewModel.state.isSubmitting)
        viewModel.onSubmit()
        assertEquals(1, joins.calls.size, "a repeat would be a second application")
    }

    @Test
    fun sendingWithoutAFormDoesNothing() {
        val gone = viewModel(roleId = "zzz")

        gone.onSubmit()

        assertTrue(joins.calls.isEmpty())
        assertFalse(gone.state.isSubmitting)
    }

    @Test
    fun theRefusalsHaveTheirOwnOutcomes() {
        val expected = mapOf(
            JoinRefusal.Conflict to ApplyFailure.Conflict,
            JoinRefusal.NotAllowed to ApplyFailure.NotAllowed,
            JoinRefusal.NotFound to ApplyFailure.Gone,
        )
        for ((refusal, failure) in expected) {
            joins.failure = JoinRefusedException(refusal)
            val viewModel = viewModel()

            viewModel.onSubmit()

            assertEquals(failure, viewModel.state.failure, "$refusal")
            assertFalse(viewModel.state.isSubmitting, "the user may try again")
            assertFalse(viewModel.state.isApplied)
        }
    }

    @Test
    fun anyOtherFailureKeepsItsCauseAndTheNextTryClearsIt() {
        val cause = RemoteFailure.NoConnection()
        joins.failure = cause
        val viewModel = viewModel()

        viewModel.onSubmit()
        assertSame(cause, assertIs<ApplyFailure.Failed>(viewModel.state.failure).cause)

        joins.failure = null
        viewModel.onSubmit()
        assertNull(viewModel.state.failure)
        assertTrue(viewModel.state.isApplied)
    }

    @Test
    fun editingTheFieldClearsTheFailure() {
        joins.failure = RemoteFailure.Http(500)
        val viewModel = viewModel()
        viewModel.onSubmit()

        viewModel.onMessageChange("ещё")

        assertNull(viewModel.state.failure)
    }
}
