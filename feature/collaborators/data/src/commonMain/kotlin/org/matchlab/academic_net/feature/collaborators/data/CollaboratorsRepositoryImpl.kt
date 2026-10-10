package org.matchlab.academic_net.feature.collaborators.data

import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorQuery
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorsRepository
import org.matchlab.academic_net.feature.collaborators.domain.ScoredCollaborator

class CollaboratorsRepositoryImpl(
    private val dataSource: CollaboratorsDataSource,
) : CollaboratorsRepository {
    override suspend fun search(query: CollaboratorQuery): List<ScoredCollaborator> =
        dataSource.search(query)
}
