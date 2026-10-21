package org.matchlab.academic_net.gateway.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The shapes of the applications and invitations of docs/openapi.yaml (schemas ProjectJoinRequest, ProjectJoinList,
// ProjectJoinInput, ProjectJoinDecisionBody): only what the app reads or sends, the client's Json skips the rest.

/** `ProjectJoinRequest`. [type] and [status] are the server's strings; the mapping reads them by the app's codes. */
@Serializable
data class ProjectJoinDto(
    val id: String = "",
    @SerialName("project_id") val projectId: String = "",
    @SerialName("role_id") val roleId: String = "",
    @SerialName("candidate_user_id") val candidateUserId: String = "",
    val type: String = "",
    val status: String = "",
    val message: String = "",
    @SerialName("created_at_unix") @Serializable(with = FlexibleLongSerializer::class) val createdAtUnix: Long = 0,
)

/** `ProjectJoinList`: one page and the size of the whole list (int64, a string or a number). */
@Serializable
data class ProjectJoinListDto(
    val items: List<ProjectJoinDto> = emptyList(),
    @Serializable(with = FlexibleLongSerializer::class) val total: Long = 0,
)

/**
 * `ProjectJoinInput`, the body of applications and invitations. API-GAP G60: the API names no required field; a `null`
 * field is not sent.
 */
@Serializable
data class ProjectJoinInputDto(
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("role_id") val roleId: String? = null,
    @SerialName("candidate_user_id") val candidateUserId: String? = null,
    val message: String? = null,
)

/** `ProjectJoinDecisionBody`: [accept] is required. */
@Serializable
data class ProjectJoinDecisionDto(val accept: Boolean)
