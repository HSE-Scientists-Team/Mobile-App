package org.matchlab.academic_net.feature.projectdetails.data

import kotlinx.coroutines.delay
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.feature.projectdetails.domain.InitiatorInfo
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetails
import org.matchlab.academic_net.mockserver.MockProjectCatalog
import org.matchlab.academic_net.mockserver.MockServerProject

/**
 * In-memory stand-in for the project-page server: answers from the [catalog] (the mock server's
 * database from `:mockserver`, the same projects the search finds) after [latencyMillis], like a
 * network call.
 *
 * Rules of this mock (the contract for the backend):
 * - a project is returned by its id when it is [ProjectStatus.Recruiting];
 * - an unknown id and an unpublished project ([ProjectStatus.Unpublished]) are both `null`: the
 *   viewer may not see them, and the server does not say which of the two it is;
 * - whose project it is is the server's business (`Project.viewerIsInitiator`): the mock does not
 *   hide the viewer's own projects, the app shows them read-only.
 */
class MockProjectDetailsDataSource(
    private val catalog: List<MockServerProject> = MockProjectCatalog.all,
    private val latencyMillis: Long = DefaultLatencyMillis,
) : ProjectDetailsDataSource {
    override suspend fun getProjectDetails(id: ProjectId): ProjectDetails? {
        if (latencyMillis > 0) delay(latencyMillis)
        return catalog
            .firstOrNull { it.project.id == id && it.project.status == ProjectStatus.Recruiting }
            ?.let { found ->
                ProjectDetails(
                    project = found.project,
                    initiator = InitiatorInfo(
                        university = found.initiatorUniversity,
                        rating = found.initiatorRating,
                        completedProjects = found.initiatorCompletedProjects,
                    ),
                )
            }
    }

    private companion object {
        const val DefaultLatencyMillis = 300L
    }
}
