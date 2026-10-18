package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleInputDto

/**
 * The calls of the viewer's own projects (07, 07b). Every call throws `RemoteFailure` when it fails and lets a
 * `CancellationException` through, like [AccountApi].
 *
 * Only what the API defines is here. API-GAP G40, G43: no `POST /projects` (its required `type` has no list of values,
 * `planned_*` no format). API-GAP G44: no `GET /projects?mine=`. API-GAP G42: no `POST /projects/{id}/transitions`.
 */
interface ProjectsApi {
    /** `GET /api/v1/projects/{id}` → 200. [id] goes into the path escaped. */
    suspend fun get(id: String): ProjectDto

    /**
     * `POST /api/v1/projects/{id}/roles` → 200: the whole project with the new role. A stale
     * [ProjectRoleInputDto.expectedRevision] is answered with a 409.
     */
    suspend fun addRole(id: String, input: ProjectRoleInputDto): ProjectDto
}
