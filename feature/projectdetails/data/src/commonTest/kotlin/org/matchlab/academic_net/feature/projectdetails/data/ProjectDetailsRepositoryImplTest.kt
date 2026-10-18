package org.matchlab.academic_net.feature.projectdetails.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.projectdetails.domain.InitiatorInfo
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetails

class ProjectDetailsRepositoryImplTest {
    private val details = ProjectDetails(
        project = Project(
            id = ProjectId("p1"),
            title = "T",
            type = null,
            sphere = Sphere("Biology", "Biology"),
            problem = "P",
            goal = "G",
            tags = emptyList(),
            initiator = UserSummary(UserId("u"), "U"),
            roles = emptyList(),
            status = null,
            period = null,
            viewerIsInitiator = false,
        ),
        initiator = InitiatorInfo("МГУ", rating = null, completedProjects = 0),
    )

    @Test
    fun theRequestGoesToTheSourceAndTheAnswerComesBackAsItIs() = runTest {
        val asked = mutableListOf<ProjectId>()
        val repository = ProjectDetailsRepositoryImpl(
            object : ProjectDetailsDataSource {
                override suspend fun getProjectDetails(id: ProjectId): ProjectDetails? {
                    asked += id
                    return if (id.value == "p1") details else null
                }
            },
        )

        assertSame(details, repository.getProjectDetails(ProjectId("p1")))
        assertNull(repository.getProjectDetails(ProjectId("other")))
        assertEquals(listOf(ProjectId("p1"), ProjectId("other")), asked)
    }
}
