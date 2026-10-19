package org.matchlab.academic_net.feature.responses.domain.usecase

import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoin
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.responses.domain.JoinMessageMaxLength
import org.matchlab.academic_net.feature.responses.domain.MyJoinsRepository
import org.matchlab.academic_net.utils.suspendRunCatching

/** What is wrong with the message of an application or an invitation. */
enum class JoinMessageError { TooLong }

/**
 * "Отправить отклик" of 04s. Trims [message]; longer than [JoinMessageMaxLength] is Invalid and nothing is sent; blank is
 * sent without a message. A refusal of the server stays in `Failure(cause)` as a [org.matchlab.academic_net.feature.responses.domain.JoinRefusedException].
 */
class ApplyToRoleUseCase(private val joins: MyJoinsRepository) {
    suspend operator fun invoke(
        projectId: ProjectId,
        roleId: RoleId,
        message: String,
    ): SubmitResult<ProjectJoin, JoinMessageError> {
        val text = message.trim()
        if (text.length > JoinMessageMaxLength) return SubmitResult.Invalid(setOf(JoinMessageError.TooLong))
        return suspendRunCatching { joins.apply(projectId, roleId, text.ifEmpty { null }) }.fold(
            onSuccess = { SubmitResult.Success(it) },
            onFailure = { SubmitResult.Failure(it) },
        )
    }
}
