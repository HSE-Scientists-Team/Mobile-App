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
    status: String = "1",
    type: String = "1",
    field: String = "7",
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
)

internal fun roleDto(id: String, title: String = "Роль $id", slots: Int = 1, occupied: Int = 0) =
    ProjectRoleDto(id = id, title = title, slots = slots, occupied = occupied)

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
