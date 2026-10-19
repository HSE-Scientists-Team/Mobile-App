package org.matchlab.academic_net.feature.complaints.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.complaints.domain.Complaint
import org.matchlab.academic_net.feature.complaints.domain.ComplaintDescriptionMaxLength
import org.matchlab.academic_net.feature.complaints.domain.ComplaintDraftError
import org.matchlab.academic_net.feature.complaints.domain.ComplaintNotAllowedException
import org.matchlab.academic_net.feature.complaints.domain.ComplaintSubject
import org.matchlab.academic_net.feature.complaints.domain.ComplaintTarget
import org.matchlab.academic_net.feature.complaints.domain.ComplaintsRepository
import org.matchlab.academic_net.feature.complaints.domain.GetComplaintSubjectUseCase
import org.matchlab.academic_net.feature.complaints.domain.ProjectComplaintReason
import org.matchlab.academic_net.feature.complaints.domain.SendComplaintUseCase
import org.matchlab.academic_net.feature.complaints.domain.UserComplaintReason
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ComplaintViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FakeRepository : ComplaintsRepository {
        var subject: ComplaintSubject? = null
        var subjectFailure: Throwable? = null
        var subjectGate: CompletableDeferred<Unit>? = null
        var sendFailure: Throwable? = null
        var sendGate: CompletableDeferred<Unit>? = null
        val sent = mutableListOf<Complaint>()

        override suspend fun getSubject(target: ComplaintTarget): ComplaintSubject? {
            subjectGate?.await()
            subjectFailure?.let { throw it }
            return subject
        }

        override suspend fun send(complaint: Complaint) {
            sent += complaint
            sendGate?.await()
            sendFailure?.let { throw it }
        }
    }

    private val repository = FakeRepository()
    private val user = ComplaintTarget.User(UserId("u1"))
    private val project = ComplaintTarget.Project(ProjectId("p1"))

    private fun viewModel(target: ComplaintTarget = user) =
        ComplaintViewModel(target, GetComplaintSubjectUseCase(repository), SendComplaintUseCase(repository))

    private val ComplaintViewModel.state get() = uiState.value

    private fun ComplaintViewModel.fill() {
        onReasonSelected(UserComplaintReason.Scam)
        onDescriptionChange("Просит деньги")
    }

    // ---- the form and its card ----

    @Test
    fun theReasonsAreThoseOfTheTargetInTheOrderOfTheFrame() {
        assertEquals(UserComplaintReason.entries, viewModel(user).state.reasons)
        assertEquals(ProjectComplaintReason.entries, viewModel(project).state.reasons)
    }

    @Test
    fun theCardOfTheTargetComesWhenFound() {
        val subject = ComplaintSubject.PersonSubject(Collaborator(UserSummary(UserId("u1"), "Анна"), "", Study(), ""))
        repository.subject = subject
        repository.subjectGate = CompletableDeferred()

        val viewModel = viewModel()
        assertNull(viewModel.state.subject)
        repository.subjectGate!!.complete(Unit)

        assertSame(subject, viewModel.state.subject)
    }

    @Test
    fun withoutACardTheFormWorksAllTheSame() {
        for (broken in listOf<() -> Unit>({ repository.subject = null }, { repository.subjectFailure = RemoteFailure.Timeout() })) {
            broken()
            val viewModel = viewModel()

            viewModel.fill()
            viewModel.onSubmit()

            assertNull(viewModel.state.subject)
            assertTrue(viewModel.state.isSent)
        }
    }

    // ---- validation (17c) ----

    @Test
    fun submittingAnEmptyFormShowsTheErrorsAndSendsNothing() {
        val viewModel = viewModel()

        viewModel.onSubmit()

        assertEquals(
            setOf(ComplaintDraftError.ReasonMissing, ComplaintDraftError.DescriptionMissing),
            viewModel.state.errors,
        )
        assertFalse(viewModel.state.isSubmitting, "the user may fix the form")
        assertTrue(repository.sent.isEmpty())
    }

    @Test
    fun editingAFieldClearsOnlyItsErrors() {
        val viewModel = viewModel()
        viewModel.onSubmit()

        viewModel.onReasonSelected(UserComplaintReason.Spam)
        assertEquals(setOf(ComplaintDraftError.DescriptionMissing), viewModel.state.errors)

        viewModel.onDescriptionChange("x")
        assertEquals(emptySet(), viewModel.state.errors)
    }

    @Test
    fun aTooLongDescriptionIsAnErrorAndSendsNothing() {
        val viewModel = viewModel()
        viewModel.onReasonSelected(UserComplaintReason.Spam)
        viewModel.onDescriptionChange("x".repeat(ComplaintDescriptionMaxLength + 1))

        viewModel.onSubmit()

        assertEquals(setOf(ComplaintDraftError.DescriptionTooLong), viewModel.state.errors)
        assertTrue(repository.sent.isEmpty())
        viewModel.onDescriptionChange("x".repeat(ComplaintDescriptionMaxLength))
        viewModel.onSubmit()
        assertTrue(viewModel.state.isSent, "exactly the limit goes")
    }

    @Test
    fun aReasonOfTheOtherKindIsNotAcceptedForTheTarget() {
        val viewModel = viewModel(project)
        viewModel.onReasonSelected(UserComplaintReason.Spam)
        viewModel.onDescriptionChange("x")

        viewModel.onSubmit()

        assertEquals(setOf(ComplaintDraftError.ReasonMissing), viewModel.state.errors)
        assertTrue(repository.sent.isEmpty())
    }

    // ---- sending ----

    @Test
    fun aSentComplaintCarriesTheTargetTheReasonAndTheTrimmedText() {
        val viewModel = viewModel(project)
        viewModel.onReasonSelected(ProjectComplaintReason.FalseInformation)
        viewModel.onDescriptionChange("  Неправда  ")

        viewModel.onSubmit()

        assertEquals(listOf(Complaint(project, ProjectComplaintReason.FalseInformation, "Неправда")), repository.sent)
        assertTrue(viewModel.state.isSent)
    }

    @Test
    fun theGuardHoldsWhileSendingAndAfterTheSuccess() {
        repository.sendGate = CompletableDeferred()
        val viewModel = viewModel()
        viewModel.fill()

        viewModel.onSubmit()
        assertTrue(viewModel.state.isSubmitting)
        viewModel.onSubmit()
        viewModel.onDescriptionChange("changed")
        viewModel.onReasonSelected(UserComplaintReason.Other)
        assertEquals("Просит деньги", viewModel.state.description, "the form is frozen while sending")
        repository.sendGate!!.complete(Unit)

        assertTrue(viewModel.state.isSent)
        assertTrue(viewModel.state.isSubmitting, "the guard stays up on 17e")
        viewModel.onSubmit()
        assertEquals(1, repository.sent.size, "a repeat would be a second complaint")
    }

    @Test
    fun aRefusalOf403Is17fAndTheUserMaySendAgain() {
        repository.sendFailure = ComplaintNotAllowedException()
        val viewModel = viewModel()
        viewModel.fill()

        viewModel.onSubmit()

        assertEquals(ComplaintFailure.NotAllowed, viewModel.state.failure)
        assertFalse(viewModel.state.isSubmitting)
        assertFalse(viewModel.state.isSent)
        repository.sendFailure = null
        viewModel.onSubmit()
        assertNull(viewModel.state.failure)
        assertTrue(viewModel.state.isSent)
        assertEquals(2, repository.sent.size)
    }

    @Test
    fun everyOtherFailureIs17gWithItsCause() {
        for (cause in listOf(RemoteFailure.Http(400), RemoteFailure.Http(404), RemoteFailure.Http(409), RemoteFailure.Http(500), RemoteFailure.NoConnection(), RemoteFailure.Timeout())) {
            repository.sendFailure = cause
            val viewModel = viewModel()
            viewModel.fill()

            viewModel.onSubmit()

            val failure = assertIs<ComplaintFailure.Failed>(viewModel.state.failure)
            assertSame(cause, failure.cause)
            assertFalse(viewModel.state.isSubmitting)
        }
    }

    @Test
    fun editingAfterAFailureClearsIt() {
        repository.sendFailure = RemoteFailure.Http(500)
        val viewModel = viewModel()
        viewModel.fill()
        viewModel.onSubmit()

        viewModel.onDescriptionChange("ещё")

        assertNull(viewModel.state.failure)
    }
}
