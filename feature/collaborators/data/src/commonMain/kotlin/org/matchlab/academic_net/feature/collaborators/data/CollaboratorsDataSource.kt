package org.matchlab.academic_net.feature.collaborators.data

import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorQuery
import org.matchlab.academic_net.feature.collaborators.domain.ScoredCollaborator

/**
 * Where the people come from. The server matches, scores and orders them; the only implementation
 * for now is [org.matchlab.academic_net.feature.collaborators.data.mock.MockCollaboratorsDataSource],
 * which plays the server. A remote one (with DTOs and their mappers) replaces it when the backend
 * appears.
 */
interface CollaboratorsDataSource {
    suspend fun search(query: CollaboratorQuery): List<ScoredCollaborator>
}
