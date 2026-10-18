package org.matchlab.academic_net.feature.profile.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentDraftError
import org.matchlab.academic_net.feature.profile.domain.DocumentId
import org.matchlab.academic_net.feature.profile.domain.DocumentKind
import org.matchlab.academic_net.feature.profile.domain.EditOutcome
import org.matchlab.academic_net.feature.profile.domain.FakeMyProfileRepository
import org.matchlab.academic_net.feature.profile.domain.PortfolioChange
import org.matchlab.academic_net.feature.profile.domain.ProfileDraft
import org.matchlab.academic_net.feature.profile.domain.ProfileDraftError
import org.matchlab.academic_net.feature.profile.domain.ProofAttachment
import org.matchlab.academic_net.feature.profile.domain.VerificationOutcome
import org.matchlab.academic_net.feature.profile.domain.VerificationResult
import org.matchlab.academic_net.feature.profile.domain.VerificationResultId
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.feature.profile.domain.WorkDraftError
import org.matchlab.academic_net.feature.profile.domain.WorkId
import org.matchlab.academic_net.feature.profile.domain.document
import org.matchlab.academic_net.feature.profile.domain.myProfile
import org.matchlab.academic_net.feature.profile.domain.toDraft
import org.matchlab.academic_net.feature.profile.domain.work
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The use cases of the viewer's own profile: what they check themselves and what they hand to the repository. */
class MyProfileUseCasesTest {
    private val repository = FakeMyProfileRepository()

    private val goodWork = WorkDraft(title = "Статья", description = "О графах", link = "https://doi.org/10.1/x")
    private val goodDocument = DocumentDraft(
        title = "Диплом",
        issuer = "ВШЭ",
        year = "2025",
        proof = ProofAttachment("https://files.example.com/d.pdf"),
    )

    // ---- the profile ----

    @Test
    fun savingAnInvalidProfileSendsNothing() = runTest {
        val result = UpdateProfileUseCase(repository)(ProfileDraft(name = "  ", studyYear = "9"))

        assertEquals(
            SubmitResult.Invalid(setOf(ProfileDraftError.NameEmpty, ProfileDraftError.StudyYearInvalid)),
            result,
        )
        assertEquals(emptyList(), repository.calls)
    }

    @Test
    fun theProfileIsSavedNormalized() = runTest {
        val result = UpdateProfileUseCase(repository)(
            ProfileDraft(name = "  Арджун   Мехта ", about = " Текст ", skills = listOf("Kotlin", " kotlin ")),
        )

        assertIs<SubmitResult.Success<*>>(result)
        val sent = repository.updatedProfiles.single()
        assertEquals("Арджун Мехта", sent.name)
        assertEquals("Текст", sent.about)
        assertEquals(listOf("Kotlin"), sent.skills)
    }

    @Test
    fun aFailedSaveOfTheProfileIsAFailureWithItsCause() = runTest {
        val cause = IllegalStateException("no network")
        repository.onUpdateProfile = { throw cause }

        val result = UpdateProfileUseCase(repository)(ProfileDraft(name = "Имя"))

        assertEquals(SubmitResult.Failure(cause), result)
    }

    @Test
    fun aCancelledSaveIsNotAFailure() = runTest {
        repository.onUpdateProfile = { throw CancellationException("screen left") }

        assertFailsWith<CancellationException> { UpdateProfileUseCase(repository)(ProfileDraft(name = "Имя")) }
    }

    @Test
    fun aFailedRefreshIsAFailedResultAndACancelledOneIsRethrown() = runTest {
        repository.onRefresh = { throw IllegalStateException("no network") }
        assertTrue(RefreshMyProfileUseCase(repository)().isFailure)

        repository.onRefresh = { throw CancellationException("screen left") }
        assertFailsWith<CancellationException> { RefreshMyProfileUseCase(repository)() }
    }

    // ---- adding ----

    @Test
    fun anInvalidWorkIsNotAdded() = runTest {
        val result = AddWorkUseCase(repository)(WorkDraft(title = " ", description = "", link = "doi.org"))

        assertEquals(
            SubmitResult.Invalid(setOf(WorkDraftError.TitleEmpty, WorkDraftError.DescriptionEmpty, WorkDraftError.LinkInvalid)),
            result,
        )
        assertEquals(emptyList(), repository.calls)
    }

