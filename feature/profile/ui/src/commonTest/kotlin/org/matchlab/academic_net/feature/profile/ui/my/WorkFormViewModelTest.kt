package org.matchlab.academic_net.feature.profile.ui.my

import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.feature.profile.domain.EditOutcome
import org.matchlab.academic_net.feature.profile.domain.PortfolioChange
import org.matchlab.academic_net.feature.profile.domain.UploadFile
import org.matchlab.academic_net.feature.profile.domain.VerificationStatus
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.feature.profile.domain.WorkDraftError
import org.matchlab.academic_net.feature.profile.domain.WorkId
import org.matchlab.academic_net.feature.profile.domain.usecase.AddWorkUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.DeletePortfolioItemUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetMyWorkUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.SaveWorkUseCase
import org.matchlab.academic_net.feature.profile.ui.FakeMyProfileRepository
import org.matchlab.academic_net.feature.profile.ui.installMainDispatcher
import org.matchlab.academic_net.feature.profile.ui.uninstallMainDispatcher
import org.matchlab.academic_net.feature.profile.ui.work
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "11 Добавить работу" and "11b Редактировать работу": one form with two modes. One action at a time, and an action that
 * succeeded keeps the form shut until the screen is gone, so that no tap can add, save or send twice.
 */
class WorkFormViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val repository = FakeMyProfileRepository()

    private fun adding() = viewModel(null)

    private fun editing(id: String = "w1") = viewModel(WorkId(id))

    private fun viewModel(id: WorkId?) = WorkFormViewModel(
        workId = id,
        addWork = AddWorkUseCase(repository),
        getMyWork = GetMyWorkUseCase(repository),
        saveWork = SaveWorkUseCase(repository),
        deletePortfolioItem = DeletePortfolioItemUseCase(repository),
    )

    private fun WorkFormViewModel.content() = assertIs<WorkFormUiState.Content>(uiState.value)

    private fun WorkFormViewModel.fillValid() {
        onTitleChange("Статья")
        onDescriptionChange("О графах")
    }

    // ---- adding ----

    @Test
    fun aNewWorkStartsWithAnEmptyFormAndNoRequest() {
        val content = adding().content()

        assertFalse(content.isEditing)
        assertFalse(content.isBusy)
        assertEquals("", content.draft.title)
        assertEquals(ProjectType.Scientific, content.draft.type)
        assertEquals(emptyList(), repository.calls)
    }

    @Test
    fun anInvalidWorkShowsItsErrorsAndIsNotAdded() {
        val viewModel = adding()

        viewModel.onSaveClick()

        assertEquals(setOf(WorkDraftError.TitleEmpty, WorkDraftError.DescriptionEmpty), viewModel.content().errors)
        assertFalse(viewModel.content().isBusy)
        assertEquals(emptyList(), repository.createdWorks)
    }

    @Test
    fun afterTheFirstAttemptTheErrorsFollowTheTyping() {
        val viewModel = adding()
        viewModel.onSaveClick()

        viewModel.onTitleChange("Статья")
        assertEquals(setOf(WorkDraftError.DescriptionEmpty), viewModel.content().errors)

        viewModel.onLinkChange("not a link")
        assertEquals(setOf(WorkDraftError.DescriptionEmpty, WorkDraftError.LinkInvalid), viewModel.content().errors)
    }

    @Test
    fun errorsAreNotShownBeforeTheFirstAttempt() {
        val viewModel = adding()

        viewModel.onLinkChange("not a link")

        assertEquals(emptySet(), viewModel.content().errors)
    }

    @Test
    fun aValidWorkIsAddedAndTheFormShutsForGood() {
        val viewModel = adding()
        viewModel.fillValid()
        viewModel.onTypeSelect(ProjectType.Practical)
        viewModel.onLinkChange("https://github.com/a/b")

        viewModel.onSaveClick()

        val content = viewModel.content()
        assertTrue(content.isDone)
        assertTrue(content.isBusy)
        val sent = repository.createdWorks.single()
        assertEquals("Статья", sent.title)
        assertEquals(ProjectType.Practical, sent.type)
        assertEquals("https://github.com/a/b", sent.link)
    }

    @Test
    fun aFormThatIsDoneTakesNoEditsAndNoActions() {
        val viewModel = adding()
        viewModel.fillValid()
        viewModel.onSaveClick()

        viewModel.onTitleChange("Другое")
        viewModel.onSaveClick()
        viewModel.onSendClick()
        viewModel.onDeleteConfirmed()

        assertEquals("Статья", viewModel.content().draft.title)
        assertEquals(1, repository.createdWorks.size)
        assertEquals(emptyList(), repository.submitted)
        assertEquals(emptyList(), repository.deleted)
    }

    @Test
    fun aDoubleTapAddsOnce() {
        val gate = CompletableDeferred<Unit>()
        repository.onCreateWork = { gate.await(); PortfolioChange.Saved(work(), sent = false) }
        val viewModel = adding()
        viewModel.fillValid()

        viewModel.onSaveClick()
        assertEquals(FormAction.Saving, viewModel.content().running)
        viewModel.onSaveClick()
        viewModel.onSaveClick()
        gate.complete(Unit)

        assertEquals(1, repository.createdWorks.size)
        assertTrue(viewModel.content().isDone)
    }

    @Test
    fun theFormTakesNoEditsWhileTheWorkIsBeingAdded() {
        val gate = CompletableDeferred<Unit>()
        repository.onCreateWork = { gate.await(); PortfolioChange.Saved(work(), sent = false) }
        val viewModel = adding()
        viewModel.fillValid()
        viewModel.onSaveClick()

        viewModel.onTitleChange("Другое")

        assertEquals("Статья", viewModel.content().draft.title)
        gate.complete(Unit)
    }

    @Test
    fun aFailedAddIsToldAndTheFormCanTryAgain() {
        repository.onCreateWork = { throw IllegalStateException("no network") }
        val viewModel = adding()
        viewModel.fillValid()

        viewModel.onSaveClick()

        val failed = viewModel.content()
        assertEquals(FormProblem.SaveFailed, failed.problem)
        assertNull(failed.running)
        assertFalse(failed.isDone)
        assertEquals("Статья", failed.draft.title, "the typing stays")

        repository.onCreateWork = { PortfolioChange.Saved(work(), sent = false) }
        viewModel.onSaveClick()

        assertTrue(viewModel.content().isDone)
        assertNull(viewModel.content().problem)
        assertEquals(2, repository.createdWorks.size)
    }

    @Test
    fun editingAfterAFailureHidesTheFailure() {
        repository.onCreateWork = { throw IllegalStateException("no network") }
        val viewModel = adding()
        viewModel.fillValid()
        viewModel.onSaveClick()

        viewModel.onDescriptionChange("Другое описание")

        assertNull(viewModel.content().problem)
    }

    @Test
    fun aNewRecordThatCameBackAsAConflictIsAFailureOfTheCall() {
        repository.onCreateWork = { PortfolioChange.Conflict }
        val viewModel = adding()
        viewModel.fillValid()

        viewModel.onSaveClick()

        assertEquals(FormProblem.SaveFailed, viewModel.content().problem)
        assertFalse(viewModel.content().isDone)
    }

    @Test
    fun aWorkAddedButNotSentClosesTheFormAllTheSame() {
        repository.onCreateWork = { PortfolioChange.Saved(work(), sent = false) }
        val viewModel = adding()
        viewModel.fillValid()
        viewModel.onLinkChange("https://doi.org/10.1/x")

        viewModel.onSaveClick()

        // The work is there unconfirmed: a second "add" would make a duplicate, and it is sent from its own form.
        assertTrue(viewModel.content().isDone)
        assertNull(viewModel.content().problem)
    }

    @Test
    fun theEditActionsDoNothingOnANewForm() {
        val viewModel = adding()
        viewModel.fillValid()

        viewModel.onSendClick()
        viewModel.onDeleteConfirmed()
        viewModel.onRetry()

        assertEquals(emptyList(), repository.calls)
        assertFalse(viewModel.content().isBusy)
    }

    // ---- loading a work to edit ----

    @Test
    fun aWorkToEditIsLoadedIntoTheForm() {
        repository.onGetMyWork = { work(it.value, title = "Загруженная", revision = 7) }

        val content = editing("w9").content()

        assertTrue(content.isEditing)
        assertEquals(WorkId("w9"), content.base?.id)
        assertEquals("Загруженная", content.draft.title)
        assertEquals(7, content.base?.revision)
    }

    @Test
    fun theFormIsLoadingWhileTheWorkIsAskedFor() {
        val gate = CompletableDeferred<Unit>()
        repository.onGetMyWork = { gate.await(); work() }

        val viewModel = editing()
        assertEquals(WorkFormUiState.Loading, viewModel.uiState.value)
        gate.complete(Unit)

        assertIs<WorkFormUiState.Content>(viewModel.uiState.value)
    }

    @Test
    fun aWorkThatIsGoneIsNotFound() {
        repository.onGetMyWork = { null }

        assertEquals(WorkFormUiState.NotFound, editing().uiState.value)
    }

    @Test
    fun aFailureToLoadIsAnErrorAndRetryAsksAgainOnlyFromThere() {
        val cause = IllegalStateException("no network")
        repository.onGetMyWork = { throw cause }
        val viewModel = editing()
        assertEquals(WorkFormUiState.Error(cause), viewModel.uiState.value)

        repository.onGetMyWork = { work() }
        viewModel.onRetry()
        assertIs<WorkFormUiState.Content>(viewModel.uiState.value)
        viewModel.onRetry()

        assertEquals(2, repository.calls.count { it == "getMyWork" })
    }

    @Test
    fun theActionsDoNothingBeforeTheWorkIsLoaded() {
        val gate = CompletableDeferred<Unit>()
        repository.onGetMyWork = { gate.await(); work() }
        val viewModel = editing()

        viewModel.onSaveClick()
        viewModel.onSendClick()
        viewModel.onDeleteConfirmed()
        viewModel.onTitleChange("Другое")
        gate.complete(Unit)

        assertEquals(listOf("getMyWork"), repository.calls)
        assertEquals("Статья о графах", viewModel.content().draft.title)
    }

    // ---- saving a work that is edited ----

    @Test
    fun savingAWorkThatWasNotChangedAsksNothingAndClosesTheForm() {
        val viewModel = editing()

        viewModel.onSaveClick()

        assertTrue(viewModel.content().isDone)
        assertEquals(listOf("getMyWork"), repository.calls)
    }

    @Test
    fun aChangedWorkIsSavedOnItsRevisionAndNotSentWhenItsStateIsUnknown() {
        repository.onGetMyWork = { work(it.value, revision = 7, status = null) }
        val viewModel = editing()
        viewModel.onTitleChange("Новое название")

        viewModel.onSaveClick()

        assertTrue(viewModel.content().isDone)
        val (id, draft, revision) = repository.updatedWorks.single()
        assertEquals(WorkId("w1"), id)
        assertEquals("Новое название", draft.title)
        assertEquals(7, revision)
        assertEquals(emptyList(), repository.submitted, "API-GAP G12: with an unknown state a save only saves")
    }

    @Test
    fun aChangedWorkThatWasCheckedIsSentAgainBySave() {
        repository.onGetMyWork = { work(it.value, status = VerificationStatus.Verified) }
        val viewModel = editing()
        assertTrue(viewModel.content().checksAgain)
        viewModel.onTitleChange("Новое название")

        viewModel.onSaveClick()

        assertEquals(listOf("getMyWork", "updateWork", "submit"), repository.calls)
        assertTrue(viewModel.content().isDone)
    }

    @Test
    fun anInvalidChangeShowsItsErrorsAndIsNotSaved() {
        val viewModel = editing()
        viewModel.onTitleChange("")

        viewModel.onSaveClick()

        assertEquals(setOf(WorkDraftError.TitleEmpty), viewModel.content().errors)
        assertEquals(listOf("getMyWork"), repository.calls)
    }

    @Test
    fun aDoubleTapOnSaveSavesOnce() {
        val gate = CompletableDeferred<Unit>()
        repository.onUpdateWork = { id, draft, _ -> gate.await(); EditOutcome.Done(work(id.value, title = draft.title)) }
        val viewModel = editing()
        viewModel.onTitleChange("Новое название")

        viewModel.onSaveClick()
        viewModel.onSaveClick()
        viewModel.onSendClick()
        gate.complete(Unit)

        assertEquals(1, repository.updatedWorks.size)
        assertEquals(emptyList(), repository.submitted)
    }

    @Test
    fun aFailedSaveIsToldAndTheChangesStay() {
        repository.onUpdateWork = { _, _, _ -> throw IllegalStateException("no network") }
        val viewModel = editing()
        viewModel.onTitleChange("Новое название")

        viewModel.onSaveClick()

        val content = viewModel.content()
        assertEquals(FormProblem.SaveFailed, content.problem)
        assertNull(content.running)
        assertEquals("Новое название", content.draft.title)
    }

    @Test
    fun aConflictShowsTheNewerVersionAndSaysSo() {
        var served = work("w1", title = "Версия A", revision = 3)
        repository.onGetMyWork = { served }
        val viewModel = editing()
        viewModel.onTitleChange("Мои правки")
        // Somebody changed the work meanwhile: the server has a newer revision and refuses the save.
        served = work("w1", title = "Версия B", revision = 4)
        repository.onUpdateWork = { _, _, _ -> EditOutcome.Conflict }

        viewModel.onSaveClick()

        val content = viewModel.content()
        assertEquals(FormProblem.Conflict, content.problem)
        assertEquals("Версия B", content.draft.title, "what was typed is dropped, the server's version is shown")
        assertEquals(4, content.base?.revision)
        assertNull(content.running)
        assertFalse(content.isDone)
    }

    @Test
    fun theConflictMessageStaysWhileTheUserTypesAndGoesWithTheNextAction() {
        repository.onUpdateWork = { _, _, _ -> EditOutcome.Conflict }
        val viewModel = editing()
        viewModel.onTitleChange("Мои правки")
        viewModel.onSaveClick()
        assertEquals(FormProblem.Conflict, viewModel.content().problem)

        viewModel.onTitleChange("Снова мои правки")
        assertEquals(FormProblem.Conflict, viewModel.content().problem)

        repository.onUpdateWork = { id, draft, _ -> EditOutcome.Done(work(id.value, title = draft.title)) }
        viewModel.onSaveClick()
        assertNull(viewModel.content().problem)
        assertTrue(viewModel.content().isDone)
    }

    @Test
    fun aWorkThatWasDeletedMeanwhileIsNotFoundOnSave() {
        repository.onUpdateWork = { _, _, _ -> EditOutcome.NotFound }
        val viewModel = editing()
        viewModel.onTitleChange("Новое название")

        viewModel.onSaveClick()

        assertEquals(WorkFormUiState.NotFound, viewModel.uiState.value)
    }

    // ---- sending ----

    @Test
    fun theWorkCanBeSentWhenItHasALinkAndItsStateIsUnknownOrNotVerified() {
        fun canSend(status: VerificationStatus?, link: String? = "https://doi.org/10.1/x", file: Boolean = false): Boolean {
            repository.onGetMyWork = { work(it.value, status = status, link = link, hasEvidenceFile = file) }
            return editing().content().canSend
        }

        assertTrue(canSend(null), "API-GAP G12: an unknown state counts as not sent")
        assertTrue(canSend(VerificationStatus.Unconfirmed))
        assertFalse(canSend(VerificationStatus.Pending))
        assertFalse(canSend(VerificationStatus.Verified))
        assertFalse(canSend(VerificationStatus.Rejected))
        assertFalse(canSend(null, link = null), "nothing to check")
        assertTrue(canSend(null, link = null, file = true), "a proof file is something to check")
        assertFalse(adding().content().canSend)
    }

    @Test
    fun saveSendsAgainOnlyForARecordWhoseStateIsKnownAndNotUnconfirmed() {
        fun checksAgain(status: VerificationStatus?): Boolean {
            repository.onGetMyWork = { work(it.value, status = status) }
            return editing().content().checksAgain
        }

        assertFalse(checksAgain(null))
        assertFalse(checksAgain(VerificationStatus.Unconfirmed))
        assertTrue(checksAgain(VerificationStatus.Pending))
        assertTrue(checksAgain(VerificationStatus.Verified))
        assertTrue(checksAgain(VerificationStatus.Rejected))
    }

    @Test
    fun theSendButtonSavesWhatWasChangedAndSendsTheWork() {
        val viewModel = editing("w5")
        viewModel.onTitleChange("Новое название")

        viewModel.onSendClick()

        assertEquals(listOf("getMyWork", "updateWork", "submit"), repository.calls)
        assertEquals(listOf<VerificationSubject>(VerificationSubject.Work(WorkId("w5"))), repository.submitted)
        assertTrue(viewModel.content().isDone)
    }

    @Test
    fun theSendButtonOnAWorkThatWasNotChangedOnlySends() {
        val viewModel = editing()

        viewModel.onSendClick()

        assertEquals(listOf("getMyWork", "submit"), repository.calls)
        assertTrue(viewModel.content().isDone)
    }

    @Test
    fun aSendThatFailsAfterASaveKeepsTheFormBasedOnTheSavedWork() {
        repository.onUpdateWork = { id, draft, _ -> EditOutcome.Done(work(id.value, title = draft.title, revision = 4)) }
        repository.onSubmit = { throw IllegalStateException("no network") }
        val viewModel = editing()
        viewModel.onTitleChange("Новое название")

        viewModel.onSendClick()

        val content = viewModel.content()
        assertEquals(FormProblem.SendFailed, content.problem)
        assertFalse(content.isDone)
        assertNull(content.running)
        assertEquals(4, content.base?.revision, "the form is based on what was saved")
        assertEquals("Новое название", content.base?.title)
        assertEquals("Новое название", content.draft.title)

        // "Отправить на проверку" again sends without saving twice.
        repository.onSubmit = { EditOutcome.Done(Unit) }
        viewModel.onSendClick()

        assertEquals(1, repository.updatedWorks.size)
        assertEquals(2, repository.submitted.size)
        assertTrue(viewModel.content().isDone)
    }

    @Test
    fun aSendOfAnUnchangedWorkThatFailsIsASendFailure() {
        repository.onSubmit = { throw IllegalStateException("no network") }
        val viewModel = editing()

        viewModel.onSendClick()

        assertEquals(FormProblem.SendFailed, viewModel.content().problem)
        assertNull(viewModel.content().running)
        assertFalse(viewModel.content().isDone)
    }

    @Test
    fun aSendThatFindsAConflictShowsTheNewerVersion() {
        var served = work("w1", revision = 3)
        repository.onGetMyWork = { served }
        repository.onSubmit = { EditOutcome.Conflict }
        val viewModel = editing()
        served = work("w1", title = "Версия B", revision = 4)

        viewModel.onSendClick()

        assertEquals(FormProblem.Conflict, viewModel.content().problem)
        assertEquals("Версия B", viewModel.content().draft.title)
    }

    // ---- deleting ----

    @Test
    fun aWorkIsDeletedAndTheFormShuts() {
        val viewModel = editing("w5")

        viewModel.onDeleteConfirmed()

        assertEquals(listOf<VerificationSubject>(VerificationSubject.Work(WorkId("w5"))), repository.deleted)
        assertTrue(viewModel.content().isDone)
    }

    @Test
    fun aWorkThatIsGoneAlreadyCountsAsDeleted() {
        repository.onDelete = { EditOutcome.NotFound }
        val viewModel = editing()

        viewModel.onDeleteConfirmed()

        assertTrue(viewModel.content().isDone)
    }

    @Test
    fun aDoubleTapOnDeleteDeletesOnce() {
        val gate = CompletableDeferred<Unit>()
        repository.onDelete = { gate.await(); EditOutcome.Done(Unit) }
        val viewModel = editing()

        viewModel.onDeleteConfirmed()
        assertEquals(FormAction.Deleting, viewModel.content().running)
        viewModel.onDeleteConfirmed()
        viewModel.onSaveClick()
        gate.complete(Unit)

        assertEquals(1, repository.deleted.size)
    }

    @Test
    fun aFailedDeleteIsToldAndTheWorkStays() {
        repository.onDelete = { throw IllegalStateException("no network") }
        val viewModel = editing()

        viewModel.onDeleteConfirmed()

        assertEquals(FormProblem.DeleteFailed, viewModel.content().problem)
        assertNull(viewModel.content().running)
        assertFalse(viewModel.content().isDone)
    }

    @Test
    fun aConflictOnDeleteShowsTheNewerVersion() {
        repository.onDelete = { EditOutcome.Conflict }
        val viewModel = editing()

        viewModel.onDeleteConfirmed()

        assertEquals(FormProblem.Conflict, viewModel.content().problem)
        assertFalse(viewModel.content().isDone)
        assertNull(viewModel.content().running)
    }

    // ---- the proof file ----

    private val proof = UploadFile(byteArrayOf(1), "application/pdf", "paper.pdf")

    @Test
    fun aChosenFileIsInTheDraftAndTheWorkIsSentWithIt() {
        val viewModel = adding()
        viewModel.onTitleChange("Статья")
        viewModel.onDescriptionChange("О графах")

        viewModel.onProofFileChosen(proof)
        viewModel.onSaveClick()

        assertSame(proof, repository.createdWorks.single().proofFile)
        assertTrue(repository.createdWorks.single().sendsForVerification)
    }

    @Test
    fun removingTheFileOfAWorkThatHasOneMarksItForClearing() {
        repository.onGetMyWork = { work(it.value, hasEvidenceFile = true) }
        val viewModel = editing()

        assertTrue(viewModel.content().draft.hasProofFile)
        viewModel.onProofFileRemoved()

        assertTrue(viewModel.content().draft.removeEvidenceFile)
        assertFalse(viewModel.content().draft.hasProofFile)
    }

    @Test
    fun aNewFileAfterARemovalBringsTheProofBack() {
        repository.onGetMyWork = { work(it.value, hasEvidenceFile = true) }
        val viewModel = editing()
        viewModel.onProofFileRemoved()

        viewModel.onProofFileChosen(proof)

        assertFalse(viewModel.content().draft.removeEvidenceFile)
        assertTrue(viewModel.content().draft.hasProofFile)
    }

    @Test
    fun aFileThatCannotBeReadIsAProblemAndNothingIsAttached() {
        val viewModel = adding()

        viewModel.onProofFileUnreadable()

        assertEquals(FormProblem.FileUnreadable, viewModel.content().problem)
        assertNull(viewModel.content().draft.proofFile)
    }
}
