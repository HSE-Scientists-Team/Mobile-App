package org.matchlab.academic_net.feature.collaborators.domain.usecase

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorQuery
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorsRepository
import org.matchlab.academic_net.feature.collaborators.domain.RoleNotFoundException
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirement
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirementProvider
import org.matchlab.academic_net.utils.suspendRunCatching

/** "Искать людей в проект на роль по фильтрам": one page of the server's answer. */
class SearchCollaboratorsUseCase(private val repository: CollaboratorsRepository) {
    suspend operator fun invoke(query: CollaboratorQuery, page: PageRequest): Result<Page<Collaborator>> =
        suspendRunCatching { repository.search(query, page) }
}

/** "Показать N" of "08b": how many people the filters being edited would show. */
class CountCollaboratorsUseCase(private val repository: CollaboratorsRepository) {
    suspend operator fun invoke(query: CollaboratorQuery): Result<Int> =
        suspendRunCatching { repository.count(query) }
}

/** The role's title and skills: the title of the screen and the first text of the search bar. */
class GetRoleRequirementUseCase(private val provider: RoleRequirementProvider) {
    suspend operator fun invoke(projectId: ProjectId, roleId: RoleId): Result<RoleRequirement> =
        suspendRunCatching {
            provider.getRoleRequirement(projectId, roleId) ?: throw RoleNotFoundException(projectId, roleId)
        }
}
