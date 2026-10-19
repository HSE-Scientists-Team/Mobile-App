package org.matchlab.academic_net.feature.complaints.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.complaints.domain.Complaint
import org.matchlab.academic_net.feature.complaints.domain.ComplaintNotAllowedException
import org.matchlab.academic_net.feature.complaints.domain.ComplaintSubject
import org.matchlab.academic_net.feature.complaints.domain.ComplaintTarget
import org.matchlab.academic_net.feature.complaints.domain.ProjectComplaintReason
import org.matchlab.academic_net.feature.complaints.domain.UserComplaintReason
import org.matchlab.academic_net.gateway.ComplaintsApi
import org.matchlab.academic_net.gateway.ProjectCatalogApi
import org.matchlab.academic_net.gateway.ProjectSearchParams
import org.matchlab.academic_net.gateway.UserDirectory
import org.matchlab.academic_net.gateway.dto.CreateComplaintDto
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectInputDto
import org.matchlab.academic_net.gateway.dto.ProjectListDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import org.matchlab.academic_net.gateway.mapping.AssembledProject
import org.matchlab.academic_net.gateway.mapping.ProjectAssembler
import org.matchlab.academic_net.gateway.mapping.toProject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class ScriptedComplaintsApi : ComplaintsApi {
    val created = mutableListOf<CreateComplaintDto>()
    var failure: Throwable? = null

    override suspend fun create(input: CreateComplaintDto) {
        created += input
        failure?.let { throw it }
    }
}

private class ScriptedCatalog : ProjectCatalogApi {
    val gets = mutableListOf<String>()
    var onGet: suspend (String) -> ProjectDto = { throw AssertionError("Unexpected GET of $it") }

    override suspend fun get(id: String): ProjectDto {
        gets += id
        return onGet(id)
    }

    override suspend fun search(params: ProjectSearchParams, page: PageRequest): ProjectListDto = throw AssertionError("search")

    override suspend fun list(params: ProjectSearchParams, page: PageRequest): ProjectListDto = throw AssertionError("list")
}

private fun profile(id: String, name: String, university: String? = null) =
    UserProfileDto(userId = id, details = UserProfileInputDto(displayName = name, university = university))

private fun projectDto(id: String = "p1", owner: String = "owner-1", title: String = "Проект") = ProjectDto(
    id = id,
    ownerUserId = owner,
    details = ProjectInputDto(title = title, type = "1", field = "1", problem = "P", goal = "G"),
    status = "1",
    revision = 1,
)

class GatewayComplaintsDataSourceTest {
    private val api = ScriptedComplaintsApi()
    private val catalog = ScriptedCatalog()
    private val profiles = mutableMapOf<String, UserProfileDto?>()
    private val fetched = mutableListOf<String>()
    private val owners = mutableMapOf<String, UserProfileDto>()
    private val assembled = mutableListOf<List<ProjectDto>>()

    private fun TestScope.source(): GatewayComplaintsDataSource {
        val users = UserDirectory(
            fetch = { id ->
                fetched += id
                profiles[id] ?: throw RemoteFailure.Http(404)
            },
            scope = backgroundScope,
        )
        val assembler = ProjectAssembler { dtos ->
            assembled += dtos
            dtos.mapNotNull { dto -> dto.toProject(UserId("viewer"), owners[dto.ownerUserId])?.let { AssembledProject(it, owners[dto.ownerUserId]) } }
        }
        return GatewayComplaintsDataSource(api, users, catalog, assembler)
    }

    // ---- send ----

    @Test
    fun aComplaintAboutAPersonSendsTheReasonTextAndOnlyTheUserTarget() = runTest {
        source().send(Complaint(ComplaintTarget.User(UserId("u1")), UserComplaintReason.PortfolioFraud, "Текст"))

        assertEquals(
            listOf(CreateComplaintDto(subject = "portfolio_fraud", description = "Текст", targetUserId = "u1", targetProjectId = null)),
            api.created,
        )
        assertTrue(api.created.single().fileIds.isEmpty())
    }

    @Test
    fun aComplaintAboutAProjectSendsTheReasonTextAndOnlyTheProjectTarget() = runTest {
        source().send(Complaint(ComplaintTarget.Project(ProjectId("p1")), ProjectComplaintReason.Other, "Текст"))

        assertEquals(
            listOf(CreateComplaintDto(subject = "other", description = "Текст", targetUserId = null, targetProjectId = "p1")),
            api.created,
        )
    }

    @Test
    fun everyReasonGoesWithItsOwnText() = runTest {
        val source = source()
        for (reason in UserComplaintReason.entries) {
            source.send(Complaint(ComplaintTarget.User(UserId("u")), reason, "x"))
        }

        assertEquals(listOf("spam", "insults", "portfolio_fraud", "fake_account", "scam", "other"), api.created.map { it.subject })
    }

    @Test
    fun a403IsNotAllowed() = runTest {
        api.failure = RemoteFailure.Http(403)

        assertFailsWith<ComplaintNotAllowedException> {
            source().send(Complaint(ComplaintTarget.User(UserId("u1")), UserComplaintReason.Spam, "x"))
        }
        assertEquals(1, api.created.size, "no retry")
    }

