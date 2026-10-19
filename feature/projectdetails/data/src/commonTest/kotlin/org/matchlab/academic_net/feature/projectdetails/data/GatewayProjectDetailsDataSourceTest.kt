package org.matchlab.academic_net.feature.projectdetails.data

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectMember
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.projectdetails.domain.InitiatorInfo
import org.matchlab.academic_net.gateway.ProjectCatalogApi
import org.matchlab.academic_net.gateway.ProjectSearchParams
import org.matchlab.academic_net.gateway.ProjectsApi
import org.matchlab.academic_net.gateway.dto.ProjectCreateDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleInputDto
import org.matchlab.academic_net.gateway.dto.ProjectTransitionDto
import org.matchlab.academic_net.gateway.dto.ProjectUpdateDto
import org.matchlab.academic_net.feature.projectdetails.domain.LeaveNotAllowedException
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectGoneException
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectInputDto
import org.matchlab.academic_net.gateway.dto.ProjectListDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import org.matchlab.academic_net.gateway.mapping.AssembledProject
import org.matchlab.academic_net.gateway.mapping.ProjectAssembler
import org.matchlab.academic_net.gateway.mapping.ProjectTeamReader
import org.matchlab.academic_net.gateway.mapping.TeamMemberProfile
import org.matchlab.academic_net.gateway.mapping.toProject

class GatewayProjectDetailsDataSourceTest {
    private class ScriptedCatalog : ProjectCatalogApi {
        var onGet: suspend (String) -> ProjectDto = { throw AssertionError("Unexpected GET of $it") }
        val gets = mutableListOf<String>()

        override suspend fun get(id: String): ProjectDto {
            gets += id
            return onGet(id)
        }

        override suspend fun search(params: ProjectSearchParams, page: PageRequest): ProjectListDto =
            throw AssertionError("The page of a project never searches")

        override suspend fun list(params: ProjectSearchParams, page: PageRequest): ProjectListDto =
            throw AssertionError("The page of a project never reads the catalog")
    }

    /** Only [leave] is scripted; the page of a project never calls the rest. */
    private class ScriptedProjects : ProjectsApi {
        val left = mutableListOf<String>()
        var onLeave: suspend (String) -> ProjectDto = { ProjectDto() }

        override suspend fun leave(id: String): ProjectDto {
            left += id
            return onLeave(id)
        }

        override suspend fun get(id: String): ProjectDto = throw AssertionError("get")
        override suspend fun addRole(id: String, input: ProjectRoleInputDto): ProjectDto = throw AssertionError("addRole")
        override suspend fun create(input: ProjectCreateDto): ProjectDto = throw AssertionError("create")
        override suspend fun transition(id: String, input: ProjectTransitionDto): ProjectDto = throw AssertionError("transition")
        override suspend fun update(id: String, input: ProjectUpdateDto): ProjectDto = throw AssertionError("update")
        override suspend fun updateRole(id: String, roleId: String, input: ProjectRoleInputDto): ProjectDto =
            throw AssertionError("updateRole")
        override suspend fun deleteRole(id: String, roleId: String, expectedRevision: Int): ProjectDto =
            throw AssertionError("deleteRole")
        override suspend fun removeMember(id: String, userId: String): ProjectDto = throw AssertionError("removeMember")
    }

    private class ScriptedTeam : ProjectTeamReader {
        val reads = mutableListOf<List<ProjectMember>>()
        var profiles = mapOf<String, UserProfileDto>()
        var failure: Throwable? = null

        override suspend fun read(members: List<ProjectMember>): List<TeamMemberProfile> {
            reads += members
            failure?.let { throw it }
            return members.map { TeamMemberProfile(it, profiles[it.userId.value]) }
        }
    }

    private class Assembler : ProjectAssembler {
        val owners = mutableMapOf<String, UserProfileDto>()
        var failure: Throwable? = null

        override suspend fun assemble(projects: List<ProjectDto>): List<AssembledProject> {
            failure?.let { throw it }
            return projects.mapNotNull { dto ->
                val owner = owners[dto.ownerUserId]
                dto.toProject(UserId("viewer"), owner)?.let { AssembledProject(it, owner) }
            }
        }
    }

    private val id = ProjectId("p1")
    private val api = ScriptedCatalog()
    private val assembler = Assembler()
    private val team = ScriptedTeam()
    private val projects = ScriptedProjects()
    private val source = GatewayProjectDetailsDataSource(api, projects, assembler, team)

