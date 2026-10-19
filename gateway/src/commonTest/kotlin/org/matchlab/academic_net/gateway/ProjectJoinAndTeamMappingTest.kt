package org.matchlab.academic_net.gateway

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoinId
import org.matchlab.academic_net.common.model.ProjectJoinStatus
import org.matchlab.academic_net.common.model.ProjectJoinType
import org.matchlab.academic_net.common.model.ProjectMember
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.RoleMember
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectInputDto
import org.matchlab.academic_net.gateway.dto.ProjectJoinDto
import org.matchlab.academic_net.gateway.dto.ProjectJoinListDto
import org.matchlab.academic_net.gateway.dto.ProjectMemberDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import org.matchlab.academic_net.gateway.mapping.DirectoryProjectTeamReader
import org.matchlab.academic_net.gateway.mapping.TeamMemberProfile
import org.matchlab.academic_net.gateway.mapping.toProject
import org.matchlab.academic_net.gateway.mapping.toProjectJoin
import org.matchlab.academic_net.gateway.mapping.toProjectJoinPage
import org.matchlab.academic_net.gateway.mapping.toRoleMember
import org.matchlab.academic_net.gateway.mapping.withTeam
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProjectJoinMappingTest {
    private fun dto(
        id: String = "j-1",
        projectId: String = "p-1",
        roleId: String = "r-1",
        candidate: String = "u-1",
        type: String = "1",
        status: String = "1",
        message: String = " Привет ",
        createdAt: Long = 1_760_000_000L,
    ) = ProjectJoinDto(id, projectId, roleId, candidate, type, status, message, createdAt)

    @Test
    fun aFullRecordIsRead() {
        val join = assertNotNull(dto().toProjectJoin())

        assertEquals(ProjectJoinId("j-1"), join.id)
        assertEquals(ProjectId("p-1"), join.projectId)
        assertEquals(RoleId("r-1"), join.roleId)
        assertEquals(UserId("u-1"), join.candidateId)
        assertEquals(ProjectJoinType.Application, join.type)
        assertEquals(ProjectJoinStatus.Pending, join.status)
        assertEquals("Привет", join.message)
        assertEquals(Instant.fromEpochSeconds(1_760_000_000), join.createdAt)
    }

    @Test
    fun aBlankIdOfTheRecordTheProjectOrTheRoleCannotBeShown() {
        assertNull(dto(id = "").toProjectJoin())
        assertNull(dto(id = "  ").toProjectJoin())
        assertNull(dto(projectId = "").toProjectJoin())
        assertNull(dto(roleId = " ").toProjectJoin())
    }

    @Test
    fun unknownCodesAndMissingFieldsDoNotHideTheRecord() {
        val join = assertNotNull(dto(type = "9", status = "pending", candidate = "", createdAt = 0).toProjectJoin())

        assertNull(join.type)
        assertNull(join.status)
        assertNull(join.candidateId)
        assertNull(join.createdAt)
    }

    @Test
    fun everyCodeOfTheContractIsRead() {
        assertEquals(ProjectJoinType.Invitation, dto(type = "2").toProjectJoin()?.type)
        assertEquals(
            listOf(ProjectJoinStatus.Pending, ProjectJoinStatus.Accepted, ProjectJoinStatus.Declined, ProjectJoinStatus.Withdrawn),
            listOf("1", "2", "3", "4").map { dto(status = it).toProjectJoin()?.status },
        )
    }

    @Test
    fun aNegativeTimeIsUnknown() {
        assertNull(dto(createdAt = -5).toProjectJoin()?.createdAt)
    }

    @Test
    fun aPageKeepsTheReadableRecordsAndCountsAllThatWereSent() {
        val request = PageRequest(limit = 20, offset = 0)
        val list = ProjectJoinListDto(items = listOf(dto(id = "a"), dto(id = ""), dto(id = "c")), total = 3)

        val page = list.toProjectJoinPage(request)

        assertEquals(listOf(ProjectJoinId("a"), ProjectJoinId("c")), page.items.map { it.id })
        assertEquals(3, page.received)
        assertEquals(3, page.total)
    }

    @Test
    fun aTotalSmallerThanWhatWasReceivedIsRaisedToIt() {
        val request = PageRequest(offset = 20, limit = 20)
        val list = ProjectJoinListDto(items = listOf(dto(id = "a")), total = 0)

        val page = list.toProjectJoinPage(request)

        assertEquals(21, page.total)
    }
}

class ProjectTeamMappingTest {
    private val viewer = UserId("viewer")

    private fun project(
        members: List<ProjectMemberDto>,
        roles: List<ProjectRoleDto> = listOf(ProjectRoleDto(id = "r-1", title = " Dev ", slots = 2, occupied = 1)),
    ): Project = assertNotNull(
        ProjectDto(
            id = "p-1",
            ownerUserId = "owner",
            details = ProjectInputDto(title = "T"),
            roles = roles,
            members = members,
        ).toProject(viewer, null),
    )

