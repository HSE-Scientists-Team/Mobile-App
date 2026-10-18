package org.matchlab.academic_net.feature.projectsearch.data

import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.feature.projectsearch.domain.FoundProject
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectSearchCapabilities

/**
 * Where the projects come from: the Gateway ([GatewayProjectSearchDataSource]). The server matches and orders them, so a
 * source only asks and translates the answer.
 */
interface ProjectSearchDataSource {
    /** What the server behind this source can filter by; the filters sheet hides the rest. */
    val capabilities: ProjectSearchCapabilities

    /** One [page] of the projects the server finds for [query], in the server's order. */
    suspend fun search(query: ProjectQuery, page: PageRequest): Page<FoundProject>
}
