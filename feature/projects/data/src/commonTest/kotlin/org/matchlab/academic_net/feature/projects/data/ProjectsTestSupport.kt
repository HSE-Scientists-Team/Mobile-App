package org.matchlab.academic_net.feature.projects.data

import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.gateway.ProjectCatalogApi
import org.matchlab.academic_net.gateway.ProjectSearchParams
import org.matchlab.academic_net.gateway.ProjectsApi
import org.matchlab.academic_net.gateway.dto.ProjectCreateDto
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectInputDto
import org.matchlab.academic_net.gateway.dto.ProjectListDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleInputDto
import org.matchlab.academic_net.gateway.dto.ProjectTransitionDto
import org.matchlab.academic_net.gateway.dto.ProjectMemberDto
import org.matchlab.academic_net.gateway.dto.ProjectUpdateDto
import org.matchlab.academic_net.common.model.ProjectMember
import org.matchlab.academic_net.gateway.mapping.ProjectTeamReader
import org.matchlab.academic_net.gateway.mapping.TeamMemberProfile
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.mapping.AssembledProject
import org.matchlab.academic_net.gateway.mapping.ProjectAssembler
import org.matchlab.academic_net.gateway.mapping.toProject

/** What the tests of the projects source share: the calls of the Gateway scripted one by one, and DTOs. */

/** The user of the session in these tests: the viewer of [MappingAssembler] and the `viewerId` of the source. */
internal val TestViewer = UserId("viewer-1")

internal fun projectDto(
    id: String = "p1",
    owner: String = "owner-1",
    revision: Int = 1,
    roles: List<ProjectRoleDto> = emptyList(),
    title: String = "Проект $id",
    status: String = "recruiting",
    type: String = "scientific",
    field: String = "Биология",
    members: List<ProjectMemberDto> = emptyList(),
) = ProjectDto(
    id = id,
    ownerUserId = owner,
    details = ProjectInputDto(
        title = title,
        type = type,
        field = field,
        problem = "Проблема",
        goal = "Цель",
        plannedStart = "2026-10-01",
        plannedEnd = "2027-03-31",
    ),
    status = status,
    revision = revision,
    roles = roles,
    members = members,
)

internal fun roleDto(
    id: String,
    title: String = "Роль $id",
    slots: Int = 1,
    occupied: Int = 0,
    description: String = "",
    skills: List<String> = emptyList(),
) = ProjectRoleDto(id = id, title = title, description = description, skills = skills, slots = slots, occupied = occupied)

internal fun memberDto(user: String, role: String = "", title: String = "") =
    ProjectMemberDto(userId = user, roleId = role, roleTitle = title)

/** The reads of the team of a project: records what it was asked and answers with [onRead] (no profiles by default). */
internal class ScriptedTeam : ProjectTeamReader {
    var onRead: suspend (List<ProjectMember>) -> List<TeamMemberProfile> =
        { members -> members.map { TeamMemberProfile(it, null) } }

    /** The lists of members it was asked about, in order. */
    val reads = mutableListOf<List<ProjectMember>>()

    override suspend fun read(members: List<ProjectMember>): List<TeamMemberProfile> {
        reads += members
        return onRead(members)
    }
}

/**
 * `ProjectsApi` that records what it was asked and answers with [onGet], [onAddRole], [onCreate] and [onTransition]; a
 * call nobody scripted fails the test.
 */
internal class ScriptedProjectsApi : ProjectsApi {
    var onGet: suspend (String) -> ProjectDto = { throw AssertionError("Unexpected GET of $it") }
    var onAddRole: suspend (String, ProjectRoleInputDto) -> ProjectDto =
        { id, _ -> throw AssertionError("Unexpected POST of a role to $id") }
    var onCreate: suspend (ProjectCreateDto) -> ProjectDto = { throw AssertionError("Unexpected POST of a project") }
    var onTransition: suspend (String, ProjectTransitionDto) -> ProjectDto =
        { id, _ -> throw AssertionError("Unexpected transition of $id") }
    var onUpdate: suspend (String, ProjectUpdateDto) -> ProjectDto =
        { id, _ -> throw AssertionError("Unexpected PUT of $id") }
    var onUpdateRole: suspend (String, String, ProjectRoleInputDto) -> ProjectDto =
        { id, role, _ -> throw AssertionError("Unexpected PUT of the role $role of $id") }
    var onDeleteRole: suspend (String, String, Int) -> ProjectDto =
        { id, role, _ -> throw AssertionError("Unexpected DELETE of the role $role of $id") }

