package org.matchlab.academic_net.gateway.mapping

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectMember
import org.matchlab.academic_net.common.model.RoleMember
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.gateway.UserDirectory
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.utils.suspendRunCatching

/** A member of a project's team with the public profile the viewer may see; `null`: none could be read. */
data class TeamMemberProfile(
    val member: ProjectMember,
    val profile: UserProfileDto?,
)

/**
 * Reads the profiles of a project's team. API-GAP G47: the API names a person by id only, so each person is a request
 * of their own. Shared by the projects and the page of a project; their data layers are tested with a fake of it.
 */
fun interface ProjectTeamReader {
    /**
     * [members] in their order, each with their profile. Each distinct person is asked for once. A person whose profile
     * is missing (400, 403, 404) or whose request failed has `null`: one person never fails the team. Never throws a
     * `RemoteFailure`; a `CancellationException` passes through. An empty [members] sends nothing.
     */
    suspend fun read(members: List<ProjectMember>): List<TeamMemberProfile>
}

/** The [ProjectTeamReader] of a session: profiles come from [users] (cache 5 min, at most 4 requests at a time). */
class DirectoryProjectTeamReader(private val users: UserDirectory) : ProjectTeamReader {
    override suspend fun read(members: List<ProjectMember>): List<TeamMemberProfile> {
        if (members.isEmpty()) return emptyList()
        val profiles: Map<String, UserProfileDto?> = coroutineScope {
            members.map { it.userId.value }.distinct()
                .map { id -> async { id to suspendRunCatching { users.profile(id) }.getOrNull() } }
                .awaitAll()
                .toMap()
        }
        return members.map { TeamMemberProfile(it, profiles[it.userId.value]) }
    }
}

/**
 * The member as a role and the team show them: the profile's name, verification, rating ([ratingOrNull], G20) and finished
 * projects (0 or more); without a profile, the member's id with an empty name and `hasProfile = false`. The id is always
 * the member's (the profile may leave `user_id` out, G55).
 */
fun TeamMemberProfile.toRoleMember(): RoleMember {
    val dto = profile ?: return RoleMember(UserSummary(member.userId, name = ""), hasProfile = false)
    return RoleMember(
        user = dto.toUserSummary().copy(id = member.userId),
        rating = dto.ratingOrNull(),
        completedProjects = dto.completedProjectCount.coerceAtLeast(0),
        hasProfile = true,
    )
}

/** [this] with each role's `members` set to the [team] members whose `roleId` is the role's; `occupied` is kept. */
fun Project.withTeam(team: List<TeamMemberProfile>): Project =
    copy(
        roles = roles.map { role ->
            role.copy(members = team.filter { it.member.roleId == role.id }.map { it.toRoleMember() })
        },
    )
