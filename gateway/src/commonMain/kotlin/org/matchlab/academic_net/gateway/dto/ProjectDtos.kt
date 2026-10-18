package org.matchlab.academic_net.gateway.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The shapes of the project calls of docs/openapi.yaml (schemas Project, ProjectInput, ProjectRole, ProjectList,
// ProjectRoleInput): only what the app reads or sends, the client's Json skips the rest. The strings without a list
// of values or a format (`type`, `status`, `planned_start`, `planned_end`) stay strings nothing parses
// (API-GAP G40, G41, G43).

/** `ProjectInput` as the `details` of [ProjectDto]: what the owner wrote about the project. */
@Serializable
data class ProjectInputDto(
    val title: String = "",
    /** API-GAP G40: no list of values; not read. */
    val type: String = "",
    /** API-GAP G46: the field of science as text. */
    val field: String = "",
    val problem: String = "",
    val goal: String = "",
    val tags: List<String> = emptyList(),
    /** API-GAP G43: no format; not read. */
    @SerialName("planned_start") val plannedStart: String = "",
    /** API-GAP G43: no format; not read. */
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
    /** API-GAP G41: no list of values; not read. */
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
