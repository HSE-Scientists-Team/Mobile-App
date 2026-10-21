package org.matchlab.academic_net

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import me.tatarka.inject.annotations.Inject
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.feature.responses.domain.ResponsesProjectSource
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * Tells the responses feature what it needs to know about projects. The two features do not know each other: this
 * adapter, living in the app, is the only place that connects them, over the projects feature's repository (so there is
 * one cache of projects: after a decision on 07d or an accepted invitation on 15 the project the other screens show is
 * the one that was read again here).
 */
@Inject
internal class ProjectsResponsesProjectSource(
    private val projects: ProjectsRepository,
) : ResponsesProjectSource {
    override suspend fun getProject(id: ProjectId): Project? = projects.getProject(id)

    override fun observeProject(id: ProjectId): Flow<Project?> = projects.observeProject(id)

    /** Reads the project again; a failure is dropped (the screens show what the cache has), a cancellation is not. */
    override suspend fun refreshProject(id: ProjectId) {
        suspendRunCatching { projects.loadProject(id) }
    }

    /** The viewer's own projects; fails with `FeatureUnavailableException` when the server cannot list them. */
    override suspend fun getMyProjects(): List<Project> = projects.observeMyProjects().first()
}
