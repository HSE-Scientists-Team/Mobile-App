package org.matchlab.academic_net.feature.collaborators.ui

import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorsCapabilities
import org.matchlab.academic_net.feature.collaborators.domain.usecase.CountCollaboratorsUseCase
import org.matchlab.academic_net.feature.collaborators.domain.usecase.GetRoleRequirementUseCase
import org.matchlab.academic_net.feature.collaborators.domain.usecase.SearchCollaboratorsUseCase

/**
 * What the screens of the collaborators feature need from the outside; the app creates it once per session and hands
 * it to every `…Route`.
 *
 * @property getRoleRequirement the title and the skills of the role a search is started for
 * @property searchCollaborators one page of the people the server finds
 * @property countCollaborators "Показать N" of the filters sheet: how many people the server finds
 * @property capabilities what the source behind the search can do (see `docs/api-values.md`): the filters sheet hides
 *  the rest
 */
class CollaboratorsUiDependencies(
    val getRoleRequirement: GetRoleRequirementUseCase,
    val searchCollaborators: SearchCollaboratorsUseCase,
    val countCollaborators: CountCollaboratorsUseCase,
    val capabilities: CollaboratorsCapabilities,
)
