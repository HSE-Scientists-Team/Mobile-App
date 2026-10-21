package org.matchlab.academic_net.gateway.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The shapes of the project calls of docs/openapi.yaml (schemas Project, ProjectInput, ProjectRole, ProjectList,
// ProjectRoleInput, ProjectCreate, ProjectInitialRole, ProjectTransitionRequest, ProjectUpdate, ProjectMember): only what the app reads or sends, the
// client's Json skips the rest. The strings the API leaves open (`type`, `field`, `status`, `planned_start`,
// `planned_end`, `action`) stay strings here: the app defines their values (docs/api-values.md) and the mapping reads
// and writes them, the DTOs do not parse anything.

/** `ProjectInput` as the `details` of [ProjectDto]: what the owner wrote about the project. */
@Serializable
data class ProjectInputDto(
    val title: String = "",
    /** The code of the project's type (docs/api-values.md); read by the mapping. */
    val type: String = "",
    /** The name of the project's sphere (docs/api-values.md); read by the mapping. */
    val field: String = "",
    val problem: String = "",
    val goal: String = "",
    val tags: List<String> = emptyList(),
    /** `YYYY-MM-DD` as the app writes it (docs/api-values.md); the mapping reads only its year and month. */
    @SerialName("planned_start") val plannedStart: String = "",
    /** `YYYY-MM-DD` as the app writes it (docs/api-values.md); the mapping reads only its year and month. */
    @SerialName("planned_end") val plannedEnd: String = "",
)

/**
 * `ProjectRole`: a position for [slots] people, [occupied] of them taken; `0` when the server left a number out.
 * [description] is kept as it came, to be sent back by a role update (the PUT replaces the role).
 */
@Serializable
data class ProjectRoleDto(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val skills: List<String> = emptyList(),
    val slots: Int = 0,
    val occupied: Int = 0,
)

/** `ProjectMember`. API-GAP G52: [status] has no table of values and is not read by the mapping. */
@Serializable
data class ProjectMemberDto(
    @SerialName("user_id") val userId: String = "",
    @SerialName("role_id") val roleId: String = "",
    @SerialName("role_title") val roleTitle: String = "",
    val status: String = "",
)

/** `Project`. [revision] is the version a change sends back as `expected_revision`. */
@Serializable
data class ProjectDto(
    val id: String = "",
    @SerialName("owner_user_id") val ownerUserId: String = "",
    val details: ProjectInputDto = ProjectInputDto(),
    /** The code of the project's status (docs/api-values.md); read by the mapping. */
    val status: String = "",
    val revision: Int = 0,
    val roles: List<ProjectRoleDto> = emptyList(),
    val members: List<ProjectMemberDto> = emptyList(),
    /** API-GAP G61: what it counts is not described. */
    @SerialName("pending_join_count") val pendingJoinCount: Int = 0,
    /** When the project was completed, in seconds since the epoch (int64, a string or a number); `0` when the server left it out. */
    @SerialName("completed_at_unix") @Serializable(with = FlexibleLongSerializer::class) val completedAtUnix: Long = 0,
)

/** `ProjectList`: one page and the size of the whole list (int64, a string or a number). */
@Serializable
data class ProjectListDto(
    val items: List<ProjectDto> = emptyList(),
    @Serializable(with = FlexibleLongSerializer::class) val total: Long = 0,
)

/** `ProjectRoleInput`, the body of `POST /projects/{id}/roles`; [title], [slots], [expectedRevision] are required. */
@Serializable
data class ProjectRoleInputDto(
    val title: String,
    val slots: Int,
    @SerialName("expected_revision") val expectedRevision: Int,
    val skills: List<String> = emptyList(),
    val description: String? = null,
)

/**
 * `ProjectCreate` (`ProjectInput` with the fields the API requires), the body of `POST /projects`. Every field but
 * [tags] is required by the API.
 *
 * @property type the code of the project's type, the `ProjectType.code` (docs/api-values.md).
 * @property field the name of the sphere, the `Sphere.id` (docs/api-values.md).
 * @property plannedStart the first day of the month the project starts in, `YYYY-MM-DD` (docs/api-values.md).
 * @property plannedEnd the last day of the month the project ends in, `YYYY-MM-DD` (docs/api-values.md).
 * @property roles the roles the project starts with.
 */
@Serializable
data class ProjectCreateDto(
    val title: String,
    val type: String,
    val field: String,
    val problem: String,
    val goal: String,
    val tags: List<String> = emptyList(),
    @SerialName("planned_start") val plannedStart: String,
    @SerialName("planned_end") val plannedEnd: String,
    val roles: List<ProjectInitialRoleDto>,
)

/** `ProjectInitialRole`, a role of [ProjectCreateDto.roles]; [title] and [slots] are required. */
@Serializable
data class ProjectInitialRoleDto(
    val title: String,
    val description: String? = null,
    val skills: List<String> = emptyList(),
    val slots: Int,
)

/**
 * `ProjectTransitionRequest`, the body of `POST /projects/{id}/transitions`. Both fields are required.
 *
 * @property action what to do with the project: one of the codes of `ProjectActions` (not confirmed by the backend).
 * @property expectedRevision the revision the caller has read; a stale one is answered with a 409.
 */
@Serializable
data class ProjectTransitionDto(
    val action: String,
    @SerialName("expected_revision") val expectedRevision: Int,
)

/**
 * `ProjectUpdate`, the body of `PUT /projects/{id}`: every field but [tags] is required by the API. API-GAP G63: `roles`
 * and `id` of `ProjectInput` are never sent (what the PUT does with them is not described); roles change by their own calls.
 * [tags] always goes (an empty list clears them: the PUT replaces the description).
 */
@Serializable
data class ProjectUpdateDto(
    val title: String,
    val type: String,
    val field: String,
    val problem: String,
    val goal: String,
    val tags: List<String> = emptyList(),
    @SerialName("planned_start") val plannedStart: String,
    @SerialName("planned_end") val plannedEnd: String,
    @SerialName("expected_revision") val expectedRevision: Int,
)
