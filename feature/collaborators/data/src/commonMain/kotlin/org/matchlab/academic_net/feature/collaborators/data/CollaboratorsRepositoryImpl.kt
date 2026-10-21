package org.matchlab.academic_net.feature.collaborators.data

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorQuery
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorsCapabilities
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorsRepository

/** [CollaboratorsRepository] over a [CollaboratorsDataSource]: all three members are the source's. */
class CollaboratorsRepositoryImpl(
    private val dataSource: CollaboratorsDataSource,
) : CollaboratorsRepository {
    override val capabilities: CollaboratorsCapabilities get() = dataSource.capabilities

    override suspend fun search(query: CollaboratorQuery, page: PageRequest): Page<Collaborator> =
        dataSource.search(query, page)

    override suspend fun count(query: CollaboratorQuery): Int = dataSource.count(query)
}
