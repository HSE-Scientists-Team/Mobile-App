package org.matchlab.academic_net.feature.collaborators.domain

import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId

/**
 * People search. The server matches, scores and orders the people. The call throws on failure
 * (e.g. no network); the use case turns that into a result.
 */
interface CollaboratorsRepository {
    /** People for [query], the best fit for its role first. */
    suspend fun search(query: CollaboratorQuery): List<ScoredCollaborator>
}

/**
 * The role a search is started for. The roles belong to the projects feature, which implements
 * this where the app is assembled, so the two features do not depend on each other.
 */
interface RoleRequirementProvider {
    /** `null` if the project or the role does not exist. */
    suspend fun getRoleRequirement(projectId: ProjectId, roleId: RoleId): RoleRequirement?
}
