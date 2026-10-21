package org.matchlab.academic_net.feature.projectsearch.data

import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.feature.projectsearch.domain.FoundProject
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectSearchCapabilities
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectSearchRepository

/** [ProjectSearchRepository] over a [ProjectSearchDataSource]: both members are the source's. */
class ProjectSearchRepositoryImpl(
    private val dataSource: ProjectSearchDataSource,
) : ProjectSearchRepository {
    override val capabilities: ProjectSearchCapabilities get() = dataSource.capabilities

    override suspend fun search(query: ProjectQuery, page: PageRequest): Page<FoundProject> =
        dataSource.search(query, page)
}
