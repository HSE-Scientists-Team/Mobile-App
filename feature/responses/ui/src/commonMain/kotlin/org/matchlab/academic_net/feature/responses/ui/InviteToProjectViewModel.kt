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
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.responses.domain.usecase.InvitationDraft
import org.matchlab.academic_net.feature.responses.domain.JoinMessageMaxLength
import org.matchlab.academic_net.feature.responses.domain.JoinRefusal
import org.matchlab.academic_net.feature.responses.domain.JoinRefusedException
import org.matchlab.academic_net.feature.responses.domain.usecase.GetInviteOptionsUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.InviteProject
import org.matchlab.academic_net.feature.responses.domain.usecase.InviteToProjectUseCase

/** A role chosen before the sheet opened (the way from 08 with a role): the sheet starts with it selected. */
data class InvitePreselection(val projectId: ProjectId, val roleId: RoleId)

/** Why "Отправить приглашение" did not go through. */
sealed interface InviteFailure {
    /** 409: the role is taken, or the person has been invited already (09o). */
    data object Conflict : InviteFailure

    /** 403: only the initiator invites, and only with a confirmed e-mail (09p). */
    data object NotAllowed : InviteFailure

    /** 404: the project or the role is gone ("Проект или роль больше недоступны"). */
    data object Gone : InviteFailure

    /** Anything else: the screen says why with `failureText`. */
    data class Failed(val cause: Throwable) : InviteFailure
}

/**
 * What the sheet 09k shows. The sheet reads it in this order: [isLoading] (09l), [loadFailure] ("Повторить"), an empty
 * [options] (09m), then the form.
 *
 * @property options the viewer's projects that take people, each with the roles that have a free place
 * @property selectedProject the chosen project; `null` before a choice
 * @property selectedRole the chosen role of [selectedProject]
 * @property message the optional text, at most [JoinMessageMaxLength] signs (cut when typed)
 * @property isSubmitting the invitation is on its way, or went through and the sheet is closing
 * @property failure why the last send failed; cleared by the next one
 * @property isInvited the invitation went through: the host closes the sheet and shows the banner 09q
 */
@Immutable
data class InviteToProjectUiState(
    val isLoading: Boolean = true,
    val loadFailure: Throwable? = null,
    val options: List<InviteProject> = emptyList(),
    val selectedProject: ProjectId? = null,
    val selectedRole: RoleId? = null,
    val message: String = "",
    val isSubmitting: Boolean = false,
    val failure: InviteFailure? = null,
    val isInvited: Boolean = false,
) {
    /** The roles to choose from: those of the chosen project. */
    val roles: List<Role> get() = options.firstOrNull { it.project.id == selectedProject }?.roles.orEmpty()

    /** "Отправить приглашение" is on: a project and a role are chosen. */
    val canSubmit: Boolean get() = selectedProject != null && selectedRole != null
}

/**
 * 09k "Пригласить в проект": the sheet over the profile of a person, to invite them to a role of one of the viewer's
 * projects.
 *
 * **Options.** [getInviteOptions] is read when the view model is created ([onRetry] reads it again). No option is 09m, with
 * the way to create a project. A [preselected] role is chosen when it is among the options; otherwise a single project
 * with a single role is chosen at once (a single project with several roles is chosen, its role is not).
 *
 * **Sending.** The button works when a project and a role are chosen. [onSubmit] is guarded by
 * [InviteToProjectUiState.isSubmitting], which holds while the request runs and after it went through (a repeat would be
 * another invitation, G48). 409 is [InviteFailure.Conflict], 403 [InviteFailure.NotAllowed], 404 [InviteFailure.Gone].
 */
class InviteToProjectViewModel(
    private val candidateId: UserId,
    private val preselected: InvitePreselection?,
    private val getInviteOptions: GetInviteOptionsUseCase,
    private val inviteToProject: InviteToProjectUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(InviteToProjectUiState())
    val uiState: StateFlow<InviteToProjectUiState> = state.asStateFlow()

    init {
        load()
    }

    /** "Повторить" after a failed read of the options. */
    fun onRetry() {
        if (state.value.loadFailure == null) return
        state.update { it.copy(isLoading = true, loadFailure = null) }
        load()
    }

    /** A project of the list; the chosen role is dropped when it is not a role of that project. */
    fun onProjectSelected(id: ProjectId) {
        if (state.value.isSubmitting) return
        state.update { current ->
            val roles = current.options.firstOrNull { it.project.id == id }?.roles.orEmpty()
            current.copy(
                selectedProject = id,
                selectedRole = current.selectedRole?.takeIf { role -> roles.any { it.id == role } },
                failure = null,
            )
        }
    }

    /** A role of the chosen project. */
    fun onRoleSelected(id: RoleId) {
        if (state.value.isSubmitting) return
        state.update { current ->
            if (current.roles.any { it.id == id }) current.copy(selectedRole = id, failure = null) else current
        }
    }

    /** The text of the field; longer than [JoinMessageMaxLength] is cut. */
    fun onMessageChange(text: String) {
        if (state.value.isSubmitting) return
        state.update { it.copy(message = text.take(JoinMessageMaxLength), failure = null) }
    }

    /** "Отправить приглашение". */
    fun onSubmit() {
        var draft: InvitationDraft? = null
        state.update {
            draft = if (it.canSubmit && !it.isSubmitting) {
                InvitationDraft(it.selectedProject, it.selectedRole, it.message)
            } else {
                null
            }
            if (draft != null) it.copy(isSubmitting = true, failure = null) else it
        }
        val sent = draft ?: return
        viewModelScope.launch {
            when (val result = inviteToProject(candidateId, sent)) {
                // The guard stays up: the sheet closes now and must not send a second invitation.
                is SubmitResult.Success -> state.update { it.copy(isInvited = true) }
                // A project and a role are chosen and the text is cut, so this cannot happen.
                is SubmitResult.Invalid -> state.update { it.copy(isSubmitting = false) }
                is SubmitResult.Failure -> state.update {
                    it.copy(isSubmitting = false, failure = result.cause.toInviteFailure())
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            when (val result = getInviteOptions()) {
                is LoadResult.Found -> state.update { it.withOptions(result.value) }
                // The use case never says "not found"; an empty list is the same sheet as no projects.
                LoadResult.NotFound -> state.update { it.withOptions(emptyList()) }
                is LoadResult.Failure -> state.update { it.copy(isLoading = false, loadFailure = result.cause) }
            }
        }
    }

    private fun InviteToProjectUiState.withOptions(options: List<InviteProject>): InviteToProjectUiState {
        val chosen = preselected?.takeIf { pre ->
            options.any { option -> option.project.id == pre.projectId && option.roles.any { it.id == pre.roleId } }
        }
        val (project, role) = when {
            chosen != null -> chosen.projectId to chosen.roleId
            options.size == 1 -> options.single().let { option ->
                option.project.id to option.roles.singleOrNull()?.id
            }
            else -> null to null
        }
        return copy(isLoading = false, options = options, selectedProject = project, selectedRole = role)
    }
}

private fun Throwable.toInviteFailure(): InviteFailure = when ((this as? JoinRefusedException)?.refusal) {
    JoinRefusal.Conflict -> InviteFailure.Conflict
    JoinRefusal.NotAllowed -> InviteFailure.NotAllowed
    JoinRefusal.NotFound -> InviteFailure.Gone
    null -> InviteFailure.Failed(this)
}
