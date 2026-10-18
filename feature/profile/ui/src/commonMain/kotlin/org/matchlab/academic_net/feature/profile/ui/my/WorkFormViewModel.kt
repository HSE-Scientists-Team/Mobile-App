package org.matchlab.academic_net.feature.profile.ui.my

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.profile.domain.EditOutcome
import org.matchlab.academic_net.feature.profile.domain.PortfolioChange
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.VerificationStatus
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.feature.profile.domain.WorkDraftError
import org.matchlab.academic_net.feature.profile.domain.WorkId
import org.matchlab.academic_net.feature.profile.domain.toDraft
import org.matchlab.academic_net.feature.profile.domain.usecase.AddWorkUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.DeletePortfolioItemUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetMyWorkUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.SaveWorkUseCase

/**
 * What the form of a work shows. A new work has the form from the start; a work that is edited first has to be loaded.
 */
sealed interface WorkFormUiState {
    /** The work to edit is being loaded. */
    data object Loading : WorkFormUiState

    /** The work is gone (deleted elsewhere): "Запись удалена". */
    data object NotFound : WorkFormUiState

    /** The work could not be loaded because of [cause]; "Повторить" asks again. */
    data class Error(val cause: Throwable) : WorkFormUiState

    /**
     * The form.
     *
     * @property base the work as the server has it, which the form was opened on; `null` for a new work. Its revision
     *  is what a save names, so that a save over a newer version is refused
     * @property draft the form as the user has filled it
     * @property errors the problems of the draft; empty until the first attempt to save, then kept up to date while
     *  editing
     * @property running the action that is on its way, see [FormAction]
     * @property problem what went wrong with the last action, see [FormProblem]
     * @property isDone the work is added, saved or deleted: the screen closes. The form keeps taking nothing
     *  from then on, even though [running] is not reset
     */
    @Immutable
    data class Content(
        val base: PortfolioWork? = null,
        val draft: WorkDraft = WorkDraft(),
        val errors: Set<WorkDraftError> = emptySet(),
        val running: FormAction? = null,
        val problem: FormProblem? = null,
        val isDone: Boolean = false,
    ) : WorkFormUiState {
        /** The form edits a work that exists; otherwise it adds one. */
        val isEditing: Boolean get() = base != null

        /** An action runs or has succeeded: the form takes no other one. */
        val isBusy: Boolean get() = running != null || isDone

        /**
         * A save of this record sends it again: the server has checked the record already (its state is known and is
         * not "not verified"), changing it resets the confirmation, and the design promises "После сохранения работа
         * снова пройдёт проверку". API-GAP G12: with an unknown state (the Gateway's) nothing is promised and a save only
         * saves; "Отправить на проверку" sends.
         */
        val checksAgain: Boolean
            get() {
                val status = base?.status ?: return false
                return status != VerificationStatus.Unconfirmed && draft.sendsForVerification
            }

        /**
         * "Отправить на проверку" is offered: the work has a link or a file to check, and it is not being checked or
         * confirmed already. API-GAP G12: an unknown state counts as "not sent", the server decides.
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
 * "11 Добавить работу в портфолио" and "11b Редактировать работу": one form with two modes, by [workId]: `null` adds a
 * work, an id edits that work.
 *
 * **Adding** is the use case of adding: with a link or a proof file the work is also sent for verification (the
 * button says "Отправить на проверку", otherwise "Добавить без подтверждения"). If the sending fails the work is added
 * all the same, and the form closes: a work that was added is never added twice, and the user can send it from its
 * own form (API-GAP G12: its state is not known, so the form offers "Отправить на проверку" always).
 *
 * **Editing** loads the work, keeps it as the base of the form and offers: [onSaveClick] ("Сохранить"),
 * [onSendClick] ("Отправить на проверку"), [onDeleteConfirmed] ("Удалить работу"). A save over a work that changed
 * meanwhile (HTTP 409) loads the work again and shows the newer version with a message ([FormProblem.Conflict]); a work
 * that is gone is [WorkFormUiState.NotFound].
 *
 * **One action at a time** ([WorkFormUiState.Content.isBusy]): nothing is started while an action runs or after it
 * succeeded, so a double tap cannot add a work twice or send it twice.
 */
class WorkFormViewModel(
    private val workId: WorkId?,
    private val addWork: AddWorkUseCase,
    private val getMyWork: GetMyWorkUseCase,
    private val saveWork: SaveWorkUseCase,
    private val deletePortfolioItem: DeletePortfolioItemUseCase,
) : ViewModel() {
    private val state = MutableStateFlow<WorkFormUiState>(
        if (workId == null) WorkFormUiState.Content() else WorkFormUiState.Loading,
    )

    val uiState: StateFlow<WorkFormUiState> = state.asStateFlow()

    private var showErrors = false

    init {
        if (workId != null) load(workId)
    }

    /** "Повторить" after the work could not be loaded; it does nothing in any other state. */
    fun onRetry() {
        val id = workId ?: return
        if (state.value !is WorkFormUiState.Error) return
        state.value = WorkFormUiState.Loading
        load(id)
    }

    fun onTypeSelect(type: ProjectType) = editDraft { it.copy(type = type) }

    fun onTitleChange(title: String) = editDraft { it.copy(title = title) }

    fun onDescriptionChange(description: String) = editDraft { it.copy(description = description) }

    fun onLinkChange(link: String) = editDraft { it.copy(link = link) }

    /**
     * The main button: adds the work, or, when a work is edited, saves it (and sends it when the server checks a changed
     * record again, see [WorkFormUiState.Content.checksAgain]).
     */
    fun onSaveClick() {
        val current = idle() ?: return
        if (current.base == null) add(current) else save(current, send = current.checksAgain, action = FormAction.Saving)
    }

    /** "Отправить на проверку" of the form of a work that is edited: saves what was changed and sends the work. */
    fun onSendClick() {
        val current = idle() ?: return
        if (current.base == null) return
        save(current, send = true, action = FormAction.Sending)
    }

    /** The user confirmed "Удалить работу?". */
    fun onDeleteConfirmed() {
        val current = idle() ?: return
        val base = current.base ?: return
        state.value = current.copy(running = FormAction.Deleting, problem = null)
        viewModelScope.launch {
            deletePortfolioItem(VerificationSubject.Work(base.id)).fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        // A work that is gone already counts as deleted.
                        is EditOutcome.Done, EditOutcome.NotFound -> finish()
                        EditOutcome.Conflict -> showNewerVersion(base.id)
                    }
                },
                onFailure = { stop(problem = FormProblem.DeleteFailed) },
            )
        }
    }

    private fun add(current: WorkFormUiState.Content) {
        val errors = current.draft.normalized().validate()
        showErrors = true
        if (errors.isNotEmpty()) {
            state.value = current.copy(errors = errors)
            return
        }
        state.value = current.copy(errors = emptySet(), running = FormAction.Saving, problem = null)
        viewModelScope.launch {
            when (val result = addWork(current.draft)) {
                is SubmitResult.Success -> when (result.value) {
                    // Added. If the sending that follows did not go through, the work is there unconfirmed and can be
                    // sent from its own form; the form must not stay open for a second "add".
                    is PortfolioChange.Saved -> finish()
                    // A new record cannot be changed or gone: it is a failure of the call all the same.
                    PortfolioChange.Conflict, PortfolioChange.NotFound -> stop(problem = FormProblem.SaveFailed)
                }
                is SubmitResult.Invalid -> stop(errors = result.errors)
                is SubmitResult.Failure -> stop(problem = FormProblem.SaveFailed)
            }
        }
    }

    private fun save(current: WorkFormUiState.Content, send: Boolean, action: FormAction) {
        val base = current.base ?: return
        val errors = current.draft.normalized().validate()
        showErrors = true
        if (errors.isNotEmpty()) {
            state.value = current.copy(errors = errors)
            return
        }
        state.value = current.copy(errors = emptySet(), running = action, problem = null)
        viewModelScope.launch {
            when (val result = saveWork(base, current.draft, send)) {
                is SubmitResult.Success -> when (val change = result.value) {
                    is PortfolioChange.Saved -> if (send && current.draft.sendsForVerification && !change.sent) {
                        // Saved, but the server did not take the sending: the form stays, based on what was saved, so
                        // that "Отправить на проверку" sends again without saving twice.
                        state.value = WorkFormUiState.Content(
                            base = change.value,
                            draft = change.value.toDraft(),
                            problem = FormProblem.SendFailed,
                        )
                    } else {
                        finish()
                    }
                    PortfolioChange.Conflict -> showNewerVersion(base.id)
                    PortfolioChange.NotFound -> state.value = WorkFormUiState.NotFound
                }
                is SubmitResult.Invalid -> stop(errors = result.errors)
                is SubmitResult.Failure -> stop(
                    problem = if (action == FormAction.Sending) FormProblem.SendFailed else FormProblem.SaveFailed,
                )
            }
        }
    }

    private fun load(id: WorkId) {
        viewModelScope.launch { state.value = loaded(id) }
    }

    /** The work as the server has it now as the form, with [problem] told; or what is wrong with getting it. */
    private suspend fun loaded(id: WorkId, problem: FormProblem? = null): WorkFormUiState =
        when (val result = getMyWork(id)) {
            is LoadResult.Found -> WorkFormUiState.Content(
                base = result.value,
                draft = result.value.toDraft(),
                problem = problem,
            )
            LoadResult.NotFound -> WorkFormUiState.NotFound
            is LoadResult.Failure -> WorkFormUiState.Error(result.cause)
        }

    /** 409: what the user typed is dropped, the form shows the version the server has, and says so. */
    private suspend fun showNewerVersion(id: WorkId) {
        showErrors = false
        state.value = loaded(id, problem = FormProblem.Conflict)
    }

    /** The form, when it takes an action now: no action runs and none has succeeded. */
    private fun idle(): WorkFormUiState.Content? =
        (state.value as? WorkFormUiState.Content)?.takeIf { !it.isBusy }

    private fun editDraft(transform: (WorkDraft) -> WorkDraft) {
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

    /** The action succeeded: the screen closes. [WorkFormUiState.Content.running] stays set, so nothing starts again. */
    private fun finish() = updateContent { it.copy(isDone = true) }

    /** The action did not succeed: the form takes actions again, with what went wrong. */
    private fun stop(problem: FormProblem? = null, errors: Set<WorkDraftError>? = null) =
        updateContent { it.copy(running = null, problem = problem, errors = errors ?: it.errors) }

    private fun updateContent(transform: (WorkFormUiState.Content) -> WorkFormUiState.Content) {
        state.update { current -> if (current is WorkFormUiState.Content) transform(current) else current }
    }
}
