package org.matchlab.academic_net.feature.complaints.ui

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
import org.matchlab.academic_net.feature.complaints.domain.ComplaintDraft
import org.matchlab.academic_net.feature.complaints.domain.ComplaintDraftError
import org.matchlab.academic_net.feature.complaints.domain.ComplaintNotAllowedException
import org.matchlab.academic_net.feature.complaints.domain.ComplaintReason
import org.matchlab.academic_net.feature.complaints.domain.ComplaintSubject
import org.matchlab.academic_net.feature.complaints.domain.ComplaintTarget
import org.matchlab.academic_net.feature.complaints.domain.complaintReasonsFor
import org.matchlab.academic_net.feature.complaints.domain.GetComplaintSubjectUseCase
import org.matchlab.academic_net.feature.complaints.domain.SendComplaintUseCase

/** Why "Отправить жалобу" did not go through. */
sealed interface ComplaintFailure {
    /** 403: the e-mail is not confirmed, or the account is blocked (17f "Подтвердите почту, чтобы отправлять жалобы"). */
    data object NotAllowed : ComplaintFailure

    /** Anything else (400, 404, 409, 5xx, no network): 17g "Не удалось отправить жалобу. Попробуйте ещё раз". */
    data class Failed(val cause: Throwable) : ComplaintFailure
}

/**
 * What 17 / 17b–17g show.
 *
 * @property subject the card at the top: the person or the project; `null` while it is read and when it could not be (the
 *  form works without it)
 * @property reasons the reasons of the list, in the order of the frame: the user's six or the project's four
 * @property reason the chosen reason
 * @property description the text "Что произошло"
 * @property errors the fields that do not pass the check, after a tap on "Отправить жалобу" (17c); an error goes away
 *  when its field is edited
 * @property isSubmitting the complaint is on its way, or went through and the screen shows 17e: the button shows an arc
 *  and a second complaint cannot be sent (API-GAP G48: a repeat would be another complaint)
 * @property failure why the last send failed (17f, 17g); cleared by the next send
 * @property isSent the complaint went through: the screen shows 17e "Жалоба отправлена"
 */
@Immutable
data class ComplaintUiState(
    val target: ComplaintTarget,
    val subject: ComplaintSubject? = null,
    val reasons: List<ComplaintReason> = emptyList(),
    val reason: ComplaintReason? = null,
    val description: String = "",
    val errors: Set<ComplaintDraftError> = emptySet(),
    val isSubmitting: Boolean = false,
    val failure: ComplaintFailure? = null,
    val isSent: Boolean = false,
)

/**
 * 17 / 17b "Пожаловаться": a complaint about a person or a project, sent to the moderators.
 *
 * The card of the target is read when the view model is created; when it cannot be read (not found, a failure) there is
 * no card and the form works all the same. A reason and a description are required (17c shows what is missing); the
 * checks are the domain's ([SendComplaintUseCase] answers `Invalid` and sends nothing). [onSubmit] is guarded by
 * [ComplaintUiState.isSubmitting], which holds after a success as well: the complaint is made, the screen is 17e, and a
 * repeat would be a second one. 403 is [ComplaintFailure.NotAllowed], every other failure [ComplaintFailure.Failed].
 */
class ComplaintViewModel(
    private val target: ComplaintTarget,
    private val getComplaintSubject: GetComplaintSubjectUseCase,
    private val sendComplaint: SendComplaintUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(ComplaintUiState(target = target, reasons = complaintReasonsFor(target)))
    val uiState: StateFlow<ComplaintUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val result = getComplaintSubject(target)
            if (result is LoadResult.Found) state.update { it.copy(subject = result.value) }
        }
    }

    /** A reason of the list. */
    fun onReasonSelected(reason: ComplaintReason) {
        if (state.value.isSubmitting) return
        state.update { it.copy(reason = reason, errors = it.errors - ComplaintDraftError.ReasonMissing, failure = null) }
    }

    /** The text of the field. */
    fun onDescriptionChange(text: String) {
        if (state.value.isSubmitting) return
        state.update {
            it.copy(
                description = text,
                errors = it.errors - ComplaintDraftError.DescriptionMissing - ComplaintDraftError.DescriptionTooLong,
                failure = null,
            )
        }
    }

    /** "Отправить жалобу". */
    fun onSubmit() {
        var draft: ComplaintDraft? = null
        state.update {
            draft = if (it.isSubmitting) null else ComplaintDraft(target, it.reason, it.description)
            if (draft != null) it.copy(isSubmitting = true, failure = null) else it
        }
        val sent = draft ?: return
        viewModelScope.launch {
            when (val result = sendComplaint(sent)) {
                // The guard stays up: the complaint is made, and the screen is 17e.
                is SubmitResult.Success -> state.update { it.copy(isSent = true) }
                is SubmitResult.Invalid -> state.update { it.copy(isSubmitting = false, errors = result.errors) }
                is SubmitResult.Failure -> state.update {
                    it.copy(
                        isSubmitting = false,
                        failure = if (result.cause is ComplaintNotAllowedException) {
                            ComplaintFailure.NotAllowed
                        } else {
                            ComplaintFailure.Failed(result.cause)
                        },
                    )
                }
            }
        }
    }
}
