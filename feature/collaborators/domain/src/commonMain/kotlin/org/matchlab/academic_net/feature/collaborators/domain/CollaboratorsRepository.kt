package org.matchlab.academic_net.feature.collaborators.domain

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest

/**
 * The people search. The server matches and orders the people; the calls throw on failure (no network, a server error)
 * and the use cases turn that into a result.
 */
interface CollaboratorsRepository {
    /** What the server behind the search can do; the screens hide the rest (see [CollaboratorsCapabilities]). */
    val capabilities: CollaboratorsCapabilities

    /**
     * One [page] of the people the server finds for [query], in the server's order. [Page.total] is the server's count of
     * all of them ("Найдено N"); a record the app cannot read is not in [Page.items] but is counted in [Page.received],
     * so the next page starts after it.
     */
    suspend fun search(query: CollaboratorQuery, page: PageRequest): Page<Collaborator>

    /** How many people the server finds for [query], without loading them: "Показать N" of the filters sheet. */
    suspend fun count(query: CollaboratorQuery): Int
}

/**
 * The role a search is started for. The roles belong to the projects feature, which implements this where the app is
 * assembled, so the two features do not depend on each other.
 */
interface RoleRequirementProvider {
    /** `null` if the project or the role does not exist. */
    suspend fun getRoleRequirement(projectId: ProjectId, roleId: RoleId): RoleRequirement?
}
