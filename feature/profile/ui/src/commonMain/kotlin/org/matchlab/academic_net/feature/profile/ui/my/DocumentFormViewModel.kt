package org.matchlab.academic_net.feature.profile.ui.my

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentDraftError
import org.matchlab.academic_net.feature.profile.domain.DocumentId
import org.matchlab.academic_net.feature.profile.domain.DocumentKind
import org.matchlab.academic_net.feature.profile.domain.EditOutcome
import org.matchlab.academic_net.feature.profile.domain.PortfolioChange
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.ProofAttachment
import org.matchlab.academic_net.feature.profile.domain.UploadFile
import org.matchlab.academic_net.feature.profile.domain.VerificationStatus
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.feature.profile.domain.toDraft
import org.matchlab.academic_net.feature.profile.domain.usecase.AddDocumentUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.DeletePortfolioItemUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetMyDocumentUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.SaveDocumentUseCase

/**
 * What the form of a document shows. A new document has the form from the start; a document that is edited first has
 * to be loaded.
 */
sealed interface DocumentFormUiState {
    /** The document to edit is being loaded. */
    data object Loading : DocumentFormUiState

    /** The document is gone (deleted elsewhere): "Запись удалена". */
    data object NotFound : DocumentFormUiState

    /** The document could not be loaded because of [cause]; "Повторить" asks again. */
    data class Error(val cause: Throwable) : DocumentFormUiState

    /**
     * The form.
     *
     * @property base the document as the server has it, which the form was opened on; `null` for a new document. Its
     *  revision is what a save names, so that a save over a newer version is refused
     * @property draft the form as the user has filled it
     * @property errors the problems of the draft; empty until the first attempt to save, then kept up to date while
     *  editing
     * @property running the action that is on its way, see [FormAction]
     * @property problem what went wrong with the last action, see [FormProblem]
     * @property isDone the document is added, saved or deleted: the screen closes. The form keeps taking nothing from
     *  then on, even though [running] is not reset
     */
    @Immutable
    data class Content(
        val base: ProfileDocument? = null,
        val draft: DocumentDraft = DocumentDraft(),
        val errors: Set<DocumentDraftError> = emptySet(),
        val running: FormAction? = null,
        val problem: FormProblem? = null,
        val isDone: Boolean = false,
    ) : DocumentFormUiState {
        /** The form edits a document that exists; otherwise it adds one. */
        val isEditing: Boolean get() = base != null

        /** An action runs or has succeeded: the form takes no other one. */
        val isBusy: Boolean get() = running != null || isDone

        /**
         * A save of this document sends it again: the server has checked it already (its state is known and is not "not
         * verified"), changing it resets the confirmation, and the design promises "После сохранения документ снова
         * пройдёт проверку". API-GAP G12: with an unknown state (the Gateway's) nothing is promised and a save only saves;
         * "Отправить на проверку" sends.
         */
        val checksAgain: Boolean
            get() {
                val status = base?.status ?: return false
                return status != VerificationStatus.Unconfirmed && draft.sendsForVerification
            }

        /**
         * "Отправить на проверку" is offered: the document has a proof to check, and it is not being checked or confirmed
         * already. API-GAP G12: an unknown state counts as "not sent", the server decides.
         */
        val canSend: Boolean
            get() {
                if (base == null || !draft.sendsForVerification) return false
                val status = base.status
                return status == null || status == VerificationStatus.Unconfirmed
            }
    }
}

/**
 * "Новый документ" and "11c Редактировать документ": one form with two modes, by [documentId]: `null` adds a document,
 * an id edits that document. It is the form of [WorkFormViewModel] for a document; read the rules there. The differences:
 *
 * - a document always goes to moderation, so it needs a proof, which is the link of the record (API-GAP G17: the API
 *   has no private field for it, so the link is **public**: everyone who opens the profile can read it, and the form
 *   says so). A document that came with a proof file on the server keeps it;
 * - the proof is the text of one field ([onProofLinkChange]), there is no picker.
 */
