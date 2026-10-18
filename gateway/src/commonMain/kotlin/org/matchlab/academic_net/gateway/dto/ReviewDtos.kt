package org.matchlab.academic_net.gateway.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The shapes of the review calls of docs/openapi.yaml (schemas UserReview, UserReviewList). The `author_role` has no
// list of values in the spec (API-GAP G13), so it stays a string that nothing parses.

/**
 * `UserReview`: what one person wrote about another after a finished project.
 *
 * @property id API-GAP G55: the schema does not require it (protobuf JSON leaves an empty string out), so it is `""`
 * when the server left it out; a review without an id cannot be opened by id, and the data layer leaves it out.
 * @property authorUserId API-GAP G55: `""` when the server left it out; the review is kept, but its author has no
 * profile to open.
 * @property authorRole the role of the author in the project, as a string the API does not define (API-GAP G13).
 * @property rating 1 to 5 stars; `0` when the server left it out.
 * @property createdAtUnix seconds since the epoch, `0` when the server left it out.
 * @property projectCompletedAtUnix when the project ended, in seconds since the epoch, `0` when the server left it out.
 */
@Serializable
data class UserReviewDto(
    val id: String = "",
    @SerialName("author_user_id") val authorUserId: String = "",
    @SerialName("author_display_name") val authorDisplayName: String = "",
    @SerialName("author_role") val authorRole: String? = null,
    @SerialName("project_id") val projectId: String = "",
    @SerialName("project_title") val projectTitle: String = "",
    @SerialName("target_user_id") val targetUserId: String = "",
    val title: String = "",
    val text: String = "",
    val rating: Int = 0,
    @SerialName("created_at_unix") @Serializable(with = FlexibleLongSerializer::class) val createdAtUnix: Long = 0,
    @SerialName("project_completed_at_unix") @Serializable(with = FlexibleLongSerializer::class)
    val projectCompletedAtUnix: Long = 0,
)

/** `UserReviewList`: a page of reviews; the API gives no total (API-GAP G16). */
@Serializable
data class UserReviewListDto(val items: List<UserReviewDto> = emptyList())
