package org.matchlab.academic_net.feature.projects.data

import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.gateway.ProjectsApi
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectInputDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleInputDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.mapping.AssembledProject
import org.matchlab.academic_net.gateway.mapping.ProjectAssembler
import org.matchlab.academic_net.gateway.mapping.toProject

/** What the tests of the projects source share: the calls of the Gateway scripted one by one, and DTOs. */

internal fun projectDto(
    id: String = "p1",
    owner: String = "owner-1",
    revision: Int = 1,
    roles: List<ProjectRoleDto> = emptyList(),
    title: String = "Проект $id",
) = ProjectDto(
    id = id,
    ownerUserId = owner,
    details = ProjectInputDto(title = title, field = "Biology", problem = "Проблема", goal = "Цель"),
    revision = revision,
    roles = roles,
)

internal fun roleDto(id: String, title: String = "Роль $id", slots: Int = 1, occupied: Int = 0) =
    ProjectRoleDto(id = id, title = title, slots = slots, occupied = occupied)

/**
 * `ProjectsApi` that records what it was asked and answers with [onGet] and [onAddRole]; a call nobody scripted fails
 * the test.
 */
internal class ScriptedProjectsApi : ProjectsApi {
    var onGet: suspend (String) -> ProjectDto = { throw AssertionError("Unexpected GET of $it") }
    var onAddRole: suspend (String, ProjectRoleInputDto) -> ProjectDto =
        { id, _ -> throw AssertionError("Unexpected POST of a role to $id") }

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
}

/**
 * The assembler of the tests: the real mapping of a project without any lookup; [owners] are the profiles the test
 * wants the owners to have. A project the mapping cannot read is left out, like the real one does.
 */
internal class MappingAssembler(
    private val viewer: UserId = UserId("viewer-1"),
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