    @Test
    fun aWorkIsAddedNormalizedAndWithItsLinkSentForVerification() = runTest {
        val result = AddWorkUseCase(repository)(goodWork.copy(title = "  Статья   о графах ", link = " https://doi.org/10.1/x "))

        val saved = assertIs<SubmitResult.Success<PortfolioChange<*>>>(result).value
        assertEquals(true, assertIs<PortfolioChange.Saved<*>>(saved).sent)
        val sent = repository.createdWorks.single()
        assertEquals("Статья о графах", sent.title)
        assertEquals("https://doi.org/10.1/x", sent.link)
    }

    @Test
    fun aWorkWithoutALinkIsAddedUnconfirmed() = runTest {
        val result = AddWorkUseCase(repository)(goodWork.copy(link = ""))

        val saved = assertIs<PortfolioChange.Saved<*>>(assertIs<SubmitResult.Success<PortfolioChange<*>>>(result).value)
        assertEquals(false, saved.sent)
    }

    @Test
    fun aFailureToAddAWorkIsAFailure() = runTest {
        val cause = IllegalStateException("no network")
        repository.onCreateWork = { throw cause }

        assertEquals(SubmitResult.Failure(cause), AddWorkUseCase(repository)(goodWork))
    }

    @Test
    fun aDocumentWithoutAProofIsNotAdded() = runTest {
        val result = AddDocumentUseCase(repository)(goodDocument.copy(proof = null))

        assertEquals(SubmitResult.Invalid(setOf(DocumentDraftError.ProofMissing)), result)
        assertEquals(emptyList(), repository.calls)
    }

    @Test
    fun aDocumentWithAProofFileOfTheServerNeedsNoLink() = runTest {
        val result = AddDocumentUseCase(repository)(goodDocument.copy(proof = null, hasEvidenceFile = true))

        assertIs<SubmitResult.Success<*>>(result)
    }

    @Test
    fun aDocumentIsAddedNormalized() = runTest {
        AddDocumentUseCase(repository)(
            goodDocument.copy(title = "  Диплом   бакалавра ", year = " 2025 ", proof = ProofAttachment(" https://x.org/d.pdf ")),
        )

        val sent = repository.createdDocuments.single()
        assertEquals("Диплом бакалавра", sent.title)
        assertEquals("2025", sent.year)
        assertEquals(ProofAttachment("https://x.org/d.pdf"), sent.proof)
    }

    // ---- loading a record ----

    @Test
    fun theFormOfAWorkLoadsTheWorkOrSaysWhyNot() = runTest {
        assertEquals(LoadResult.Found(work("w9")), GetMyWorkUseCase(repository)(WorkId("w9")))

        repository.onGetMyWork = { null }
        assertEquals(LoadResult.NotFound, GetMyWorkUseCase(repository)(WorkId("w9")))

        val cause = IllegalStateException("no network")
        repository.onGetMyWork = { throw cause }
        assertEquals(LoadResult.Failure(cause), GetMyWorkUseCase(repository)(WorkId("w9")))
    }

    @Test
    fun theFormOfADocumentLoadsTheDocumentOrSaysWhyNot() = runTest {
        assertEquals(LoadResult.Found(document("d9")), GetMyDocumentUseCase(repository)(DocumentId("d9")))

        repository.onGetMyDocument = { null }
        assertEquals(LoadResult.NotFound, GetMyDocumentUseCase(repository)(DocumentId("d9")))
    }

    @Test
    fun aCancelledLoadIsRethrown() = runTest {
        repository.onGetMyWork = { throw CancellationException("screen left") }

        assertFailsWith<CancellationException> { GetMyWorkUseCase(repository)(WorkId("w1")) }
    }

    // ---- saving a record that is edited ----

    @Test
    fun savingAWorkThatWasNotChangedAsksNothing() = runTest {
        val base = work(title = "Статья")

        val result = SaveWorkUseCase(repository)(base, base.toDraft(), send = false)

        assertEquals(SubmitResult.Success(PortfolioChange.Saved(base, sent = false)), result)
        assertEquals(emptyList(), repository.calls)
    }