class DocumentFormViewModel(
    private val documentId: DocumentId?,
    private val addDocument: AddDocumentUseCase,
    private val getMyDocument: GetMyDocumentUseCase,
    private val saveDocument: SaveDocumentUseCase,
    private val deletePortfolioItem: DeletePortfolioItemUseCase,
) : ViewModel() {
    private val state = MutableStateFlow<DocumentFormUiState>(
        if (documentId == null) DocumentFormUiState.Content() else DocumentFormUiState.Loading,
    )

    val uiState: StateFlow<DocumentFormUiState> = state.asStateFlow()

    private var showErrors = false

    init {
        if (documentId != null) load(documentId)
    }

    /** "Повторить" after the document could not be loaded; it does nothing in any other state. */
    fun onRetry() {
        val id = documentId ?: return
        if (state.value !is DocumentFormUiState.Error) return
        state.value = DocumentFormUiState.Loading
        load(id)
    }

    fun onKindSelect(kind: DocumentKind) = editDraft { it.copy(kind = kind) }

    fun onTitleChange(title: String) = editDraft { it.copy(title = title) }

    fun onIssuerChange(issuer: String) = editDraft { it.copy(issuer = issuer) }

    fun onYearChange(year: String) = editDraft { it.copy(year = year) }

    /** The text of the field of the proof; a blank text is no proof at all. */
    fun onProofLinkChange(link: String) = editDraft {
        it.copy(proof = if (link.isBlank()) null else ProofAttachment(link))
    }

    /** A file was chosen as the proof; it is uploaded on save and replaces the file the document has. */
    fun onProofFileChosen(file: UploadFile) = editDraft { it.copy(proofFile = file, removeEvidenceFile = false) }

    /** "Убрать файл": nothing is attached any more (a file the document has on the server is cleared on save). */
    fun onProofFileRemoved() = editDraft { it.copy(proofFile = null, removeEvidenceFile = it.hasEvidenceFile) }

    /** The chosen file could not be read. */
    fun onProofFileUnreadable() = updateContent { current ->
        if (current.isBusy) current else current.copy(problem = FormProblem.FileUnreadable)
    }

    /**
     * The main button: adds the document, or, when a document is edited, saves it (and sends it when the server checks a
     * changed document again, see [DocumentFormUiState.Content.checksAgain]).
     */
    fun onSaveClick() {
        val current = idle() ?: return
        if (current.base == null) add(current) else save(current, send = current.checksAgain, action = FormAction.Saving)
    }

    /** "Отправить на проверку" of the form of a document that is edited: saves what was changed and sends it. */
    fun onSendClick() {
        val current = idle() ?: return
        if (current.base == null) return
        save(current, send = true, action = FormAction.Sending)
    }

    /** The user confirmed "Удалить документ?". */
    fun onDeleteConfirmed() {
        val current = idle() ?: return
        val base = current.base ?: return
        state.value = current.copy(running = FormAction.Deleting, problem = null)
        viewModelScope.launch {
            deletePortfolioItem(VerificationSubject.Document(base.id, base.kind)).fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        // A document that is gone already counts as deleted.
                        is EditOutcome.Done, EditOutcome.NotFound -> finish()
                        EditOutcome.Conflict -> showNewerVersion(base.id)
                    }
                },
                onFailure = { stop(problem = FormProblem.DeleteFailed) },
            )
        }
    }

    private fun add(current: DocumentFormUiState.Content) {
        val errors = current.draft.normalized().validate()
        showErrors = true
        if (errors.isNotEmpty()) {
            state.value = current.copy(errors = errors)
            return
        }
        state.value = current.copy(errors = emptySet(), running = FormAction.Saving, problem = null)
        viewModelScope.launch {
            when (val result = addDocument(current.draft)) {
                is SubmitResult.Success -> when (result.value) {
                    // Added. If the sending that follows did not go through, the document is there unconfirmed and can
                    // be sent from its own form; the form must not stay open for a second "add".
                    is PortfolioChange.Saved -> finish()
                    // A new record cannot be changed or gone: it is a failure of the call all the same.
                    PortfolioChange.Conflict, PortfolioChange.NotFound -> stop(problem = FormProblem.SaveFailed)
                }
                is SubmitResult.Invalid -> stop(errors = result.errors)
                is SubmitResult.Failure -> stop(problem = FormProblem.SaveFailed)
            }
        }
    }

    private fun save(current: DocumentFormUiState.Content, send: Boolean, action: FormAction) {
        val base = current.base ?: return
        val errors = current.draft.normalized().validate()
        showErrors = true
        if (errors.isNotEmpty()) {
            state.value = current.copy(errors = errors)
            return
        }
        state.value = current.copy(errors = emptySet(), running = action, problem = null)
        viewModelScope.launch {
            when (val result = saveDocument(base, current.draft, send)) {
                is SubmitResult.Success -> when (val change = result.value) {
                    is PortfolioChange.Saved -> if (send && current.draft.sendsForVerification && !change.sent) {
                        // Saved, but the server did not take the sending: the form stays, based on what was saved, so
                        // that "Отправить на проверку" sends again without saving twice.
                        state.value = DocumentFormUiState.Content(
                            base = change.value,
                            draft = change.value.toDraft(),
                            problem = FormProblem.SendFailed,
                        )
                    } else {
                        finish()
                    }
                    PortfolioChange.Conflict -> showNewerVersion(base.id)
                    PortfolioChange.NotFound -> state.value = DocumentFormUiState.NotFound
                }
                is SubmitResult.Invalid -> stop(errors = result.errors)
                is SubmitResult.Failure -> stop(
                    problem = if (action == FormAction.Sending) FormProblem.SendFailed else FormProblem.SaveFailed,
                )
            }
        }
    }

    private fun load(id: DocumentId) {
        viewModelScope.launch { state.value = loaded(id) }
    }

    /** The document as the server has it now as the form, with [problem] told; or what is wrong with getting it. */
    private suspend fun loaded(id: DocumentId, problem: FormProblem? = null): DocumentFormUiState =
        when (val result = getMyDocument(id)) {
            is LoadResult.Found -> DocumentFormUiState.Content(
                base = result.value,
                draft = result.value.toDraft(),
                problem = problem,
            )
            LoadResult.NotFound -> DocumentFormUiState.NotFound
            is LoadResult.Failure -> DocumentFormUiState.Error(result.cause)
        }

    /** 409: what the user typed is dropped, the form shows the version the server has, and says so. */
    private suspend fun showNewerVersion(id: DocumentId) {
        showErrors = false
        state.value = loaded(id, problem = FormProblem.Conflict)
    }

    /** The form, when it takes an action now: no action runs and none has succeeded. */
    private fun idle(): DocumentFormUiState.Content? =
        (state.value as? DocumentFormUiState.Content)?.takeIf { !it.isBusy }

    private fun editDraft(transform: (DocumentDraft) -> DocumentDraft) {
        updateContent { current ->
            if (current.isBusy) return@updateContent current
            val draft = transform(current.draft)
            current.copy(
                draft = draft,
                errors = if (showErrors) draft.normalized().validate() else current.errors,
                // A failure of an action is old news once the user changes the form; the message about a newer version
                // stays until the next action.
                problem = current.problem.takeIf { it == FormProblem.Conflict },
            )
        }
    }

    /** The action succeeded: the screen closes. [DocumentFormUiState.Content.running] stays set, so nothing starts again. */
    private fun finish() = updateContent { it.copy(isDone = true) }

    /** The action did not succeed: the form takes actions again, with what went wrong. */
    private fun stop(problem: FormProblem? = null, errors: Set<DocumentDraftError>? = null) =
        updateContent { it.copy(running = null, problem = problem, errors = errors ?: it.errors) }

    private fun updateContent(transform: (DocumentFormUiState.Content) -> DocumentFormUiState.Content) {
        state.update { current -> if (current is DocumentFormUiState.Content) transform(current) else current }
    }
}
