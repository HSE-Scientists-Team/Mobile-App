package org.matchlab.academic_net.feature.collaborators.ui

import org.matchlab.academic_net.feature.collaborators.domain.usecase.GetRoleRequirementUseCase
import org.matchlab.academic_net.feature.collaborators.domain.usecase.SearchCollaboratorsUseCase

/**
 * What the screens of the collaborators feature need from the outside; the app creates it once
 * and hands it to every `…Route`.
 */
class CollaboratorsUiDependencies(
    val getRoleRequirement: GetRoleRequirementUseCase,
    val searchCollaborators: SearchCollaboratorsUseCase,
)
