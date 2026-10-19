package org.matchlab.academic_net.gateway.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The shape of the complaints of docs/openapi.yaml (schema CreateComplaint): only what the app sends. The answer
// (CreatedComplaint) is not read.

/**
 * `CreateComplaint`. [subject] is the code of the reason (`docs/api-values.md`; API-GAP G68), [description] the text
 * "Что произошло". Exactly one target is set; the other is `null` and not sent. [fileIds] stays empty (G68: no Media).
 */
@Serializable
data class CreateComplaintDto(
    val subject: String,
    val description: String,
    @SerialName("target_user_id") val targetUserId: String? = null,
    @SerialName("target_project_id") val targetProjectId: String? = null,
    @SerialName("file_ids") val fileIds: List<String> = emptyList(),
)
