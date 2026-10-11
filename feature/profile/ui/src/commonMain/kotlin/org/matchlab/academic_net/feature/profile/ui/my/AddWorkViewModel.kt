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
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.profile.domain.ProofAttachment
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.feature.profile.domain.WorkDraftError
import org.matchlab.academic_net.feature.profile.domain.usecase.AddWorkUseCase

/**
 * @property errors the problems of the draft; empty until the first attempt to send, then kept up
 *  to date while editing
 * @property isAdded set once the work is added: the screen closes
 */
@Immutable
data class AddWorkUiState(
    val draft: WorkDraft = WorkDraft(),
    val errors: Set<WorkDraftError> = emptySet(),
    val isSubmitting: Boolean = false,
    val submitFailed: Boolean = false,
    val isAdded: Boolean = false,
)

/**
 * "11 Добавить работу в портфолио": a scientific or practical work with a link and a proof. With
 * either of them it goes to moderation, without them it is added as unconfirmed; the draft says
 * which ([WorkDraft.sendsForVerification]).
 */
class AddWorkViewModel(
    private val addWork: AddWorkUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(AddWorkUiState())

    val uiState: StateFlow<AddWorkUiState> = state.asStateFlow()

    private var showErrors = false

    fun onTypeSelect(type: ProjectType) = editDraft { it.copy(type = type) }

    fun onTitleChange(title: String) = editDraft { it.copy(title = title) }

    fun onDescriptionChange(description: String) = editDraft { it.copy(description = description) }

    fun onLinkChange(link: String) = editDraft { it.copy(link = link) }

    /** The proof was attached, or removed with `null`. */
    fun onProofChange(proof: ProofAttachment?) = editDraft { it.copy(proof = proof) }

    fun onSubmitClick() {
        val current = state.value
        if (current.isSubmitting || current.isAdded) return
        val errors = current.draft.normalized().validate()
        showErrors = true
        if (errors.isNotEmpty()) {
            state.update { it.copy(errors = errors) }
            return
        }
        state.update { it.copy(errors = emptySet(), isSubmitting = true, submitFailed = false) }
        viewModelScope.launch {
            when (val result = addWork(current.draft)) {
                is SubmitResult.Success -> state.update { it.copy(isSubmitting = false, isAdded = true) }
                is SubmitResult.Invalid -> state.update { it.copy(isSubmitting = false, errors = result.errors) }
                is SubmitResult.Failure -> state.update { it.copy(isSubmitting = false, submitFailed = true) }
            }
        }
    }

    private fun editDraft(transform: (WorkDraft) -> WorkDraft) {
        state.update { current ->
            val draft = transform(current.draft)
            current.copy(
                draft = draft,
                errors = if (showErrors) draft.normalized().validate() else current.errors,
                submitFailed = false,
            )
        }
    }
}
