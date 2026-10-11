package org.matchlab.academic_net.feature.projectdetails.data

import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetails
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetailsRepository

/** The only repository of the feature: passes the request to the [ProjectDetailsDataSource] as it is. */
class ProjectDetailsRepositoryImpl(
    private val dataSource: ProjectDetailsDataSource,
) : ProjectDetailsRepository {
    override suspend fun getProjectDetails(id: ProjectId): ProjectDetails? = dataSource.getProjectDetails(id)
}