    private fun member(user: String, role: String = "r-1", title: String = "", status: String = "") =
        ProjectMemberDto(user, role, title, status)

    @Test
    fun aMemberWithoutAUserIdIsDropped() {
        val project = project(listOf(member(""), member("  "), member("u1")))

        assertEquals(listOf(UserId("u1")), project.members.map { it.userId })
    }

    @Test
    fun theTitleOfTheRoleIsTheServersThenTheRolesThenEmpty() {
        val project = project(
            listOf(
                member("u1", title = "  Бэкенд "),
                member("u2", title = "   "),
                member("u3", role = "r-9", title = ""),
                member("u4", role = ""),
            ),
        )

        assertEquals(listOf("Бэкенд", "Dev", "", ""), project.members.map { it.roleTitle })
    }

    @Test
    fun anEmptyRoleIdIsNullAndTheServerOrderIsKept() {
        val project = project(listOf(member("u3", role = ""), member("u1"), member("u2")))

        assertEquals(listOf("u3", "u1", "u2"), project.members.map { it.userId.value })
        assertNull(project.members[0].roleId)
        assertEquals(RoleId("r-1"), project.members[1].roleId)
    }

    @Test
    fun aRepeatedPairIsDroppedButAPersonOnTwoRolesStays() {
        val project = project(listOf(member("u1"), member("u1"), member("u1", role = "r-2"), member("u1", role = "")))

        assertEquals(listOf(RoleId("r-1"), RoleId("r-2"), null), project.members.map { it.roleId })
    }

    @Test
    fun theViewerOnTheTeamIsTold() {
        val project = project(listOf(member("u1"), member("viewer")))

        assertEquals(listOf(false, true), project.members.map { it.isViewer })
        assertTrue(project.viewerIsMember)
        assertFalse(project(listOf(member("u1"))).viewerIsMember)
    }

    @Test
    fun theStatusOfAMemberDoesNotHideHim() {
        val project = project(listOf(member("u1", status = "left"), member("u2", status = "weird")))

        assertEquals(2, project.members.size)
    }

    @Test
    fun aNegativePendingCountIsZero() {
        fun count(n: Int) = assertNotNull(ProjectDto(id = "p", ownerUserId = "o", pendingJoinCount = n).toProject(viewer, null)).pendingJoinCount

        assertEquals(0, count(-3))
        assertEquals(0, count(0))
        assertEquals(5, count(5))
    }

    @Test
    fun theRolesStayWithoutMembersUntilTheTeamIsRead() {
        val project = project(listOf(member("u1")))

        assertEquals(emptyList(), project.roles.single().members)
        assertEquals(1, project.roles.single().occupied)
    }

    // ---- toRoleMember / withTeam ----

    private fun profile(id: String = "u1", name: String = " Анна ", rating: Double = 4.5, reviews: Int = 2, done: Int = 3, verified: Boolean = true) =
        UserProfileDto(
            userId = id,
            details = UserProfileInputDto(displayName = name),
            verified = verified,
            rating = rating,
            reviewCount = reviews,
            completedProjectCount = done,
        )

    private val pm = ProjectMember(UserId("u1"), RoleId("r-1"), "Dev", isViewer = false)

    @Test
    fun aMemberWithoutAProfileHasTheIdAnEmptyNameAndNoStats() {
        val person = TeamMemberProfile(pm, null).toRoleMember()

        assertEquals(RoleMember(UserSummary(UserId("u1"), name = ""), hasProfile = false), person)
        assertNull(person.rating)
        assertEquals(0, person.completedProjects)
    }

    @Test
    fun aMemberWithAProfileHasNameRatingAndFinishedProjects() {
        val person = TeamMemberProfile(pm, profile()).toRoleMember()

        assertTrue(person.hasProfile)
        assertEquals("Анна", person.user.name)
        assertTrue(person.user.isVerified)
        assertEquals(4.5, person.rating)
        assertEquals(3, person.completedProjects)
    }

    @Test
    fun theIdIsAlwaysTheMembersEvenIfTheProfileLeavesItOutOrNamesAnother() {
        assertEquals(UserId("u1"), TeamMemberProfile(pm, profile(id = "")).toRoleMember().user.id)
        assertEquals(UserId("u1"), TeamMemberProfile(pm, profile(id = "other")).toRoleMember().user.id)
    }

    @Test
    fun noReviewsMeansNoRatingAndNegativeFinishedProjectsAreZero() {
        val person = TeamMemberProfile(pm, profile(rating = 5.0, reviews = 0, done = -4)).toRoleMember()

        assertNull(person.rating)
        assertEquals(0, person.completedProjects)
    }

