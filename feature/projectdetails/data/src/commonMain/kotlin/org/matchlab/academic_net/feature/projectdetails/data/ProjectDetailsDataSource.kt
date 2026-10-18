package org.matchlab.academic_net.feature.projectdetails.data

import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetails

/**
 * Where the project pages come from: the Gateway ([GatewayProjectDetailsDataSource]). The server decides what the viewer
 * may see.
 */
interface ProjectDetailsDataSource {
    /** The project with [id], or `null` when the viewer may not see such a project. */
    suspend fun getProjectDetails(id: ProjectId): ProjectDetails?
}
