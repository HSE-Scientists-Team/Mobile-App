package org.matchlab.academic_net.gateway.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The shapes of the project calls of docs/openapi.yaml (schemas Project, ProjectInput, ProjectRole, ProjectList,
// ProjectRoleInput, ProjectCreate, ProjectInitialRole, ProjectTransitionRequest): only what the app reads or sends, the
// client's Json skips the rest. The strings the API leaves open (`type`, `field`, `status`, `planned_start`,
// `planned_end`, `action`) stay strings here: the app defines their values (docs/api-values.md) and the mapping reads
// and writes them, the DTOs do not parse anything.

/** `ProjectInput` as the `details` of [ProjectDto]: what the owner wrote about the project. */
@Serializable
data class ProjectInputDto(
    val title: String = "",
    /** The code of the project's type (docs/api-values.md); read by the mapping. */
    val type: String = "",
    /** The code of the project's sphere (docs/api-values.md); read by the mapping. */
    val field: String = "",
    val problem: String = "",
    val goal: String = "",
    val tags: List<String> = emptyList(),
    /** `YYYY-MM-DD` as the app writes it (docs/api-values.md); the mapping reads only its year and month. */
    @SerialName("planned_start") val plannedStart: String = "",
    /** `YYYY-MM-DD` as the app writes it (docs/api-values.md); the mapping reads only its year and month. */
    @SerialName("planned_end") val plannedEnd: String = "",
)

/** `ProjectRole`: a position for [slots] people, [occupied] of them taken; `0` when the server left a number out. */
@Serializable
data class ProjectRoleDto(
    val id: String = "",
    val title: String = "",
    val skills: List<String> = emptyList(),
    val slots: Int = 0,
    val occupied: Int = 0,
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
 * @property field the code of the sphere, the `Sphere.id` (docs/api-values.md).
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
