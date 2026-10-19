package org.matchlab.academic_net.common.model

import kotlin.time.Instant

/**
 * What a [ProjectJoin] is: the server writes `type` itself and the API names no values, so these are the codes the app
 * reads it by. **Not confirmed by the backend** (`docs/api-values.md`); a code the app has none for is "unknown" (`null`).
 *
 * @property code the string of digits the app takes the type from.
 */
enum class ProjectJoinType(val code: String) {
    /** "Отклик": the candidate asked for the role. */
    Application("1"),

    /** "Приглашение": the initiator asked the candidate to take the role. */
    Invitation("2"),
    ;

    companion object {
        /** Exact match once the outer spaces are cut; anything else is `null`. */
        fun fromCode(raw: String?): ProjectJoinType? {
            val code = raw?.trim() ?: return null
            return entries.firstOrNull { it.code == code }
        }
    }
}

/**
 * Where a [ProjectJoin] is. The server writes `status` itself; these are the codes the app reads it by. **Not confirmed by
 * the backend** (`docs/api-values.md`); an unknown code is `null` and the record is shown all the same.
 *
 * @property code the string of digits the app takes the status from.
 */
enum class ProjectJoinStatus(val code: String) {
    /** Waiting for the other side's decision. */
    Pending("1"),

    /** Accepted: the candidate is on the team. */
    Accepted("2"),

    /** Declined by the side that decides. */
    Declined("3"),

    /** Taken back by the side that sent it. */
    Withdrawn("4"),
    ;

    companion object {
        /** Exact match once the outer spaces are cut; anything else is `null`. */
        fun fromCode(raw: String?): ProjectJoinStatus? {
            val code = raw?.trim() ?: return null
            return entries.firstOrNull { it.code == code }
        }
    }
}

/**
 * An application of a candidate to a role, or an invitation of a candidate to it (`ProjectJoinRequest`).
 *
 * @property candidateId `null` when the server left `candidate_user_id` out (API-GAP G55).
 * @property type `null` when unknown ([ProjectJoinType.fromCode]).
 * @property status `null` when unknown ([ProjectJoinStatus.fromCode]).
 * @property message the text the sender wrote, trimmed; empty when none.
 * @property createdAt `null` when the server sent no usable `created_at_unix`.
 */
data class ProjectJoin(
    val id: ProjectJoinId,
    val projectId: ProjectId,
    val roleId: RoleId,
    val candidateId: UserId?,
    val type: ProjectJoinType?,
    val status: ProjectJoinStatus?,
    val message: String,
    val createdAt: Instant?,
)
