package org.matchlab.academic_net.feature.responses.domain

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId

/**
 * What the responses need to know about projects. The responses feature does not depend on the projects feature, so the
 * app implements this port over it (an adapter in `:shared`).
 */
interface ResponsesProjectSource {
    /** The project as this session last read it, else read now; `null`: the viewer may not see it. Throws a failure. */
    suspend fun getProject(id: ProjectId): Project?

    /** The project as the session knows it, following its changes; `null` until read. Never fails. */
    fun observeProject(id: ProjectId): Flow<Project?>

    /** Reads the project again, after a decision changed its team. Never throws (but a cancellation). */
    suspend fun refreshProject(id: ProjectId)

    /** The viewer's own projects ("Я инициатор"). Throws a failure, `FeatureUnavailableException` included. */
    suspend fun getMyProjects(): List<Project>
}