    /** The descriptions that were put, with the id of the project, in order. */
    val updates = mutableListOf<Pair<String, ProjectUpdateDto>>()

    /** The roles that were put: project id, role id, body. */
    val roleUpdates = mutableListOf<Triple<String, String, ProjectRoleInputDto>>()

    /** The roles that were deleted: project id, role id, expected revision. */
    val roleDeletes = mutableListOf<Triple<String, String, Int>>()

    /** The projects that were created, in order. */
    val creates = mutableListOf<ProjectCreateDto>()

    /** The transitions that were posted, with the id of the project, in order. */
    val transitions = mutableListOf<Pair<String, ProjectTransitionDto>>()

    /** The ids that were read, in order. */
    val gets = mutableListOf<String>()

    /** The roles that were posted, with the id of the project, in order. */
    val posts = mutableListOf<Pair<String, ProjectRoleInputDto>>()

    override suspend fun get(id: String): ProjectDto {
        gets += id
        return onGet(id)
    }

    override suspend fun addRole(id: String, input: ProjectRoleInputDto): ProjectDto {
        posts += id to input
        return onAddRole(id, input)
    }

    override suspend fun create(input: ProjectCreateDto): ProjectDto {
        creates += input
        return onCreate(input)
    }

    override suspend fun transition(id: String, input: ProjectTransitionDto): ProjectDto {
        transitions += id to input
        return onTransition(id, input)
    }

    override suspend fun update(id: String, input: ProjectUpdateDto): ProjectDto {
        updates += id to input
        return onUpdate(id, input)
    }

    override suspend fun updateRole(id: String, roleId: String, input: ProjectRoleInputDto): ProjectDto {
        roleUpdates += Triple(id, roleId, input)
        return onUpdateRole(id, roleId, input)
    }

    override suspend fun deleteRole(id: String, roleId: String, expectedRevision: Int): ProjectDto {
        roleDeletes += Triple(id, roleId, expectedRevision)
        return onDeleteRole(id, roleId, expectedRevision)
    }

    var onRemoveMember: suspend (String, String) -> ProjectDto =
        { id, user -> throw AssertionError("Unexpected DELETE of the member $user of $id") }

    /** The members that were removed: project id, user id. */
    val memberRemovals = mutableListOf<Pair<String, String>>()

    override suspend fun removeMember(id: String, userId: String): ProjectDto {
        memberRemovals += id to userId
        return onRemoveMember(id, userId)
    }

    override suspend fun leave(id: String): ProjectDto = throw AssertionError("The projects never leave")
}

/** `ProjectCatalogApi` that records the reads of the catalog and answers with [onList]; the rest fails the test. */
internal class ScriptedCatalogApi : ProjectCatalogApi {
    var onList: suspend (ProjectSearchParams, PageRequest) -> ProjectListDto =
        { _, _ -> throw AssertionError("Unexpected GET of the catalog") }

    /** The reads of the catalog, in order. */
    val lists = mutableListOf<Pair<ProjectSearchParams, PageRequest>>()

    override suspend fun list(params: ProjectSearchParams, page: PageRequest): ProjectListDto {
        lists += params to page
        return onList(params, page)
    }

    override suspend fun search(params: ProjectSearchParams, page: PageRequest): ProjectListDto =
        throw AssertionError("The projects never search")

    override suspend fun get(id: String): ProjectDto = throw AssertionError("The projects read one project through ProjectsApi")
}

/**
 * The assembler of the tests: the real mapping of a project without any lookup; [owners] are the profiles the test
 * wants the owners to have. A project the mapping cannot read is left out, like the real one does.
 */
internal class MappingAssembler(
    private val viewer: UserId = TestViewer,
    val owners: MutableMap<String, UserProfileDto> = mutableMapOf(),
) : ProjectAssembler {
    /** The lists of projects it was asked to assemble. */
    val asked = mutableListOf<List<ProjectDto>>()

    /** While set, assembling fails with it. */
    var failure: Throwable? = null

    override suspend fun assemble(projects: List<ProjectDto>): List<AssembledProject> {
        asked += projects
        failure?.let { throw it }
        return projects.mapNotNull { dto ->
            val owner = owners[dto.ownerUserId]
            dto.toProject(viewer, owner)?.let { AssembledProject(it, owner) }
        }
    }
}
