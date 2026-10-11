package org.matchlab.academic_net.feature.projectsearch.data

import org.matchlab.academic_net.feature.projectsearch.domain.FoundProject
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectSearchRepository

class ProjectSearchRepositoryImpl(
    private val dataSource: ProjectSearchDataSource,
) : ProjectSearchRepository {
    override suspend fun search(query: ProjectQuery): List<FoundProject> = dataSource.search(query)
}
