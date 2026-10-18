package org.matchlab.academic_net.gateway.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The shapes of the profile calls of docs/openapi.yaml (schemas UserProfileInput, UserProfile). Fields are named as the
// server names them, in snake_case. A field that is only sent by the server is not null and defaults to zero: the
// schema is written in the style of protobuf JSON, which leaves zeros out. A field the client may leave out is
// nullable with a `null` default, and a missing one is not sent.
//
// The strings without a list of values in the spec (`study_level`, `project_types`) stay strings here: what they
// mean is not known (API-GAP G10, G11), so nothing in this module parses them.

/**
 * `UserProfileInput`: the body of `PUT /users/me/profile` and the `details` of [UserProfileDto]. Only
 * `display_name` is required by the API.
 *
 * API-GAP G10: [studyLevel] is a free text. API-GAP G11: [projectTypes] has no list of values; the client reads it
 * and writes it back as it came.
 */
@Serializable
data class UserProfileInputDto(
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_file_id") val avatarFileId: String? = null,
    val university: String? = null,
    @SerialName("study_level") val studyLevel: String? = null,
    @SerialName("study_year") val studyYear: Int? = null,
    val specialization: String? = null,
    val about: String? = null,
    @SerialName("open_to_collaboration") val openToCollaboration: Boolean? = null,
    @SerialName("project_types") val projectTypes: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    @SerialName("contact_email") val contactEmail: String? = null,
    val telegram: String? = null,
)

/**
 * `UserProfile`: a person's profile with the numbers the server counts. [rating] is the mean of the reviews and is
 * only meaningful when [reviewCount] is more than zero (API-GAP G20: the API does not say what it is otherwise).
 */
@Serializable
data class UserProfileDto(
    @SerialName("user_id") val userId: String = "",
    val details: UserProfileInputDto = UserProfileInputDto(),
    val verified: Boolean = false,
    val rating: Double = 0.0,
    @SerialName("review_count") val reviewCount: Int = 0,
    @SerialName("completed_project_count") val completedProjectCount: Int = 0,
    /** The number of reviews with a rating of 4 or more. */
    @SerialName("positive_review_count") val positiveReviewCount: Int = 0,
)
