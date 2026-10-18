package org.matchlab.academic_net.feature.profile.ui.my

import kotlinx.coroutines.CompletableDeferred
import org.matchlab.academic_net.feature.profile.domain.DocumentDraftError
import org.matchlab.academic_net.feature.profile.domain.DocumentId
import org.matchlab.academic_net.feature.profile.domain.DocumentKind
import org.matchlab.academic_net.feature.profile.domain.EditOutcome
import org.matchlab.academic_net.feature.profile.domain.PortfolioChange
import org.matchlab.academic_net.feature.profile.domain.ProofAttachment
import org.matchlab.academic_net.feature.profile.domain.VerificationStatus
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.feature.profile.domain.usecase.AddDocumentUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.DeletePortfolioItemUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetMyDocumentUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.SaveDocumentUseCase
import org.matchlab.academic_net.feature.profile.ui.FakeMyProfileRepository
import org.matchlab.academic_net.feature.profile.ui.document
import org.matchlab.academic_net.feature.profile.ui.installMainDispatcher
import org.matchlab.academic_net.feature.profile.ui.uninstallMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The form of a document: the same rules as the form of a work (one action at a time, a form that is done stays shut), with
 * a proof that every document needs and that is public (API-GAP G17).
 */
class DocumentFormViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val repository = FakeMyProfileRepository()

    private fun adding() = viewModel(null)

    private fun editing(id: String = "d1") = viewModel(DocumentId(id))

    private fun viewModel(id: DocumentId?) = DocumentFormViewModel(
        documentId = id,
        addDocument = AddDocumentUseCase(repository),
        getMyDocument = GetMyDocumentUseCase(repository),
        saveDocument = SaveDocumentUseCase(repository),
        deletePortfolioItem = DeletePortfolioItemUseCase(repository),
    )

    private fun DocumentFormViewModel.content() = assertIs<DocumentFormUiState.Content>(uiState.value)

    private fun DocumentFormViewModel.fillValid() {
        onTitleChange("Диплом")
        onIssuerChange("ВШЭ")
        onProofLinkChange("https://files.example.com/d.pdf")
    }

    // ---- adding ----

    @Test
    fun aNewDocumentStartsWithAnEmptyFormOfTheEducationKind() {
        val content = adding().content()

        assertFalse(content.isEditing)
        assertEquals(DocumentKind.Education, content.draft.kind)
        assertNull(content.draft.proof)
        assertEquals(emptyList(), repository.calls)
    }

    @Test
    fun aDocumentWithoutAProofIsNotAdded() {
        val viewModel = adding()
        viewModel.onTitleChange("Диплом")
        viewModel.onIssuerChange("ВШЭ")

        viewModel.onSaveClick()

        assertEquals(setOf(DocumentDraftError.ProofMissing), viewModel.content().errors)
        assertEquals(emptyList(), repository.createdDocuments)
    }

    @Test
    fun theProofIsTheTextOfOneFieldAndABlankTextIsNoProof() {
        val viewModel = adding()

        viewModel.onProofLinkChange("https://files.example.com/d.pdf")
        assertEquals(ProofAttachment("https://files.example.com/d.pdf"), viewModel.content().draft.proof)

        viewModel.onProofLinkChange("   ")
        assertNull(viewModel.content().draft.proof)
    }

    @Test
    fun aProofThatIsNotALinkIsInvalid() {
        val viewModel = adding()
        viewModel.fillValid()
        viewModel.onProofLinkChange("диплом.pdf")

        viewModel.onSaveClick()

        assertEquals(setOf(DocumentDraftError.ProofInvalid), viewModel.content().errors)
    }

    @Test
    fun aYearOutsideTheRangeIsInvalid() {
        val viewModel = adding()
        viewModel.fillValid()
        viewModel.onYearChange("1900")

        viewModel.onSaveClick()

        assertEquals(setOf(DocumentDraftError.YearInvalid), viewModel.content().errors)
        viewModel.onYearChange("2025")
        assertEquals(emptySet(), viewModel.content().errors)
    }

    @Test
    fun aValidDocumentIsAddedWithItsKindAndTheFormShutsForGood() {
        val viewModel = adding()
        viewModel.fillValid()
        viewModel.onKindSelect(DocumentKind.Certificate)
        viewModel.onYearChange("2024")

        viewModel.onSaveClick()

        assertTrue(viewModel.content().isDone)
        val sent = repository.createdDocuments.single()
        assertEquals(DocumentKind.Certificate, sent.kind)
        assertEquals("Диплом", sent.title)
        assertEquals("2024", sent.year)
        assertEquals(ProofAttachment("https://files.example.com/d.pdf"), sent.proof)

        viewModel.onSaveClick()
        viewModel.onTitleChange("Другое")
        assertEquals(1, repository.createdDocuments.size)
        assertEquals("Диплом", viewModel.content().draft.title)
    }

    @Test
    fun aDoubleTapAddsOnce() {
        val gate = CompletableDeferred<Unit>()
        repository.onCreateDocument = { gate.await(); PortfolioChange.Saved(document(), sent = true) }
        val viewModel = adding()
        viewModel.fillValid()

        viewModel.onSaveClick()
        viewModel.onSaveClick()
        viewModel.onSaveClick()
        gate.complete(Unit)

        assertEquals(1, repository.createdDocuments.size)
        assertTrue(viewModel.content().isDone)
    }

    @Test
    fun aFailedAddIsToldAndTheFormCanTryAgain() {
        repository.onCreateDocument = { throw IllegalStateException("no network") }
        val viewModel = adding()
        viewModel.fillValid()

        viewModel.onSaveClick()
        assertEquals(FormProblem.SaveFailed, viewModel.content().problem)
        assertNull(viewModel.content().running)

        repository.onCreateDocument = { PortfolioChange.Saved(document(), sent = true) }
        viewModel.onSaveClick()

        assertTrue(viewModel.content().isDone)
    }

    @Test
    fun aDocumentAddedButNotSentClosesTheFormAllTheSame() {
        repository.onCreateDocument = { PortfolioChange.Saved(document(), sent = false) }
        val viewModel = adding()
        viewModel.fillValid()

        viewModel.onSaveClick()

        assertTrue(viewModel.content().isDone)
        assertNull(viewModel.content().problem)
    }

    // ---- loading ----

    @Test
    fun aDocumentToEditIsLoadedIntoTheForm() {
        repository.onGetMyDocument = { document(it.value, kind = DocumentKind.Certificate, year = 2023, revision = 5) }

        val content = editing("d9").content()

        assertTrue(content.isEditing)
        assertEquals(DocumentKind.Certificate, content.draft.kind)
        assertEquals("2023", content.draft.year)
        assertEquals(5, content.base?.revision)
    }

    @Test
    fun aDocumentThatIsGoneIsNotFoundAndAFailureIsAnErrorThatRetryClears() {
        repository.onGetMyDocument = { null }
        assertEquals(DocumentFormUiState.NotFound, editing().uiState.value)

        val cause = IllegalStateException("no network")
        repository.onGetMyDocument = { throw cause }
        val viewModel = editing()
        assertEquals(DocumentFormUiState.Error(cause), viewModel.uiState.value)

        repository.onGetMyDocument = { document() }
        viewModel.onRetry()
        assertIs<DocumentFormUiState.Content>(viewModel.uiState.value)
    }

    // ---- saving ----

    @Test
    fun savingADocumentThatWasNotChangedAsksNothingAndClosesTheForm() {
        val viewModel = editing()

        viewModel.onSaveClick()

        assertTrue(viewModel.content().isDone)
        assertEquals(listOf("getMyDocument"), repository.calls)
    }

    @Test
    fun aChangedDocumentIsSavedOnItsRevisionAndNotSentWhenItsStateIsUnknown() {
        repository.onGetMyDocument = { document(it.value, revision = 6) }
        val viewModel = editing()
        viewModel.onIssuerChange("Другой вуз")

        viewModel.onSaveClick()

        assertTrue(viewModel.content().isDone)
        assertEquals(6, repository.updatedDocuments.single().third)
        assertEquals(emptyList(), repository.submitted)
    }

    @Test
    fun aChangedDocumentThatWasCheckedIsSentAgainBySave() {
        repository.onGetMyDocument = { document(it.value, status = VerificationStatus.Verified) }
        val viewModel = editing()
        assertTrue(viewModel.content().checksAgain)
        viewModel.onIssuerChange("Другой вуз")

        viewModel.onSaveClick()

        assertEquals(listOf("getMyDocument", "updateDocument", "submit"), repository.calls)
    }

    @Test
    fun aDocumentThatLostItsProofCannotBeSaved() {
        val viewModel = editing()
        viewModel.onProofLinkChange("")

        viewModel.onSaveClick()

        assertEquals(setOf(DocumentDraftError.ProofMissing), viewModel.content().errors)
        assertEquals(emptyList(), repository.updatedDocuments)
    }

    @Test
    fun aDocumentWithAProofFileOfTheServerKeepsItWithoutALink() {
        repository.onGetMyDocument = { document(it.value, proof = null, hasEvidenceFile = true) }
        val viewModel = editing()
        viewModel.onIssuerChange("Другой вуз")

        viewModel.onSaveClick()

        assertEquals(emptySet(), viewModel.content().errors)
        assertTrue(viewModel.content().isDone)
        assertTrue(repository.updatedDocuments.single().second.hasEvidenceFile)
    }

    @Test
    fun aConflictShowsTheNewerVersionAndAGoneDocumentIsNotFound() {
        var served = document("d1", title = "Версия A", revision = 2)
        repository.onGetMyDocument = { served }
        val viewModel = editing()
        viewModel.onTitleChange("Мои правки")
        served = document("d1", title = "Версия B", revision = 3)
        repository.onUpdateDocument = { _, _, _ -> EditOutcome.Conflict }

        viewModel.onSaveClick()

        assertEquals(FormProblem.Conflict, viewModel.content().problem)
        assertEquals("Версия B", viewModel.content().draft.title)

        repository.onUpdateDocument = { _, _, _ -> EditOutcome.NotFound }
        viewModel.onTitleChange("Опять мои правки")
        viewModel.onSaveClick()

        assertEquals(DocumentFormUiState.NotFound, viewModel.uiState.value)
    }

    // ---- sending ----

    @Test
    fun theDocumentCanBeSentWhenItHasAProofAndItsStateIsUnknownOrNotVerified() {
        fun canSend(status: VerificationStatus?): Boolean {
            repository.onGetMyDocument = { document(it.value, status = status) }
            return editing().content().canSend
        }

        assertTrue(canSend(null))
        assertTrue(canSend(VerificationStatus.Unconfirmed))
        assertFalse(canSend(VerificationStatus.Pending))
        assertFalse(canSend(VerificationStatus.Verified))
        assertFalse(canSend(VerificationStatus.Rejected))
        assertFalse(adding().content().canSend)
    }

    @Test
    fun theSendButtonSendsTheDocumentWithItsKind() {
        repository.onGetMyDocument = { document(it.value, kind = DocumentKind.Certificate) }
        val viewModel = editing("d7")

        viewModel.onSendClick()

        assertEquals(
            listOf<VerificationSubject>(VerificationSubject.Document(DocumentId("d7"), DocumentKind.Certificate)),
            repository.submitted,
        )
        assertTrue(viewModel.content().isDone)
    }

    @Test
    fun aSendThatFailsAfterASaveKeepsTheFormBasedOnTheSavedDocument() {
        repository.onUpdateDocument = { id, draft, _ -> EditOutcome.Done(document(id.value, title = draft.title, revision = 3)) }
        repository.onSubmit = { throw IllegalStateException("no network") }
        val viewModel = editing()
        viewModel.onTitleChange("Новое название")

        viewModel.onSendClick()

        assertEquals(FormProblem.SendFailed, viewModel.content().problem)
        assertEquals(3, viewModel.content().base?.revision)
        assertFalse(viewModel.content().isDone)

        repository.onSubmit = { EditOutcome.Done(Unit) }
        viewModel.onSendClick()

        assertEquals(1, repository.updatedDocuments.size)
        assertTrue(viewModel.content().isDone)
    }

    // ---- deleting ----

    @Test
    fun aDocumentIsDeletedWithItsKindAndTheFormShuts() {
        repository.onGetMyDocument = { document(it.value, kind = DocumentKind.Certificate) }
        val viewModel = editing("d7")

        viewModel.onDeleteConfirmed()

        assertEquals(
            listOf<VerificationSubject>(VerificationSubject.Document(DocumentId("d7"), DocumentKind.Certificate)),
            repository.deleted,
        )
        assertTrue(viewModel.content().isDone)
    }

    @Test
    fun aDeleteThatFailsIsToldAndOneThatFindsAConflictShowsTheNewerVersion() {
        repository.onDelete = { throw IllegalStateException("no network") }
        val viewModel = editing()
        viewModel.onDeleteConfirmed()
        assertEquals(FormProblem.DeleteFailed, viewModel.content().problem)
        assertFalse(viewModel.content().isDone)

        repository.onDelete = { EditOutcome.Conflict }
        viewModel.onDeleteConfirmed()
        assertEquals(FormProblem.Conflict, viewModel.content().problem)
    }

    @Test
    fun aDoubleTapOnDeleteDeletesOnce() {
        val gate = CompletableDeferred<Unit>()
        repository.onDelete = { gate.await(); EditOutcome.Done(Unit) }
        val viewModel = editing()

        viewModel.onDeleteConfirmed()
        viewModel.onDeleteConfirmed()
        gate.complete(Unit)

        assertEquals(1, repository.deleted.size)
        assertTrue(viewModel.content().isDone)
    }
}
