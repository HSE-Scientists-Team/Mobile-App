package org.matchlab.academic_net.feature.responses.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.responses.domain.JoinMessageMaxLength
import org.matchlab.academic_net.feature.responses.domain.JoinRefusal
import org.matchlab.academic_net.feature.responses.domain.JoinRefusedException
import org.matchlab.academic_net.feature.responses.domain.usecase.ApplyToRoleUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.GetRoleToApplyUseCase

/** Why "Отправить отклик" did not go through. */
sealed interface ApplyFailure {
    /** 409: the role is taken, or the viewer has applied already (04u). */
    data object Conflict : ApplyFailure

    /** 403: no rights, a blocked account or an e-mail that is not confirmed (04v). */
    data object NotAllowed : ApplyFailure

    /** 404: the project or the role is gone ("Проект или роль больше недоступны"). */
    data object Gone : ApplyFailure

    /** Anything else: the screen says why with `failureText` (no network is 04w). */
    data class Failed(val cause: Throwable) : ApplyFailure
}

/**
 * What the sheet 04s shows.
 *
 * @property isLoading the role is being read
 * @property isGone the project or the role is not there: the sheet says so instead of the form
 * @property loadFailure why reading the role failed; "Повторить" reads it again
 * @property roleTitle the role of the form, empty before it is read
 * @property skills the skills of the role
 * @property message the text of the field, at most [JoinMessageMaxLength] signs
 * @property isSubmitting the application is on its way, or went through and the sheet is closing: the button shows an arc
 *  and the sheet takes no second send
 * @property failure why the last send failed; cleared by the next one
 * @property isApplied the application went through: the host closes the sheet
 */
@Immutable
data class ApplyToRoleUiState(
    val isLoading: Boolean = true,
    val isGone: Boolean = false,
    val loadFailure: Throwable? = null,
    val roleTitle: String = "",
    val skills: List<String> = emptyList(),
    val message: String = "",
    val isSubmitting: Boolean = false,
    val failure: ApplyFailure? = null,
    val isApplied: Boolean = false,
) {
    /** The form is on the screen: the role is known. */
    val hasForm: Boolean get() = !isLoading && !isGone && loadFailure == null
}

/**
 * 04s "Откликнуться": the sheet with the role and an optional message.
 *
 * The role is read when the view model is created ([onRetry] reads it again after a failure). The field takes at most
 * [JoinMessageMaxLength] signs: what is typed or pasted beyond is cut (API-GAP G60: the limit is the app's), and the
 * counter says "N / 500". [onSubmit] sends the application; the guard [ApplyToRoleUiState.isSubmitting] holds while the
 * request runs and after it went through, while the sheet closes: a second tap cannot send a second application (G48: a
 * repeat would be another record).
 *
 * The failures are told by what the server refused: 409 [ApplyFailure.Conflict], 403 [ApplyFailure.NotAllowed], 404
 * [ApplyFailure.Gone]; the rest is [ApplyFailure.Failed].
 */
class ApplyToRoleViewModel(
    private val projectId: ProjectId,
    private val roleId: RoleId,
    private val getRoleToApply: GetRoleToApplyUseCase,
    private val applyToRole: ApplyToRoleUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(ApplyToRoleUiState())
    val uiState: StateFlow<ApplyToRoleUiState> = state.asStateFlow()

    init {
        load()
    }

    /** "Повторить" after a failed read of the role. */
    fun onRetry() {
        if (state.value.loadFailure == null) return
        state.update { it.copy(isLoading = true, loadFailure = null) }
        load()
    }

    /** The text of the field; longer than [JoinMessageMaxLength] is cut. */
    fun onMessageChange(text: String) {
        if (state.value.isSubmitting) return
        state.update { it.copy(message = text.take(JoinMessageMaxLength), failure = null) }
    }

    /** "Отправить отклик". */
    fun onSubmit() {
        var started = false
        state.update {
            started = it.hasForm && !it.isSubmitting
            if (started) it.copy(isSubmitting = true, failure = null) else it
        }
        if (!started) return
        val message = state.value.message
        viewModelScope.launch {
            when (val result = applyToRole(projectId, roleId, message)) {
                // The guard stays up: the sheet closes now and must not send a second application.
                is SubmitResult.Success -> state.update { it.copy(isApplied = true) }
                // The field is cut at the limit, so this cannot happen; the form stays as it was.
                is SubmitResult.Invalid -> state.update { it.copy(isSubmitting = false) }
                is SubmitResult.Failure -> state.update {
                    it.copy(isSubmitting = false, failure = result.cause.toApplyFailure())
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            when (val result = getRoleToApply(projectId, roleId)) {
                is LoadResult.Found -> state.update {
                    it.copy(
                        isLoading = false,
                        roleTitle = result.value.role.title,
                        skills = result.value.role.skills,
                    )
                }
                LoadResult.NotFound -> state.update { it.copy(isLoading = false, isGone = true) }
                is LoadResult.Failure -> state.update { it.copy(isLoading = false, loadFailure = result.cause) }
            }
        }
    }
}

private fun Throwable.toApplyFailure(): ApplyFailure = when ((this as? JoinRefusedException)?.refusal) {
    JoinRefusal.Conflict -> ApplyFailure.Conflict
    JoinRefusal.NotAllowed -> ApplyFailure.NotAllowed
    JoinRefusal.NotFound -> ApplyFailure.Gone
    null -> ApplyFailure.Failed(this)
}
