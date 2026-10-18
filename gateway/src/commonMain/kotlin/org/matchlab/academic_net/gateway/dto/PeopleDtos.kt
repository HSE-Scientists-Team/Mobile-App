package org.matchlab.academic_net.gateway.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The shapes of the people calls of docs/openapi.yaml (schemas Person, PeopleList). Only the server sends them, so every
// field is not null and defaults to zero: protobuf JSON leaves zeros out, and a `null` is coerced to the default.
// API-GAP G10: `study_level` is a free text. API-GAP G18: `avatar_file_id` is not shown (no Media yet).
// API-GAP G30: there is no `review_count`, so `rating` cannot tell "no reviews" from a low rating; it is not read.

/** `Person`: one person of the search or of the recommendations. */
@Serializable
data class PersonDto(
    @SerialName("user_id") val userId: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_file_id") val avatarFileId: String = "",
    val university: String = "",
    @SerialName("study_level") val studyLevel: String = "",
    @SerialName("study_year") val studyYear: Int = 0,
    val specialization: String = "",
    val skills: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    @SerialName("open_to_collaboration") val openToCollaboration: Boolean = false,
    val verified: Boolean = false,
    val rating: Double = 0.0,
    @SerialName("completed_project_count") val completedProjectCount: Int = 0,
)

/** `PeopleList`: a page of people and the server's count of all of them (`int64`: a string or a number). */
@Serializable
data class PeopleListDto(
    val items: List<PersonDto> = emptyList(),
    @Serializable(with = FlexibleLongSerializer::class) val total: Long = 0,
)
