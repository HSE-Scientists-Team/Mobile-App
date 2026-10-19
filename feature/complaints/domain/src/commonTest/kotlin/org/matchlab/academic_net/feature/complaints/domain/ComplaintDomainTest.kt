package org.matchlab.academic_net.feature.complaints.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.common.result.SubmitResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val UserTarget = ComplaintTarget.User(UserId("u1"))
private val ProjectTarget = ComplaintTarget.Project(ProjectId("p1"))

class ComplaintReasonsTest {
    @Test
    fun aPersonHasSixReasonsWithCodesOneToSixInTheOrderOfTheFrame() {
        val reasons = complaintReasonsFor(UserTarget)

        assertEquals(UserComplaintReason.entries, reasons)
        assertEquals(listOf("1", "2", "3", "4", "5", "6"), reasons.map { it.code })
        assertEquals(
            listOf("Spam", "Insults", "PortfolioFraud", "FakeAccount", "Scam", "Other"),
            UserComplaintReason.entries.map { it.name },
        )
    }

    @Test
    fun aProjectHasFourReasonsWithCodesOneToFour() {
        val reasons = complaintReasonsFor(ProjectTarget)

        assertEquals(ProjectComplaintReason.entries, reasons)
        assertEquals(listOf("1", "2", "3", "4"), reasons.map { it.code })
        assertEquals(
            listOf("Spam", "FalseInformation", "Scam", "Other"),
            ProjectComplaintReason.entries.map { it.name },
        )
    }
}

class ComplaintDraftTest {
    private fun draft(
        target: ComplaintTarget = UserTarget,
        reason: ComplaintReason? = UserComplaintReason.Spam,
        text: String = "Рассылает рекламу",
    ) = ComplaintDraft(target, reason, text)

    @Test
    fun aFilledDraftIsValid() {
        assertEquals(emptySet(), draft().validate())
        assertEquals(emptySet(), draft(ProjectTarget, ProjectComplaintReason.Other).validate())
    }

    @Test
    fun noReasonIsMissing() {
        assertEquals(setOf(ComplaintDraftError.ReasonMissing), draft(reason = null).validate())
    }

    @Test
    fun aReasonOfTheOtherSetIsMissing() {
        assertEquals(setOf(ComplaintDraftError.ReasonMissing), draft(UserTarget, ProjectComplaintReason.Spam).validate())
        assertEquals(setOf(ComplaintDraftError.ReasonMissing), draft(ProjectTarget, UserComplaintReason.Spam).validate())
    }

    @Test
    fun anEmptyOrBlankDescriptionIsMissing() {
        assertEquals(setOf(ComplaintDraftError.DescriptionMissing), draft(text = "").validate())
        assertEquals(setOf(ComplaintDraftError.DescriptionMissing), draft(text = " \n\t ").validate())
    }

    @Test
    fun theLimitIsTenThousandAfterTheTrim() {
        assertEquals(10_000, ComplaintDescriptionMaxLength)
        assertEquals(emptySet(), draft(text = "x".repeat(10_000)).validate())
        assertEquals(emptySet(), draft(text = "  " + "x".repeat(10_000) + "  ").validate())
        assertEquals(setOf(ComplaintDraftError.DescriptionTooLong), draft(text = "x".repeat(10_001)).validate())
    }

    @Test
    fun everyErrorIsReportedAtOnce() {
        assertEquals(
            setOf(ComplaintDraftError.ReasonMissing, ComplaintDraftError.DescriptionMissing),
            draft(reason = null, text = "").validate(),
        )
        assertEquals(
            setOf(ComplaintDraftError.ReasonMissing, ComplaintDraftError.DescriptionTooLong),
            draft(reason = null, text = "x".repeat(10_001)).validate(),
        )
    }

    @Test
    fun normalizingTrimsOnlyTheDescription() {
        val normalized = draft(text = "  текст \n").normalized()

        assertEquals("текст", normalized.description)
        assertEquals(UserComplaintReason.Spam, normalized.reason)
        assertEquals(UserTarget, normalized.target)
    }
}

private class FakeComplaintsRepository : ComplaintsRepository {
    val sent = mutableListOf<Complaint>()
    var failure: Throwable? = null
    var subject: ComplaintSubject? = null
    val asked = mutableListOf<ComplaintTarget>()

    override suspend fun getSubject(target: ComplaintTarget): ComplaintSubject? {
        asked += target
        failure?.let { throw it }
        return subject
    }

    override suspend fun send(complaint: Complaint) {
        sent += complaint
        failure?.let { throw it }
    }
}

class SendComplaintUseCaseTest {
    private val repository = FakeComplaintsRepository()
    private val send = SendComplaintUseCase(repository)

    @Test
    fun aValidDraftIsSentWithTheTrimmedDescription() = runTest {
        val result = send(ComplaintDraft(ProjectTarget, ProjectComplaintReason.Scam, "  Обман  "))

        assertEquals(SubmitResult.Success(Unit), result)
        assertEquals(listOf(Complaint(ProjectTarget, ProjectComplaintReason.Scam, "Обман")), repository.sent)
    }

    @Test
    fun anInvalidDraftSendsNothingAndNamesTheErrors() = runTest {
        val result = send(ComplaintDraft(UserTarget, reason = null, description = "  "))

        assertEquals(
            SubmitResult.Invalid(setOf(ComplaintDraftError.ReasonMissing, ComplaintDraftError.DescriptionMissing)),
            result,
        )
        assertTrue(repository.sent.isEmpty())
    }

    @Test
    fun aTooLongDescriptionSendsNothing() = runTest {
        val result = send(ComplaintDraft(UserTarget, UserComplaintReason.Other, "x".repeat(10_001)))

        assertEquals(SubmitResult.Invalid(setOf(ComplaintDraftError.DescriptionTooLong)), result)
        assertTrue(repository.sent.isEmpty())
    }

    @Test
    fun aFailureKeepsItsCauseWhateverItIs() = runTest {
        for (cause in listOf(ComplaintNotAllowedException(), RemoteFailure.Http(500), RemoteFailure.NoConnection())) {
            repository.failure = cause

            val result = send(ComplaintDraft(UserTarget, UserComplaintReason.Spam, "x"))

            assertSame(cause, assertIs<SubmitResult.Failure>(result).cause)
        }
    }

    @Test
    fun aCancellationPassesThrough() = runTest {
        repository.failure = CancellationException("c")
        assertFailsWith<CancellationException> { send(ComplaintDraft(UserTarget, UserComplaintReason.Spam, "x")) }
    }
}

class GetComplaintSubjectUseCaseTest {
    private val repository = FakeComplaintsRepository()
    private val get = GetComplaintSubjectUseCase(repository)

    @Test
    fun aSubjectIsFound() = runTest {
        val subject = ComplaintSubject.PersonSubject(Collaborator(UserSummary(UserId("u1"), "Анна"), "", Study(), ""))
        repository.subject = subject

        assertEquals(LoadResult.Found(subject), get(UserTarget))
        assertEquals(listOf<ComplaintTarget>(UserTarget), repository.asked)
    }

    @Test
    fun noSubjectIsNotFound() = runTest {
        assertEquals(LoadResult.NotFound, get(ProjectTarget))
    }

    @Test
    fun aFailureIsAFailure() = runTest {
        val cause = RemoteFailure.Timeout()
        repository.failure = cause
        assertSame(cause, assertIs<LoadResult.Failure>(get(UserTarget)).cause)
    }

    @Test
    fun aCancellationPassesThrough() = runTest {
        repository.failure = CancellationException("c")
        assertFailsWith<CancellationException> { get(UserTarget) }
    }
}
