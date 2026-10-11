package org.matchlab.academic_net.feature.profile.ui.my

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentDraftError
import org.matchlab.academic_net.feature.profile.domain.DocumentKind
import org.matchlab.academic_net.feature.profile.domain.ProofAttachment
import org.matchlab.academic_net.feature.profile.domain.usecase.AddDocumentUseCase

/**
 * @property errors the problems of the draft; empty until the first attempt to send, then kept up
 *  to date while editing
 * @property isAdded set once the document is added: the screen closes
 */
@Immutable
data class AddDocumentUiState(
    val draft: DocumentDraft = DocumentDraft(),
    val errors: Set<DocumentDraftError> = emptySet(),
    val isSubmitting: Boolean = false,
    val submitFailed: Boolean = false,
    val isAdded: Boolean = false,
)

/**
 * "Новый документ": an education document or a certificate. It is always sent to moderation, so
 * the proof is required. The design has no such screen; it follows "11 Добавить работу".
 */
class AddDocumentViewModel(
    private val addDocument: AddDocumentUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(AddDocumentUiState())

    val uiState: StateFlow<AddDocumentUiState> = state.asStateFlow()

    private var showErrors = false

    fun onKindSelect(kind: DocumentKind) = editDraft { it.copy(kind = kind) }

    fun onTitleChange(title: String) = editDraft { it.copy(title = title) }

    fun onIssuerChange(issuer: String) = editDraft { it.copy(issuer = issuer) }

    fun onYearChange(year: String) = editDraft { it.copy(year = year) }

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
            when (val result = addDocument(current.draft)) {
                is SubmitResult.Success -> state.update { it.copy(isSubmitting = false, isAdded = true) }
                is SubmitResult.Invalid -> state.update { it.copy(isSubmitting = false, errors = result.errors) }
                is SubmitResult.Failure -> state.update { it.copy(isSubmitting = false, submitFailed = true) }
            }
        }
    }

    private fun editDraft(transform: (DocumentDraft) -> DocumentDraft) {
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
