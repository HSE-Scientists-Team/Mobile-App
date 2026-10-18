package org.matchlab.academic_net.feature.collaborators.domain

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary

fun collaborator(name: String) = Collaborator(
    user = UserSummary(UserId("u-$name"), name),
    university = "МГУ",
    study = Study("Магистратура"),
    field = "физика",
)

/** Records what it was asked and answers with [results] (or fails with [failure]). */
class FakeCollaboratorsRepository(var results: List<ScoredCollaborator> = emptyList()) : CollaboratorsRepository {
    var failure: Throwable? = null
    var lastQuery: CollaboratorQuery? = null

    override suspend fun search(query: CollaboratorQuery): List<ScoredCollaborator> {
        failure?.let { throw it }
        lastQuery = query
        return results
    }
}

class FakeRoleRequirementProvider(var requirement: RoleRequirement? = null) : RoleRequirementProvider {
    override suspend fun getRoleRequirement(projectId: ProjectId, roleId: RoleId) = requirement
}
