package org.matchlab.academic_net.gateway.mapping

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectMember
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Spheres
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectMemberDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto

/**
 * The project as the app shows it, or `null` when it cannot be shown: the server sent no `id` or no `owner_user_id`.
 *
 * [owner] is the owner's public profile; without one the initiator is the owner's id with an empty name (the UI
 * writes "Без имени"). The type, the status, the sphere and the months are read from the codes the app defines
 * (`docs/api-values.md`): [ProjectType.fromCode], [ProjectStatus.fromCode], [Spheres.fromCode] and [periodOfPlanned]. A
 * value the app has no code for is `null` ("unknown") and does not hide the project. Roles come from the top-level
 * `roles`, a role without an id is dropped, `slots` below 1 is 1 (G49). The team ([Project.members]) is the server's `members` with
 * a `user_id`, in the server's order and each (person, role) once; a member's `status` is not read (G52). `Role.members`
 * stay empty here: [withTeam] fills them once the profiles of the team are read. `pending_join_count` is kept as it is,
 * never below 0 (G61). [viewerId] tells the viewer's own projects and the viewer on the team.
 */
fun ProjectDto.toProject(viewerId: UserId, owner: UserProfileDto?): Project? {
    if (id.isBlank() || ownerUserId.isBlank()) return null
    val ownerId = UserId(ownerUserId)
    return Project(
        id = ProjectId(id),
        title = details.title.trim(),
        type = ProjectType.fromCode(details.type),
        sphere = Spheres.fromCode(details.field),
        problem = details.problem.trim(),
        goal = details.goal.trim(),
        tags = details.tags.cleaned(),
        initiator = owner?.toUserSummary()?.copy(id = ownerId) ?: UserSummary(ownerId, name = ""),
        roles = roles.mapNotNull { it.toRole() },
        status = ProjectStatus.fromCode(status),
        period = periodOfPlanned(details.plannedStart, details.plannedEnd),
        viewerIsInitiator = ownerUserId == viewerId.value,
        members = members.toTeam(viewerId, roles),
        pendingJoinCount = pendingJoinCount.coerceAtLeast(0), // API-GAP G61
    )
}

/**
 * The server's members as [ProjectMember]s. A member without a `user_id` cannot be shown and is dropped (G55); the title
 * of the role is the member's `role_title`, else the title of the role the member's `role_id` names, else empty; a repeat
 * of a (`user_id`, `role_id`) pair is dropped. API-GAP G52: `status` is not read.
 */
private fun List<ProjectMemberDto>.toTeam(viewerId: UserId, roles: List<ProjectRoleDto>): List<ProjectMember> =
    asSequence()
        .map { it to it.userId.trim() }
        .filter { (_, userId) -> userId.isNotEmpty() }
        .distinctBy { (member, userId) -> userId to member.roleId }
        .map { (member, userId) ->
            ProjectMember(
                userId = UserId(userId),
                roleId = member.roleId.takeIf { it.isNotEmpty() }?.let(::RoleId),
                roleTitle = member.roleTitle.trim().ifEmpty {
                    if (member.roleId.isEmpty()) "" else roles.firstOrNull { it.id == member.roleId }?.title?.trim().orEmpty()
                },
                isViewer = userId == viewerId.value,
            )
        }
        .toList()

private fun ProjectRoleDto.toRole(): Role? =
    if (id.isBlank()) {
        null
    } else {
        Role(
            id = RoleId(id),
            title = title.trim(),
            skills = skills.cleaned(),
            slots = slots.coerceAtLeast(1), // API-GAP G49
            members = emptyList(), // filled by withTeam; API-GAP G52: ProjectMember.status has no list of values
            occupied = occupied.coerceAtLeast(0),
        )
    }

private fun List<String>.cleaned(): List<String> = map(String::trim).filter(String::isNotEmpty)