    @Test
    fun spacesAroundTheTextAreNotAChange() = runTest {
        val base = work(title = "Статья о графах", description = "Описание")

        val result = SaveWorkUseCase(repository)(
            base,
            base.toDraft().copy(title = "  Статья   о графах ", description = " Описание \n"),
            send = false,
        )

        assertEquals(SubmitResult.Success(PortfolioChange.Saved(base, sent = false)), result)
        assertEquals(emptyList(), repository.calls, "a PUT would reset the confirmation for nothing")
    }

    @Test
    fun aChangedWorkIsSavedOnTheRevisionItWasOpenedOn() = runTest {
        val base = work(revision = 7)

        SaveWorkUseCase(repository)(base, base.toDraft().copy(title = "Другое название"), send = false)

        val (id, draft, revision) = repository.updatedWorks.single()
        assertEquals(base.id, id)
        assertEquals("Другое название", draft.title)
        assertEquals(7, revision)
        assertEquals(emptyList(), repository.submitted)
    }

    @Test
    fun aChangedWorkThatIsSentIsSavedAndThenSent() = runTest {
        val base = work(id = "w5")

        val result = SaveWorkUseCase(repository)(base, base.toDraft().copy(title = "Другое"), send = true)

        val saved = assertIs<PortfolioChange.Saved<*>>(assertIs<SubmitResult.Success<PortfolioChange<*>>>(result).value)
        assertTrue(saved.sent)
        assertEquals(listOf("updateWork", "submit"), repository.calls)
        assertEquals(VerificationSubject.Work(WorkId("w5")), repository.submitted.single())
    }

    @Test
    fun aWorkThatWasNotChangedCanStillBeSent() = runTest {
        val base = work(id = "w5")

        val result = SaveWorkUseCase(repository)(base, base.toDraft(), send = true)

        assertEquals(SubmitResult.Success(PortfolioChange.Saved(base, sent = true)), result)
        assertEquals(listOf("submit"), repository.calls)
    }

    @Test
    fun aWorkWithoutALinkOrAFileIsNeverSent() = runTest {
        val base = work(link = null)

        SaveWorkUseCase(repository)(base, base.toDraft().copy(title = "Другое"), send = true)

        assertEquals(listOf("updateWork"), repository.calls)
    }

    @Test
    fun aConflictOnSavingAWorkIsReported() = runTest {
        repository.onUpdateWork = { _, _, _ -> EditOutcome.Conflict }
        val base = work()

        val result = SaveWorkUseCase(repository)(base, base.toDraft().copy(title = "Другое"), send = true)

        assertEquals(SubmitResult.Success(PortfolioChange.Conflict), result)
        assertEquals(listOf("updateWork"), repository.calls)
    }

    @Test
    fun aGoneWorkOnSavingIsReported() = runTest {
        repository.onUpdateWork = { _, _, _ -> EditOutcome.NotFound }
        val base = work()

        val result = SaveWorkUseCase(repository)(base, base.toDraft().copy(title = "Другое"), send = false)

        assertEquals(SubmitResult.Success(PortfolioChange.NotFound), result)
    }

    @Test
    fun anInvalidFormOfAWorkIsNotSaved() = runTest {
        val base = work()

        val result = SaveWorkUseCase(repository)(base, base.toDraft().copy(title = ""), send = true)

        assertEquals(SubmitResult.Invalid(setOf(WorkDraftError.TitleEmpty)), result)
        assertEquals(emptyList(), repository.calls)
    }

    @Test
    fun aFailureOfTheOnlySendIsAFailureOfTheCall() = runTest {
        val cause = IllegalStateException("no network")
        repository.onSubmit = { throw cause }
        val base = work()

        val result = SaveWorkUseCase(repository)(base, base.toDraft(), send = true)

        assertEquals(SubmitResult.Failure(cause), result)
    }

    @Test
    fun aFailureOfTheSendAfterASaveLeavesTheWorkSavedButNotSent() = runTest {
        repository.onSubmit = { throw IllegalStateException("no network") }
        val base = work()

        val result = SaveWorkUseCase(repository)(base, base.toDraft().copy(title = "Другое"), send = true)

        val saved = assertIs<PortfolioChange.Saved<*>>(assertIs<SubmitResult.Success<PortfolioChange<*>>>(result).value)
        assertEquals(false, saved.sent)
    }