    private fun dto(owner: String = "owner-1", roles: List<ProjectRoleDto> = emptyList()) =
        ProjectDto(id = "p1", ownerUserId = owner, details = ProjectInputDto(title = "AI-навигация", field = "Biology"), roles = roles)

    private fun owner(
        university: String? = "МГУ",
        rating: Double = 0.0,
        reviewCount: Int = 0,
        completed: Int = 0,
    ) = UserProfileDto(
        userId = "owner-1",
        details = UserProfileInputDto(displayName = "Елена", university = university),
        rating = rating,
        reviewCount = reviewCount,
        completedProjectCount = completed,
    )

    // ---- the project is there ----

    @Test
    fun aProjectIsReadFromItsIdWithItsInitiatorsFacts() = runTest {
        api.onGet = { dto() }
        assembler.owners["owner-1"] = owner(university = "  НИУ ВШЭ ", rating = 4.8, reviewCount = 5, completed = 12)

        val details = assertNotNull(source.getProjectDetails(id))

        assertEquals(listOf("p1"), api.gets)
        assertEquals("AI-навигация", details.project.title)
        assertEquals(InitiatorInfo(university = "НИУ ВШЭ", rating = 4.8, completedProjects = 12), details.initiator)
    }

    @Test
    fun aPersonWithoutReviewsHasNoRatingWhateverNumberTheServerSent() = runTest {
        // API-GAP G20: the API does not say what `rating` is when there are no reviews.
        api.onGet = { dto() }
        assembler.owners["owner-1"] = owner(rating = 3.7, reviewCount = 0)

        assertNull(source.getProjectDetails(id)?.initiator?.rating)
    }

    @Test
    fun aPersonWithAReviewHasTheirRating() = runTest {
        api.onGet = { dto() }
        assembler.owners["owner-1"] = owner(rating = 4.0, reviewCount = 1)

        assertEquals(4.0, source.getProjectDetails(id)?.initiator?.rating)
    }

    @Test
    fun aNegativeNumberOfFinishedProjectsIsZero() = runTest {
        api.onGet = { dto() }
        assembler.owners["owner-1"] = owner(completed = -3)

        assertEquals(0, source.getProjectDetails(id)?.initiator?.completedProjects)
    }

    @Test
    fun anOwnerWithoutAProfileTheViewerMaySeeIsNoReasonToFail() = runTest {
        api.onGet = { dto() }

        val details = assertNotNull(source.getProjectDetails(id))

        assertEquals(InitiatorInfo(university = "", rating = null, completedProjects = 0), details.initiator)
        assertEquals("", details.project.initiator.name)
    }

    @Test
    fun anOwnerWithoutAUniversityHasABlankOne() = runTest {
        api.onGet = { dto() }
        assembler.owners["owner-1"] = owner(university = null)

        assertEquals("", source.getProjectDetails(id)?.initiator?.university)
    }

    @Test
    fun theRolesComeAlongWithTheirPlaces() = runTest {
        api.onGet = { dto(roles = listOf(ProjectRoleDto("r1", "ML", slots = 2, occupied = 1))) }

        val project = assertNotNull(source.getProjectDetails(id)).project

        assertEquals(2, project.totalPlaces)
        assertEquals(1, project.occupiedPlaces)
    }

    // ---- the project is not there ----

    @Test
    fun aProjectTheViewerMayNotSeeIsNull() = runTest {
        for (status in listOf(400, 403, 404)) {
            api.onGet = { throw RemoteFailure.Http(status) }

            assertNull(source.getProjectDetails(id), "$status")
        }
    }

    @Test
    fun anAnswerTheAppCannotReadIsNull() = runTest {
        api.onGet = { dto(owner = "") }

        assertNull(source.getProjectDetails(id))
    }

    // ---- failures ----

    @Test
    fun anyOtherFailureIsThrownAsItIs() = runTest {
        for (failure in listOf(RemoteFailure.Http(500), RemoteFailure.Http(503), RemoteFailure.NoConnection(), RemoteFailure.Timeout(), RemoteFailure.Malformed())) {
            api.onGet = { throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure> { source.getProjectDetails(id) })
        }
    }

