package org.matchlab.academic_net.feature.collaborators.domain.usecase

import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorQuery
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorsRepository
import org.matchlab.academic_net.feature.collaborators.domain.RoleNotFoundException
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirement
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirementProvider
import org.matchlab.academic_net.feature.collaborators.domain.ScoredCollaborator
import org.matchlab.academic_net.utils.suspendRunCatching

/** "Искать людей в проект на роль по фильтрам": the search is the server's, this only asks. */
class SearchCollaboratorsUseCase(private val repository: CollaboratorsRepository) {
    suspend operator fun invoke(query: CollaboratorQuery): Result<List<ScoredCollaborator>> =
        suspendRunCatching { repository.search(query) }
}

/** The role's title and skills: the title of the screen and the first text of the search bar. */
class GetRoleRequirementUseCase(private val provider: RoleRequirementProvider) {
    suspend operator fun invoke(projectId: ProjectId, roleId: RoleId): Result<RoleRequirement> =
        suspendRunCatching {
            provider.getRoleRequirement(projectId, roleId) ?: throw RoleNotFoundException(projectId, roleId)
        }
}
