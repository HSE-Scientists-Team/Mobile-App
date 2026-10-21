package org.matchlab.academic_net.feature.responses.domain.usecase

import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoin
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.responses.domain.JoinMessageMaxLength
import org.matchlab.academic_net.feature.responses.domain.ProjectJoinsRepository
import org.matchlab.academic_net.utils.suspendRunCatching

/** The form of the sheet 09k: the project, the role and the message. */
data class InvitationDraft(
    val projectId: ProjectId? = null,
    val roleId: RoleId? = null,
    val message: String = "",
)

/** What is wrong with an [InvitationDraft]. */
enum class InvitationError { ProjectMissing, RoleMissing, MessageTooLong }

/**
 * "Отправить приглашение" of 09k. Validates (nothing is sent when Invalid), then `invite` with the trimmed message (blank:
 * none). A refusal of the server stays in `Failure(cause)` as a [org.matchlab.academic_net.feature.responses.domain.JoinRefusedException].
 */
class InviteToProjectUseCase(private val joins: ProjectJoinsRepository) {
    suspend operator fun invoke(candidateId: UserId, draft: InvitationDraft): SubmitResult<ProjectJoin, InvitationError> {
        val text = draft.message.trim()
        val errors = buildSet {
            if (draft.projectId == null) add(InvitationError.ProjectMissing)
            if (draft.roleId == null) add(InvitationError.RoleMissing)
            if (text.length > JoinMessageMaxLength) add(InvitationError.MessageTooLong)
        }
        val projectId = draft.projectId
        val roleId = draft.roleId
        if (errors.isNotEmpty() || projectId == null || roleId == null) return SubmitResult.Invalid(errors)
        return suspendRunCatching { joins.invite(projectId, roleId, candidateId, text.ifEmpty { null }) }.fold(
            onSuccess = { SubmitResult.Success(it) },
            onFailure = { SubmitResult.Failure(it) },
        )
    }
}
