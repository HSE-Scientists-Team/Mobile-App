package org.matchlab.academic_net.gateway.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The shapes of the achievement calls of docs/openapi.yaml (schemas UserAchievementInput, UserAchievement,
// UserAchievementList). The `status` of an achievement has no list of values in the spec (API-GAP G12), so it stays a
// string that nothing parses.

/**
 * `UserAchievementInput`: the body of `POST /users/me/achievements` and `PUT /users/me/achievements/{id}`, and the
 * `details` of [UserAchievementDto]. `kind` and `title` are required; `kind` is one of the values of
 * [org.matchlab.academic_net.gateway.contract.AchievementKinds].
 *
 * @property url a public link: whoever opens the profile can read it (API-GAP G17).
 * @property evidenceFileId a file of the Media API that proves the achievement; the app does not upload files yet and
 * only keeps the id when it edits a record.
 * @property expectedRevision the revision the change is based on; sent with `PUT` only.
 */
@Serializable
data class UserAchievementInputDto(
    val id: String? = null,
    val kind: String = "",
    val title: String = "",
    val description: String? = null,
    val institution: String? = null,
    @SerialName("graduation_year") val graduationYear: Int? = null,
    val url: String? = null,
    @SerialName("evidence_file_id") val evidenceFileId: String? = null,
    @SerialName("expected_revision") val expectedRevision: Int? = null,
)

/**
 * `UserAchievement`: a work, a diploma or a certificate of a person with the server's state of it.
 *
 * @property status where the achievement is in the moderation, as a string the API does not define (API-GAP G12).
 * @property revision the version of the record; a change names it, and a stale one is refused with 409.
 * @property moderationComment the moderator's reason; the owner is the one who gets it.
 * @property createdAtUnix seconds since the epoch, `0` when the server left it out.
 */
@Serializable
data class UserAchievementDto(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val details: UserAchievementInputDto = UserAchievementInputDto(),
    val status: String = "",
    val revision: Int = 0,
    @SerialName("moderation_comment") val moderationComment: String? = null,
    @SerialName("created_at_unix") @Serializable(with = FlexibleLongSerializer::class) val createdAtUnix: Long = 0,
)

/** `UserAchievementList`: a page of achievements; the API gives no total (API-GAP G16). */
@Serializable
data class UserAchievementListDto(val items: List<UserAchievementDto> = emptyList())
