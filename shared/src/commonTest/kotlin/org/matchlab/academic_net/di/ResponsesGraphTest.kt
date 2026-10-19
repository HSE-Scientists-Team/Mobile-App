package org.matchlab.academic_net.di

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoinStatus
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.complaints.domain.ComplaintDraft
import org.matchlab.academic_net.feature.complaints.domain.ComplaintNotAllowedException
import org.matchlab.academic_net.feature.complaints.domain.ComplaintTarget
import org.matchlab.academic_net.feature.complaints.domain.ProjectComplaintReason
import org.matchlab.academic_net.feature.complaints.domain.UserComplaintReason
import org.matchlab.academic_net.feature.projects.domain.usecase.ParticipatingProjectsResult
import org.matchlab.academic_net.feature.responses.domain.usecase.JoinActionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The applications, invitations and complaints in the graph of a signed-in user over the Gateway (a mock engine): the
 * wiring that no layer's own tests see. The answers are the shapes `docs/openapi.yaml` gives, the codes those of
 * `docs/api-values.md`.
 */
class ResponsesGraphTest {
    private val viewer = ServerUserId
    private val session = Session(UserId(viewer), StudentEmail, AccessToken("token-1"))

    private fun joinJson(id: String, project: String = "p-1", role: String = "r-1", type: String = "1", status: String = "1", created: Long = 1_760_000_000) =
        """{"id":"$id","project_id":"$project","role_id":"$role","candidate_user_id":"$viewer","type":"$type","status":"$status","message":"","created_at_unix":"$created"}"""

    private fun projectJson(id: String, owner: String, memberIsViewer: Boolean = false) =
        """{"id":"$id","owner_user_id":"$owner","details":{"title":"Проект $id","type":"1","field":"7"},"status":"1","revision":2,""" +
            """"roles":[{"id":"r-1","title":"Дизайнер","slots":2,"occupied":${if (memberIsViewer) 1 else 0}}],""" +
            """"members":[${if (memberIsViewer) """{"user_id":"$viewer","role_id":"r-1"}""" else ""}]}"""

    private val ownerProfile =
        """{"user_id":"owner-a","details":{"display_name":"Анна","university":"МГУ"},"rating":4.5,"review_count":1}"""

    private fun server(joins: String = joinJson("j-1")) = FakeServer().apply {
        onOther = { arrival ->
            when (arrival.call) {
                "GET /api/v1/project-joins" -> json("""{"items":[$joins],"total":"1"}""")
                "GET /api/v1/projects/p-1" -> json(projectJson("p-1", "owner-a", memberIsViewer = true))
                "GET /api/v1/users/owner-a/profile" -> json(ownerProfile)
                "POST /api/v1/project-joins/j-1/decision" -> json(joinJson("j-1", status = "2"))
                "POST /api/v1/complaints" -> json("""{"id":"c-1","status":"1"}""", HttpStatusCode.Created)
                else -> jsonError(HttpStatusCode.NotFound, "not found")
            }
        }
    }

    private fun graph(server: FakeServer) = AppLaunch(server).sessionGraph(session)

    @Test
    fun theUiDependenciesOfBothFeaturesAreBuiltWithoutARequest() {
        val server = server()

        graph(server).dependencies

        assertEquals(emptyList(), server.calls)
    }

    @Test
    fun theViewersListIsReadWholeInPagesOf100WithoutAFilter() = runTest {
        val server = server()

        graph(server).dependencies.responses.refreshMyResponses()

        val request = server.requests("GET /api/v1/project-joins").single()
        assertEquals(listOf("Bearer token-1"), request.authorization)
        assertEquals(listOf("100"), request.queryValues("limit"))
        assertEquals(listOf("0"), request.queryValues("offset"))
        assertTrue("status" !in request.queryNames && "role_id" !in request.queryNames, request.query)
    }

    @Test
    fun oneRepositoryOfTheSessionServesTheScreenTheBadgeTheProjectPageAndTheTab() = runTest {
        val server = server(joinJson("j-1") + "," + joinJson("j-2", role = "r-2", type = "2"))
        val dependencies = graph(server).dependencies

        dependencies.responses.refreshMyResponses().getOrThrow()

        val list = dependencies.responses.observeMyResponses().first()
        assertEquals(listOf("j-1", "j-2"), list?.map { it.join.id.value })
        assertEquals("Проект p-1", list?.first()?.project?.title)
        assertEquals("Дизайнер", list?.first()?.role?.title)
        assertTrue(dependencies.responses.observeHasNewInvitations().first(), "an invitation nobody has looked at")
        assertEquals(setOf(RoleId("r-1")), dependencies.projectDetails.observeAppliedRoles(ProjectId("p-1")).first())
        assertEquals(1, server.requests("GET /api/v1/projects/p-1").size, "the projects are asked once per read of the list")
    }