    @Test
    fun otherFailuresAreThrownAsTheyAreAndNeverRepeated() = runTest {
        val source = source()
        for (failure in listOf(RemoteFailure.Http(400), RemoteFailure.Http(404), RemoteFailure.Http(409), RemoteFailure.Http(500), RemoteFailure.NoConnection(), RemoteFailure.Timeout())) {
            api.created.clear()
            api.failure = failure

            val thrown = assertFailsWith<RemoteFailure> {
                source.send(Complaint(ComplaintTarget.User(UserId("u1")), UserComplaintReason.Spam, "x"))
            }

            assertSame(failure, thrown)
            assertEquals(1, api.created.size)
        }
    }

    @Test
    fun aCancellationPassesThrough() = runTest {
        api.failure = CancellationException("c")
        assertFailsWith<CancellationException> {
            source().send(Complaint(ComplaintTarget.User(UserId("u1")), UserComplaintReason.Spam, "x"))
        }
    }

    // ---- getSubject of a person ----

    @Test
    fun aPersonCardCarriesTheIdOfTheRequestEvenWhenTheProfileLeavesItOut() = runTest {
        profiles["u1"] = profile(id = "", name = "Анна", university = " МГУ ")

        val subject = assertIs<ComplaintSubject.PersonSubject>(source().getSubject(ComplaintTarget.User(UserId("u1"))))

        assertEquals(UserId("u1"), subject.person.user.id)
        assertEquals("Анна", subject.person.user.name)
        assertEquals("МГУ", subject.person.university)
    }

    @Test
    fun aPersonThatIsNotThereIsNull() = runTest {
        assertNull(source().getSubject(ComplaintTarget.User(UserId("nobody"))))
    }

    @Test
    fun aBlankPersonIdAsksNothing() = runTest {
        assertNull(source().getSubject(ComplaintTarget.User(UserId(" "))))
        assertTrue(fetched.isEmpty())
    }

    @Test
    fun aFailureOfTheProfileIsThrown() = runTest {
        val users = UserDirectory(fetch = { throw RemoteFailure.Http(500) }, scope = backgroundScope)
        val source = GatewayComplaintsDataSource(api, users, catalog) { emptyList() }

        assertFailsWith<RemoteFailure.Http> { source.getSubject(ComplaintTarget.User(UserId("u1"))) }
    }

    // ---- getSubject of a project ----

    @Test
    fun aProjectCardHasTheTitleTheOwnerAndTheUniversity() = runTest {
        catalog.onGet = { projectDto(id = it, owner = "owner-1", title = "AI-навигация") }
        owners["owner-1"] = profile("owner-1", "Иван Петров", university = " ВШЭ ")

        val subject = assertIs<ComplaintSubject.ProjectSubject>(source().getSubject(ComplaintTarget.Project(ProjectId("p1"))))

        assertEquals(ComplaintSubject.ProjectSubject("AI-навигация", "Иван Петров", "ВШЭ"), subject)
        assertEquals(listOf("p1"), catalog.gets)
    }

    @Test
    fun anOwnerWithoutAProfileGivesAnEmptyUniversity() = runTest {
        catalog.onGet = { projectDto(id = it, owner = "owner-1") }

        val subject = assertIs<ComplaintSubject.ProjectSubject>(source().getSubject(ComplaintTarget.Project(ProjectId("p1"))))

        assertEquals("", subject.initiatorUniversity)
    }

    @Test
    fun aProjectThatIsNotVisibleIsNull() = runTest {
        for (status in listOf(400, 403, 404)) {
            catalog.onGet = { throw RemoteFailure.Http(status) }
            assertNull(source().getSubject(ComplaintTarget.Project(ProjectId("p1"))), "status $status")
        }
    }

    @Test
    fun aProjectTheMappingCannotReadIsNull() = runTest {
        catalog.onGet = { projectDto(id = "") }
        assertNull(source().getSubject(ComplaintTarget.Project(ProjectId("p1"))))
    }

    @Test
    fun otherFailuresOfTheProjectAreThrown() = runTest {
        val source = source()
        for (failure in listOf(RemoteFailure.Http(500), RemoteFailure.Http(409), RemoteFailure.NoConnection())) {
            catalog.onGet = { throw failure }
            assertSame(failure, assertFailsWith<RemoteFailure> { source.getSubject(ComplaintTarget.Project(ProjectId("p1"))) })
        }
    }
}

class ComplaintsRepositoryImplTest {
    @Test
    fun itPassesTheCallsToTheSource() = runTest {
        val sent = mutableListOf<Complaint>()
        val subject = ComplaintSubject.ProjectSubject("T", "N", "U")
        val repository = ComplaintsRepositoryImpl(
            object : ComplaintsDataSource {
                override suspend fun getSubject(target: ComplaintTarget) = subject
                override suspend fun send(complaint: Complaint) {
                    sent += complaint
                }
            },
        )
        val complaint = Complaint(ComplaintTarget.User(UserId("u")), UserComplaintReason.Other, "x")

        repository.send(complaint)

        assertEquals(listOf(complaint), sent)
        assertSame(subject, repository.getSubject(ComplaintTarget.Project(ProjectId("p"))))
    }
}