    @Test
    fun aDocumentIsSavedAndSentOnTheSameRules() = runTest {
        val base = document(id = "d5", kind = DocumentKind.Certificate, revision = 4)

        val unchanged = SaveDocumentUseCase(repository)(base, base.toDraft(), send = false)
        assertEquals(SubmitResult.Success(PortfolioChange.Saved(base, sent = false)), unchanged)
        assertEquals(emptyList(), repository.calls)

        SaveDocumentUseCase(repository)(base, base.toDraft().copy(issuer = "Coursera"), send = true)

        assertEquals(listOf("updateDocument", "submit"), repository.calls)
        assertEquals(4, repository.updatedDocuments.single().third)
        assertEquals(VerificationSubject.Document(DocumentId("d5"), DocumentKind.Certificate), repository.submitted.single())
    }

    @Test
    fun aDocumentThatLostItsProofIsNotSaved() = runTest {
        val base = document()

        val result = SaveDocumentUseCase(repository)(base, base.toDraft().copy(proof = null), send = false)

        assertEquals(SubmitResult.Invalid(setOf(DocumentDraftError.ProofMissing)), result)
        assertEquals(emptyList(), repository.calls)
    }

    // ---- sending and deleting ----

    @Test
    fun sendingARecordPassesTheOutcomeOfTheServer() = runTest {
        val subject = VerificationSubject.Work(WorkId("w1"))

        assertEquals(Result.success(EditOutcome.Done(Unit)), SubmitForVerificationUseCase(repository)(subject))

        repository.onSubmit = { EditOutcome.Conflict }
        assertEquals(Result.success(EditOutcome.Conflict), SubmitForVerificationUseCase(repository)(subject))
    }

    @Test
    fun aFailureToSendIsAFailedResultAndACancellationIsRethrown() = runTest {
        val subject = VerificationSubject.Work(WorkId("w1"))
        repository.onSubmit = { throw IllegalStateException("no network") }
        assertTrue(SubmitForVerificationUseCase(repository)(subject).isFailure)

        repository.onSubmit = { throw CancellationException("screen left") }
        assertFailsWith<CancellationException> { SubmitForVerificationUseCase(repository)(subject) }
    }

    @Test
    fun deletingARecordPassesTheOutcomeOfTheServer() = runTest {
        val subject = VerificationSubject.Document(DocumentId("d1"), DocumentKind.Education)

        assertEquals(Result.success(EditOutcome.Done(Unit)), DeletePortfolioItemUseCase(repository)(subject))
        assertEquals<List<VerificationSubject>>(listOf(subject), repository.deleted)

        repository.onDelete = { throw IllegalStateException("no network") }
        assertTrue(DeletePortfolioItemUseCase(repository)(subject).isFailure)
    }

    // ---- the results of the verification ----

    private fun result(id: String, seen: Boolean) = VerificationResult(
        id = VerificationResultId(id),
        subject = VerificationSubject.Work(WorkId("w")),
        subjectTitle = "Работа",
        outcome = VerificationOutcome.Verified,
        decidedAt = Instant.fromEpochSeconds(1_760_000_000),
        isSeen = seen,
    )

    @Test
    fun theDotIsOnWhileAnUnseenResultExistsAndOnlyChangesAreEmitted() = runTest {
        val emissions = mutableListOf<Boolean>()
        repository.results = flowOf(
            emptyList(),
            listOf(result("1", seen = true)),
            listOf(result("1", seen = true), result("2", seen = false)),
            listOf(result("1", seen = true), result("2", seen = false), result("3", seen = false)),
            listOf(result("1", seen = true), result("2", seen = true), result("3", seen = true)),
        )

        ObserveHasUnseenResultsUseCase(repository)().collect { emissions += it }

        assertEquals(listOf(false, true, false), emissions)
    }

    @Test
    fun theViewersProfileIsObservedFromTheRepository() = runTest {
        repository.profile = myProfile()

        assertEquals(repository.profile, ObserveMyProfileUseCase(repository)().first())
    }

    @Test
    fun markingResultsSeenReachesTheRepositoryAndAFailureIsAFailedResult() = runTest {
        val ids = setOf(VerificationResultId("a"), VerificationResultId("b"))

        assertTrue(MarkResultsSeenUseCase(repository)(ids).isSuccess)
        assertEquals(listOf(ids), repository.seen)
    }
}