    @Test
    fun withTeamPutsMembersOnTheirRolesAndKeepsOccupied() {
        val base = Project(
            id = ProjectId("p"),
            title = "T",
            type = ProjectType.Scientific,
            sphere = Sphere("1", "CS"),
            problem = "",
            goal = "",
            tags = emptyList(),
            initiator = UserSummary(UserId("o"), "O"),
            roles = listOf(
                Role(RoleId("r-1"), "A", slots = 3, occupied = 2),
                Role(RoleId("r-2"), "B"),
                Role(RoleId("r-3"), "C", occupied = 1),
            ),
            status = ProjectStatus.Recruiting,
            period = ProjectPeriod(kotlinx.datetime.YearMonth(2026, 10), kotlinx.datetime.YearMonth(2026, 11)),
            viewerIsInitiator = false,
        )
        val team = listOf(
            TeamMemberProfile(ProjectMember(UserId("u1"), RoleId("r-1"), "A", false), profile("u1")),
            TeamMemberProfile(ProjectMember(UserId("u2"), RoleId("r-2"), "B", false), null),
            TeamMemberProfile(ProjectMember(UserId("u3"), RoleId("r-1"), "A", false), profile("u3", name = "Борис")),
            TeamMemberProfile(ProjectMember(UserId("u4"), null, "", false), profile("u4")),
            TeamMemberProfile(ProjectMember(UserId("u5"), RoleId("gone"), "", false), profile("u5")),
        )

        val withTeam = base.withTeam(team)

        assertEquals(listOf("u1", "u3"), withTeam.roles[0].members.map { it.user.id.value })
        assertEquals(2, withTeam.roles[0].occupied)
        assertEquals(listOf(false), withTeam.roles[1].members.map { it.hasProfile })
        assertEquals(emptyList(), withTeam.roles[2].members)
        assertEquals(1, withTeam.roles[2].occupied)
        assertEquals(base.copy(roles = emptyList()), withTeam.copy(roles = emptyList()))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DirectoryProjectTeamReaderTest {
    private class Fetcher {
        val calls = mutableListOf<String>()
        val failures = mutableMapOf<String, Throwable>()
        val missing = mutableSetOf<String>()
        var gate: CompletableDeferred<Unit>? = null

        suspend fun fetch(id: String): UserProfileDto {
            calls += id
            gate?.await()
            failures[id]?.let { throw it }
            return UserProfileDto(userId = id, details = UserProfileInputDto(displayName = "name-$id"))
        }
    }

    private fun member(user: String, role: String? = "r-1") =
        ProjectMember(UserId(user), role?.let(::RoleId), "T", false)

    private fun TestScope.reader(fetcher: Fetcher): DirectoryProjectTeamReader {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        return DirectoryProjectTeamReader(UserDirectory(fetcher::fetch, scope))
    }

    @Test
    fun anEmptyListSendsNothing() = runTest {
        val fetcher = Fetcher()

        assertEquals(emptyList(), reader(fetcher).read(emptyList()))
        assertEquals(emptyList(), fetcher.calls)
    }

    @Test
    fun everyDistinctPersonIsAskedForOnceAndTheOrderIsKept() = runTest {
        val fetcher = Fetcher()

        val team = reader(fetcher).read(listOf(member("u2"), member("u1"), member("u2", "r-2"), member("u1", null)))

        assertEquals(listOf("u1", "u2"), fetcher.calls.sorted())
        assertEquals(2, fetcher.calls.size)
        assertEquals(listOf("u2", "u1", "u2", "u1"), team.map { it.member.userId.value })
        assertTrue(team.all { it.profile != null })
    }

    @Test
    fun theFailureOfOnePersonLeavesThemWithoutAProfileAndOnlyThem() = runTest {
        val fetcher = Fetcher()
        fetcher.failures["u2"] = RemoteFailure.Http(500, "boom")
        fetcher.failures["u3"] = RemoteFailure.NoConnection(RuntimeException("offline"))

        val team = reader(fetcher).read(listOf(member("u1"), member("u2"), member("u3"), member("u4")))

        assertEquals(listOf(true, false, false, true), team.map { it.profile != null })
        assertEquals("name-u1", team[0].profile?.details?.displayName)
    }

    @Test
    fun aPersonWithNoProfileAtAllIsAMemberWithoutOne() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val users = UserDirectory({ throw RemoteFailure.Http(404, "no such user") }, scope)

        val team = DirectoryProjectTeamReader(users).read(listOf(member("ghost")))

        assertNull(team.single().profile)
    }

    @Test
    fun cancellationOfTheReaderIsPassedThroughAndNotTurnedIntoMissingProfiles() = runTest {
        val fetcher = Fetcher()
        fetcher.gate = CompletableDeferred()
        val reader = reader(fetcher)
        var thrown: Throwable? = null
        val job = launch {
            try {
                reader.read(listOf(member("u1")))
            } catch (e: CancellationException) {
                thrown = e
                throw e
            }
        }
        runCurrent()

        job.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertTrue(thrown is CancellationException)
    }

    @Test
    fun aCancellationInsideARequestIsNotSwallowedAsAMissingProfile() = runTest {
        val fetcher = Fetcher()
        fetcher.failures["u1"] = CancellationException("cancelled")

        assertFailsWith<CancellationException> { reader(fetcher).read(listOf(member("u1"))) }
    }
}