    @Test
    fun theMemberTabReadsTheListAfreshAndTheProjectsOfTheAcceptedRecords() = runTest {
        val server = server(joinJson("j-1", status = "2"))
        val dependencies = graph(server).dependencies

        val result = dependencies.projects.getParticipatingProjects()

        val loaded = assertIs<ParticipatingProjectsResult.Loaded>(result)
        assertEquals(listOf("p-1"), loaded.projects.map { it.id.value })
        assertEquals(1, server.requests("GET /api/v1/project-joins").size)
    }

    @Test
    fun aDecisionOfTheViewerGoesToTheServerAndThenReadsTheProjectAgain() = runTest {
        val server = server(joinJson("j-1", type = "2"))
        val dependencies = graph(server).dependencies
        dependencies.responses.refreshMyResponses().getOrThrow()
        val invitation = dependencies.responses.observeMyResponses().first()!!.single().join
        val reads = server.requests("GET /api/v1/projects/p-1").size

        val result = dependencies.responses.decideInvitation(invitation, accept = true)

        assertIs<JoinActionResult.Done>(result)
        val post = server.requests("POST /api/v1/project-joins/j-1/decision").single()
        assertEquals("""{"accept":true}""", post.body)
        assertTrue(server.requests("GET /api/v1/projects/p-1").size > reads, "the team changed, the project is read again")
        assertEquals(
            ProjectJoinStatus.Accepted,
            dependencies.responses.observeMyResponses().first()!!.single().join.status,
        )
    }

    // ---- complaints ----

    @Test
    fun aComplaintAboutAPersonIsPostedWithTheCodeAndTheTarget() = runTest {
        val server = server()

        val result = graph(server).dependencies.complaints.sendComplaint(
            ComplaintDraft(ComplaintTarget.User(UserId("u-9")), UserComplaintReason.Scam, " Просит деньги "),
        )

        assertEquals(SubmitResult.Success(Unit), result)
        val post = server.requests("POST /api/v1/complaints").single()
        assertEquals(listOf("Bearer token-1"), post.authorization)
        for (part in listOf("\"subject\":\"5\"", "\"description\":\"Просит деньги\"", "\"target_user_id\":\"u-9\"", "\"file_ids\":[]")) {
            assertTrue(part in post.body, "$part in ${post.body}")
        }
        assertTrue("target_project_id" !in post.body, post.body)
    }

    @Test
    fun aComplaintAboutAProjectCarriesTheProjectTarget() = runTest {
        val server = server()

        graph(server).dependencies.complaints.sendComplaint(
            ComplaintDraft(ComplaintTarget.Project(ProjectId("p-1")), ProjectComplaintReason.Other, "Текст"),
        )

        val body = server.requests("POST /api/v1/complaints").single().body
        assertTrue("\"subject\":\"4\"" in body && "\"target_project_id\":\"p-1\"" in body, body)
    }

    @Test
    fun a403OfAComplaintIsTheNotAllowedFailureAndIsNotRepeated() = runTest {
        val server = server().apply {
            val before = onOther
            onOther = { arrival ->
                if (arrival.call == "POST /api/v1/complaints") jsonError(HttpStatusCode.Forbidden) else before(arrival)
            }
        }

        val result = graph(server).dependencies.complaints.sendComplaint(
            ComplaintDraft(ComplaintTarget.User(UserId("u-9")), UserComplaintReason.Spam, "x"),
        )

        assertIs<ComplaintNotAllowedException>(assertIs<SubmitResult.Failure>(result).cause)
        assertEquals(1, server.requests("POST /api/v1/complaints").size)
    }

    @Test
    fun anInvalidComplaintSendsNothing() = runTest {
        val server = server()

        val result = graph(server).dependencies.complaints.sendComplaint(ComplaintDraft(ComplaintTarget.User(UserId("u"))))

        assertIs<SubmitResult.Invalid<*>>(result)
        assertEquals(emptyList(), server.requests("POST /api/v1/complaints"))
    }
}
