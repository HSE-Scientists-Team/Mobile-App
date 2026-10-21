package org.matchlab.academic_net.feature.collaborators.data

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorQuery
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorsCapabilities
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorsRepository

/**
 * Where the people come from: the Gateway ([GatewayCollaboratorsDataSource]); see [CollaboratorsRepository]. The server
 * matches and orders the people, so a source only asks and translates the answer.
 */
interface CollaboratorsDataSource {
    /** What the server behind this source can do; the screens hide the rest. */
    val capabilities: CollaboratorsCapabilities

    /** One [page] of the people the server finds for [query], in the server's order. */
    suspend fun search(query: CollaboratorQuery, page: PageRequest): Page<Collaborator>

    /** How many people the server finds for [query], without loading them. */
    suspend fun count(query: CollaboratorQuery): Int
}
