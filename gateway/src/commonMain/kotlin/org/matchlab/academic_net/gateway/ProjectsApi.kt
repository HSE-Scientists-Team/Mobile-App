package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.gateway.dto.ProjectCreateDto
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleInputDto
import org.matchlab.academic_net.gateway.dto.ProjectTransitionDto
import org.matchlab.academic_net.gateway.dto.ProjectUpdateDto

/**
 * The calls of the viewer's own projects (05, 07, 07b, 07c). Every call throws `RemoteFailure` when it fails and lets a
 * `CancellationException` through, like [AccountApi].
 *
 * The strings the API leaves open (`type`, `field`, `planned_*`, `action`) carry the values the app defines
 * (`docs/api-values.md`); the callers build them, this interface only moves the DTOs. The list of the viewer's projects is
 * read from the catalog ([ProjectCatalogApi.list]).
 */
interface ProjectsApi {
    /** `GET /api/v1/projects/{id}` → 200. [id] goes into the path escaped. */
    suspend fun get(id: String): ProjectDto

    /**
     * `POST /api/v1/projects/{id}/roles` → 200: the whole project with the new role. A stale
     * [ProjectRoleInputDto.expectedRevision] is answered with a 409.
     */
    suspend fun addRole(id: String, input: ProjectRoleInputDto): ProjectDto

    /**
     * `POST /api/v1/projects` → 201: creates the project and publishes it at once; the answer is the whole project, with
     * the revision and the ids of its roles. API-GAP G48: the call is not idempotent, so a repeat after a lost answer would
     * create a second project.
     */
    suspend fun create(input: ProjectCreateDto): ProjectDto

    /**
     * `POST /api/v1/projects/{id}/transitions` → 200: the whole project after the transition. [id] goes into the path
     * escaped. A stale [ProjectTransitionDto.expectedRevision] is answered with a 409, an unknown project with a 404.
     */
    suspend fun transition(id: String, input: ProjectTransitionDto): ProjectDto

    /** `PUT /api/v1/projects/{id}` → 200: the whole project. A stale [ProjectUpdateDto.expectedRevision] is a 409. */
    suspend fun update(id: String, input: ProjectUpdateDto): ProjectDto

    /** `PUT /api/v1/projects/{id}/roles/{role_id}` → 200: the whole project. Both ids escaped; stale revision: 409. */
    suspend fun updateRole(id: String, roleId: String, input: ProjectRoleInputDto): ProjectDto

    /**
     * `DELETE /api/v1/projects/{id}/roles/{role_id}?expected_revision=…` → 200: the whole project ("удалить свободную роль
     * и отменить её отклики"). API-GAP G64: what "free" means and the code for a role that is not are not described.
     */
    suspend fun deleteRole(id: String, roleId: String, expectedRevision: Int): ProjectDto

    /** `DELETE /api/v1/projects/{id}/members/{user_id}` → 200: the whole project. API-GAP G65. */
    suspend fun removeMember(id: String, userId: String): ProjectDto

    /** `DELETE /api/v1/projects/{id}/membership` → 200: the whole project after the viewer left it. API-GAP G65. */
    suspend fun leave(id: String): ProjectDto
}