    @Test
    fun aFailureOfTheOwnersLookupFailsThePage() = runTest {
        val failure = RemoteFailure.Http(500)
        api.onGet = { dto() }
        assembler.failure = failure

        assertSame(failure, assertFailsWith<RemoteFailure.Http> { source.getProjectDetails(id) })
    }

    @Test
    fun aCancellationPassesThrough() = runTest {
        api.onGet = { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { source.getProjectDetails(id) }
    }

    // ---- the team (second step) ----

    private fun projectWithMembers() = assertNotNull(
        ProjectDto(
            id = "p1",
            ownerUserId = "owner-1",
            details = ProjectInputDto(title = "T"),
            members = listOf(
                org.matchlab.academic_net.gateway.dto.ProjectMemberDto("u1", "r1", "Dev"),
                org.matchlab.academic_net.gateway.dto.ProjectMemberDto("viewer", "r2", ""),
            ),
            roles = listOf(ProjectRoleDto(id = "r2", title = "Дизайнер")),
        ).toProject(UserId("viewer"), null),
    )

    @Test
    fun theTeamIsTheProfilesOfTheMembersInTheirOrder() = runTest {
        team.profiles = mapOf(
            "u1" to UserProfileDto(
                userId = "u1",
                details = UserProfileInputDto(displayName = " Анна "),
                rating = 4.5,
                reviewCount = 2,
                completedProjectCount = 3,
            ),
        )

        val result = source.getTeam(projectWithMembers())

        assertEquals(listOf("u1", "viewer"), result.map { it.person.user.id.value })
        assertEquals("Анна", result[0].person.user.name)
        assertEquals(4.5, result[0].person.rating)
        assertEquals(3, result[0].person.completedProjects)
        assertEquals("Dev", result[0].roleTitle)
        assertFalse(result[0].isViewer)
        assertTrue(result[0].person.hasProfile)
    }

    @Test
    fun aMemberWithoutAProfileIsKeptWithoutOneAndTheRoleTitleComesFromTheRole() = runTest {
        val result = source.getTeam(projectWithMembers())

        assertFalse(result[1].person.hasProfile)
        assertEquals("", result[1].person.user.name)
        assertEquals("Дизайнер", result[1].roleTitle)
        assertTrue(result[1].isViewer)
        assertEquals(RoleId("r2"), team.reads.single()[1].roleId)
    }

    @Test
    fun theTeamOfAProjectWithoutMembersIsEmpty() = runTest {
        val project = assertNotNull(dto().toProject(UserId("viewer"), null))

        assertEquals(emptyList(), source.getTeam(project))
    }

    @Test
    fun aFailureOfTheTeamReaderCancellationPassesThrough() = runTest {
        team.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { source.getTeam(projectWithMembers()) }
    }

    // ---- leaving ----

    @Test
    fun leavingIsADeleteOfTheMembershipOfTheProjectAndTheAnswerIsNotAssembled() = runTest {
        assembler.failure = AssertionError("the answer of a leave is not assembled")

        source.leaveProject(ProjectId("p/1"))

        assertEquals(listOf("p/1"), projects.left)
        assertTrue(api.gets.isEmpty(), "nothing is read after the exit")
    }

    @Test
    fun a403Or409IsNotAllowedAndA404IsGone() = runTest {
        for (status in listOf(403, 409)) {
            projects.onLeave = { throw RemoteFailure.Http(status) }
            assertEquals(id, assertFailsWith<LeaveNotAllowedException> { source.leaveProject(id) }.projectId)
        }
        projects.onLeave = { throw RemoteFailure.Http(404) }
        assertEquals(id, assertFailsWith<ProjectGoneException> { source.leaveProject(id) }.projectId)
    }

    @Test
    fun otherFailuresOfLeavingPassAsTheyAre() = runTest {
        for (failure in listOf(RemoteFailure.Http(400), RemoteFailure.Http(401), RemoteFailure.Http(500), RemoteFailure.NoConnection(), RemoteFailure.Timeout())) {
            projects.onLeave = { throw failure }
            assertSame(failure, assertFailsWith<RemoteFailure> { source.leaveProject(id) })
        }
        assertEquals(5, projects.left.size, "no automatic retry")
    }

    @Test
    fun aCancellationWhileLeavingPassesThrough() = runTest {
        projects.onLeave = { throw CancellationException("c") }
        assertFailsWith<CancellationException> { source.leaveProject(id) }
    }
}
