package org.matchlab.academic_net.feature.responses.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoinId
import org.matchlab.academic_net.common.model.ProjectJoinStatus
import org.matchlab.academic_net.common.model.ProjectJoinType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.responses.domain.JoinRefusal
import org.matchlab.academic_net.feature.responses.domain.JoinRefusedException
import org.matchlab.academic_net.feature.responses.domain.ProjectResponsesUnavailableException
import org.matchlab.academic_net.gateway.ProjectJoinFilter
import org.matchlab.academic_net.gateway.ProjectJoinsApi
import org.matchlab.academic_net.gateway.UserDirectory
import org.matchlab.academic_net.gateway.dto.ProjectJoinDto
import org.matchlab.academic_net.gateway.dto.ProjectJoinInputDto
import org.matchlab.academic_net.gateway.dto.ProjectJoinListDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

internal fun joinDto(
    id: String,
    project: String = "p1",
    role: String = "r1",
    candidate: String = "u1",
    type: String = "application",
    status: String = "pending",
    message: String = "",
    created: Long = 0,
) = ProjectJoinDto(
    id = id,
    projectId = project,
    roleId = role,
    candidateUserId = candidate,
    type = type,
    status = status,
    message = message,
    createdAtUnix = created,
)

internal fun listDto(total: Long, vararg joins: ProjectJoinDto) = ProjectJoinListDto(joins.toList(), total)

/** `ProjectJoinsApi` scripted call by call; records everything it is asked. */
internal class ScriptedJoinsApi : ProjectJoinsApi {
    var onListMine: suspend (ProjectJoinFilter, PageRequest) -> ProjectJoinListDto =
        { _, _ -> throw AssertionError("Unexpected GET of my joins") }
    var onListForProject: suspend (String, ProjectJoinFilter, PageRequest) -> ProjectJoinListDto =
        { id, _, _ -> throw AssertionError("Unexpected GET of the joins of $id") }
    var onApply: suspend (String, ProjectJoinInputDto) -> ProjectJoinDto = { id, _ -> throw AssertionError("apply $id") }
    var onInvite: suspend (String, ProjectJoinInputDto) -> ProjectJoinDto = { id, _ -> throw AssertionError("invite $id") }
    var onDecide: suspend (String, Boolean) -> ProjectJoinDto = { id, _ -> throw AssertionError("decide $id") }
    var onWithdraw: suspend (String) -> ProjectJoinDto = { id -> throw AssertionError("withdraw $id") }

    val mineRequests = mutableListOf<Pair<ProjectJoinFilter, PageRequest>>()
    val projectRequests = mutableListOf<Triple<String, ProjectJoinFilter, PageRequest>>()
    val applies = mutableListOf<Pair<String, ProjectJoinInputDto>>()
    val invites = mutableListOf<Pair<String, ProjectJoinInputDto>>()
    val decisions = mutableListOf<Pair<String, Boolean>>()
    val withdrawals = mutableListOf<String>()

    /** Every call in order, to check the order of calls of different kinds. */
    val log = mutableListOf<String>()

    override suspend fun apply(projectId: String, input: ProjectJoinInputDto): ProjectJoinDto {
        applies += projectId to input
        log += "apply"
        return onApply(projectId, input)
    }

    override suspend fun invite(projectId: String, input: ProjectJoinInputDto): ProjectJoinDto {
        invites += projectId to input
        log += "invite"
        return onInvite(projectId, input)
    }

    override suspend fun listForProject(projectId: String, filter: ProjectJoinFilter, page: PageRequest): ProjectJoinListDto {
        projectRequests += Triple(projectId, filter, page)
        log += "listForProject"
        return onListForProject(projectId, filter, page)
    }

    override suspend fun listMine(filter: ProjectJoinFilter, page: PageRequest): ProjectJoinListDto {
        mineRequests += filter to page
        log += "listMine"
        return onListMine(filter, page)
    }

    override suspend fun decide(joinId: String, accept: Boolean): ProjectJoinDto {
        decisions += joinId to accept
        log += "decide"
        return onDecide(joinId, accept)
    }

    override suspend fun withdraw(joinId: String): ProjectJoinDto {
        withdrawals += joinId
        log += "withdraw"
        return onWithdraw(joinId)
    }
}

private fun profile(id: String, name: String, skills: List<String> = emptyList(), university: String? = null) =
    UserProfileDto(userId = id, details = UserProfileInputDto(displayName = name, skills = skills, university = university))

@OptIn(ExperimentalCoroutinesApi::class)
class GatewayResponsesDataSourceTest {
    private val api = ScriptedJoinsApi()
    private val profiles = mutableMapOf<String, UserProfileDto>()
    private val failures = mutableMapOf<String, Throwable>()
    private val fetched = mutableListOf<String>()

    private fun TestScope.source(): GatewayResponsesDataSource {
        val users = UserDirectory(
            fetch = { id ->
                fetched += id
                failures[id]?.let { throw it }
                profiles[id] ?: throw RemoteFailure.Http(404)
            },
            scope = backgroundScope,
        )
        return GatewayResponsesDataSource(api, users)
    }

    private val project = ProjectId("p1")

    // ---- reading the viewer's list ----

    @Test
    fun theListIsNullUntilTheFirstReadSucceeds() = runTest {
        val source = source()
        assertNull(source.observeMyJoins().first())

        api.onListMine = { _, _ -> throw RemoteFailure.NoConnection() }
        assertFailsWith<RemoteFailure.NoConnection> { source.refreshMyJoins() }

        assertNull(source.observeMyJoins().first())
    }

    @Test
    fun theWholeListIsReadInPagesOf100WithoutAnyFilter() = runTest {
        api.onListMine = { _, page ->
            val ids = (page.offset until minOf(page.offset + page.limit, 250)).map { joinDto("j$it") }
            ProjectJoinListDto(ids, total = 250)
        }
        val source = source()

        val joins = source.refreshMyJoins()

        assertEquals(250, joins.size)
        assertEquals(listOf(0, 100, 200), api.mineRequests.map { it.second.offset })
        assertEquals(setOf(100), api.mineRequests.map { it.second.limit }.toSet())
        assertEquals(setOf(ProjectJoinFilter()), api.mineRequests.map { it.first }.toSet())
        assertEquals(joins, source.observeMyJoins().first())
        assertEquals("j0", joins.first().id.value)
        assertEquals("j249", joins.last().id.value)
    }

    @Test
    fun aShortListIsOneRequest() = runTest {
        api.onListMine = { _, _ -> listDto(2, joinDto("a"), joinDto("b")) }

        assertEquals(2, source().refreshMyJoins().size)
        assertEquals(1, api.mineRequests.size)
    }

    @Test
    fun aListOfExactlyOnePageIsOneRequest() = runTest {
        api.onListMine = { _, _ -> ProjectJoinListDto((0 until 100).map { joinDto("j$it") }, total = 100) }

        assertEquals(100, source().refreshMyJoins().size)
        assertEquals(1, api.mineRequests.size)
    }

    @Test
    fun anEmptyListIsKnownAndEmpty() = runTest {
        api.onListMine = { _, _ -> listDto(0) }
        val source = source()

        assertEquals(emptyList(), source.refreshMyJoins())
        assertEquals(emptyList(), source.observeMyJoins().first())
    }

    @Test
    fun atMostTenPagesAreReadWhateverTheServerSays() = runTest {
        api.onListMine = { _, page ->
            ProjectJoinListDto((page.offset until page.offset + page.limit).map { joinDto("j$it") }, total = 100_000)
        }

        val joins = source().refreshMyJoins()

        assertEquals(10, api.mineRequests.size)
        assertEquals(1000, joins.size)
    }

    @Test
    fun aRepeatedIdIsDroppedAndTheFirstStays() = runTest {
        api.onListMine = { _, page ->
            if (page.offset == 0) {
                ProjectJoinListDto((0 until 100).map { joinDto("j$it", message = "first") }, total = 150)
            } else {
                ProjectJoinListDto((90 until 140).map { joinDto("j$it", message = "second") }, total = 150)
            }
        }

        val joins = source().refreshMyJoins()

        assertEquals(140, joins.size)
        assertEquals(joins.map { it.id.value }.distinct().size, joins.size)
        assertEquals("first", joins.first { it.id.value == "j95" }.message)
        assertEquals("second", joins.first { it.id.value == "j120" }.message)
    }

    @Test
    fun anUnreadableRecordIsSkippedButStillCountsForThePaging() = runTest {
        api.onListMine = { _, page ->
            if (page.offset == 0) {
                ProjectJoinListDto(listOf(joinDto("a"), joinDto(""), joinDto("b", project = "")) + (0 until 97).map { joinDto("x$it") }, total = 101)
            } else {
                listDto(101, joinDto("last"))
            }
        }

        val joins = source().refreshMyJoins()

        assertEquals(2, api.mineRequests.size, "100 records were received on the first page, so the second is asked")
        assertEquals(1 + 97 + 1, joins.size)
        assertEquals("last", joins.last().id.value)
    }

    @Test
    fun aFailureInTheMiddleKeepsTheKnownList() = runTest {
        val source = source()
        api.onListMine = { _, _ -> listDto(1, joinDto("old")) }
        source.refreshMyJoins()
        api.onListMine = { _, page ->
            if (page.offset == 0) {
                ProjectJoinListDto((0 until 100).map { joinDto("n$it") }, total = 200)
            } else {
                throw RemoteFailure.Http(500)
            }
        }

        assertFailsWith<RemoteFailure.Http> { source.refreshMyJoins() }

        assertEquals(listOf("old"), source.observeMyJoins().first()?.map { it.id.value })
    }

    @Test
    fun unknownCodesAreKeptAsUnknownNotDropped() = runTest {
        api.onListMine = { _, _ -> listDto(3, joinDto("a", type = "9", status = "zzz"), joinDto("b", type = " invitation ", status = " rejected "), joinDto("c", type = "1", status = "2")) }

        val joins = source().refreshMyJoins()

        assertNull(joins[0].type)
        assertNull(joins[0].status)
        assertEquals(ProjectJoinType.Invitation, joins[1].type)
        assertEquals(ProjectJoinStatus.Rejected, joins[1].status)
        assertNull(joins[2].type, "the old numeric codes are not read")
        assertNull(joins[2].status)
    }

    @Test
    fun aCancellationPassesThroughAndTheLockIsFree() = runTest {
        val source = source()
        api.onListMine = { _, _ -> throw CancellationException("c") }
        assertFailsWith<CancellationException> { source.refreshMyJoins() }

        api.onListMine = { _, _ -> listDto(1, joinDto("a")) }
        assertEquals(1, source.refreshMyJoins().size)
    }

    // ---- apply ----

    @Test
    fun anApplicationSendsTheProjectTheRoleAndTheMessage() = runTest {
        api.onApply = { _, _ -> joinDto("new") }

        source().apply(project, RoleId("r7"), "Привет")

        assertEquals(
            listOf("p1" to ProjectJoinInputDto(projectId = "p1", roleId = "r7", candidateUserId = null, message = "Привет")),
            api.applies,
        )
    }

    @Test
    fun anApplicationWithoutAMessageSendsNoMessage() = runTest {
        api.onApply = { _, _ -> joinDto("new") }

        source().apply(project, RoleId("r7"), null)

        assertNull(api.applies.single().second.message)
    }

    @Test
    fun anApplicationBeforeTheFirstReadReadsTheListAndTheListIsTheServers() = runTest {
        api.onApply = { _, _ -> joinDto("new", role = "r7") }
        api.onListMine = { _, _ -> listDto(2, joinDto("new", role = "r7"), joinDto("old")) }
        val source = source()

        val join = source.apply(project, RoleId("r7"), null)

        assertEquals("new", join.id.value)
        assertEquals(listOf("new", "old"), source.observeMyJoins().first()?.map { it.id.value })
        assertEquals(listOf("apply", "listMine"), api.log)
    }

    @Test
    fun anApplicationBeforeTheFirstReadDoesNotMakeTheListKnownFromOneRecordWhenTheReadFails() = runTest {
        api.onApply = { _, _ -> joinDto("new", role = "r7") }
        api.onListMine = { _, _ -> throw RemoteFailure.NoConnection() }
        val source = source()

        val join = source.apply(project, RoleId("r7"), null)

        assertEquals("new", join.id.value, "the application itself went through")
        assertNull(source.observeMyJoins().first(), "the list stays unknown")
    }

    @Test
    fun aConflictOfAnApplicationReadsTheListBestEffortAndThenThrowsTheConflict() = runTest {
        api.onApply = { _, _ -> throw RemoteFailure.Http(409) }
        api.onListMine = { _, _ -> listDto(1, joinDto("already", role = "r7")) }
        val source = source()

        val thrown = assertFailsWith<JoinRefusedException> { source.apply(project, RoleId("r7"), null) }

        assertEquals(JoinRefusal.Conflict, thrown.refusal)
        assertEquals(listOf("already"), source.observeMyJoins().first()?.map { it.id.value })
        assertEquals(listOf("apply", "listMine"), api.log)
    }

    @Test
    fun aConflictStaysAConflictWhenTheBestEffortReadFails() = runTest {
        api.onApply = { _, _ -> throw RemoteFailure.Http(409) }
        api.onListMine = { _, _ -> throw RemoteFailure.Http(500) }

        val thrown = assertFailsWith<JoinRefusedException> { source().apply(project, RoleId("r7"), null) }

        assertEquals(JoinRefusal.Conflict, thrown.refusal)
    }

    @Test
    fun otherRefusalsOfAnApplicationReadNothing() = runTest {
        for (status in listOf(403, 404)) {
            api.log.clear()
            api.onApply = { _, _ -> throw RemoteFailure.Http(status) }

            assertFailsWith<JoinRefusedException> { source().apply(project, RoleId("r7"), null) }

            assertEquals(listOf("apply"), api.log)
        }
    }

    @Test
    fun anApplicationIsAddedInFrontOfTheKnownListAndReplacesItsOwnId() = runTest {
        val source = source()
        api.onListMine = { _, _ -> listDto(2, joinDto("a"), joinDto("b")) }
        source.refreshMyJoins()
        api.onApply = { _, _ -> joinDto("b", status = "pending", message = "новый") }

        source.apply(project, RoleId("r1"), null)

        assertEquals(listOf("b", "a"), source.observeMyJoins().first()?.map { it.id.value })
        assertEquals("новый", source.observeMyJoins().first()?.first()?.message)
    }

    @Test
    fun anApplicationKeepsAnUnknownStatusOfTheAnswer() = runTest {
        api.onApply = { _, _ -> joinDto("new", status = "77") }
        assertNull(source().apply(project, RoleId("r1"), null).status)
    }

    // ---- refusals and unreadable answers ----

    @Test
    fun a403404And409AreRefusalsOfEveryAction() = runTest {
        val expected = mapOf(403 to JoinRefusal.NotAllowed, 404 to JoinRefusal.NotFound, 409 to JoinRefusal.Conflict)
        val source = source()
        for ((status, refusal) in expected) {
            val failure = RemoteFailure.Http(status)
            api.onApply = { _, _ -> throw failure }
            api.onInvite = { _, _ -> throw failure }
            api.onDecide = { _, _ -> throw failure }
            api.onWithdraw = { throw failure }

            val thrown = listOf(
                assertFailsWith<JoinRefusedException> { source.apply(project, RoleId("r"), null) },
                assertFailsWith<JoinRefusedException> { source.invite(project, RoleId("r"), UserId("u"), null) },
                assertFailsWith<JoinRefusedException> { source.decide(ProjectJoinId("j"), true) },
                assertFailsWith<JoinRefusedException> { source.withdraw(ProjectJoinId("j")) },
            )

            assertEquals(List(4) { refusal }, thrown.map { it.refusal }, "status $status")
        }
    }

    @Test
    fun otherFailuresPassAsTheyAre() = runTest {
        val source = source()
        for (failure in listOf(RemoteFailure.Http(400), RemoteFailure.Http(401), RemoteFailure.Http(500), RemoteFailure.NoConnection(), RemoteFailure.Timeout())) {
            api.onApply = { _, _ -> throw failure }
            api.onDecide = { _, _ -> throw failure }
            api.onWithdraw = { throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure> { source.apply(project, RoleId("r"), null) })
            assertSame(failure, assertFailsWith<RemoteFailure> { source.decide(ProjectJoinId("j"), true) })
            assertSame(failure, assertFailsWith<RemoteFailure> { source.withdraw(ProjectJoinId("j")) })
        }
    }

    @Test
    fun anAnswerThatIsNotARecordIsMalformed() = runTest {
        val source = source()
        api.onApply = { _, _ -> ProjectJoinDto() }
        api.onInvite = { _, _ -> joinDto("j", project = "") }
        api.onDecide = { _, _ -> joinDto("j", role = "") }
        api.onWithdraw = { joinDto("") }

        assertIs<RemoteFailure.Malformed>(assertFailsWith<RemoteFailure> { source.apply(project, RoleId("r"), null) })
        assertIs<RemoteFailure.Malformed>(assertFailsWith<RemoteFailure> { source.invite(project, RoleId("r"), UserId("u"), null) })
        assertIs<RemoteFailure.Malformed>(assertFailsWith<RemoteFailure> { source.decide(ProjectJoinId("j"), true) })
        assertIs<RemoteFailure.Malformed>(assertFailsWith<RemoteFailure> { source.withdraw(ProjectJoinId("j")) })
        assertNull(source.observeMyJoins().first(), "an unreadable answer changes no cache")
    }

    // ---- decide / withdraw ----

    @Test
    fun anUnknownStatusInTheAnswerIsTheStatusOfTheAction() = runTest {
        val source = source()
        api.onDecide = { _, _ -> joinDto("j", status = "99") }
        api.onWithdraw = { joinDto("j", status = "") }

        assertEquals(ProjectJoinStatus.Accepted, source.decide(ProjectJoinId("j"), accept = true).status)
        assertEquals(ProjectJoinStatus.Rejected, source.decide(ProjectJoinId("j"), accept = false).status)
        assertEquals(ProjectJoinStatus.Cancelled, source.withdraw(ProjectJoinId("j")).status)
        assertEquals(listOf("j" to true, "j" to false), api.decisions)
        assertEquals(listOf("j"), api.withdrawals)
    }

    @Test
    fun aKnownStatusInTheAnswerIsKeptAsTheServerSaid() = runTest {
        api.onDecide = { _, _ -> joinDto("j", status = "rejected") }

        assertEquals(ProjectJoinStatus.Rejected, source().decide(ProjectJoinId("j"), accept = true).status)
    }

    @Test
    fun aDecisionReplacesTheRecordInBothCachesAndKeepsTheCandidate() = runTest {
        val source = source()
        profiles["u1"] = profile("u1", "Анна", skills = listOf("Kotlin"))
        api.onListMine = { _, _ -> listDto(2, joinDto("j"), joinDto("other")) }
        api.onListForProject = { _, _, _ -> listDto(2, joinDto("j"), joinDto("k", candidate = "u2")) }
        source.refreshMyJoins()
        source.refreshProjectResponses(project)
        api.onDecide = { _, _ -> joinDto("j", status = "accepted") }

        source.decide(ProjectJoinId("j"), accept = true)

        val mine = source.observeMyJoins().first().orEmpty()
        assertEquals(listOf(ProjectJoinStatus.Accepted, ProjectJoinStatus.Pending), mine.map { it.status })
        val responses = assertNotNull(source.observeProjectResponses(project).first())
        assertEquals(ProjectJoinStatus.Accepted, responses[0].join.status)
        assertEquals(ProjectJoinStatus.Pending, responses[1].join.status)
        assertEquals("Анна", responses[0].candidate?.person?.user?.name)
        assertEquals(listOf("Kotlin"), responses[0].candidate?.skills)
    }

    @Test
    fun aDecisionBeforeAnyReadTouchesNoCache() = runTest {
        val source = source()
        api.onDecide = { _, _ -> joinDto("j", status = "accepted") }

        source.decide(ProjectJoinId("j"), true)

        assertNull(source.observeMyJoins().first())
        assertNull(source.observeProjectResponses(project).first())
    }

    @Test
    fun aWithdrawalReplacesTheRecordOfTheViewerList() = runTest {
        val source = source()
        api.onListMine = { _, _ -> listDto(1, joinDto("j")) }
        source.refreshMyJoins()
        api.onWithdraw = { joinDto("j", status = "cancelled") }

        source.withdraw(ProjectJoinId("j/1"))

        assertEquals(listOf("j/1"), api.withdrawals)
        assertEquals(ProjectJoinStatus.Cancelled, source.observeMyJoins().first()?.single()?.status)
    }

    // ---- invite ----

    @Test
    fun anInvitationSendsTheCandidateAndLeavesTheCachesAlone() = runTest {
        val source = source()
        api.onListForProject = { _, _, _ -> listDto(0) }
        source.refreshProjectResponses(project)
        api.onInvite = { _, _ -> joinDto("inv", type = "invitation") }

        val join = source.invite(project, RoleId("r7"), UserId("u9"), "Присоединяйся")

        assertEquals(ProjectJoinType.Invitation, join.type)
        assertEquals(
            listOf("p1" to ProjectJoinInputDto(projectId = "p1", roleId = "r7", candidateUserId = "u9", message = "Присоединяйся")),
            api.invites,
        )
        assertEquals(emptyList(), source.observeProjectResponses(project).first())
        assertNull(source.observeMyJoins().first())
    }

    // ---- the list of a project and its candidates ----

    @Test
    fun theListOfAProjectIsNullUntilReadAndPerProject() = runTest {
        val source = source()
        api.onListForProject = { id, _, _ -> listDto(1, joinDto("j", project = id)) }

        source.refreshProjectResponses(project)

        assertNotNull(source.observeProjectResponses(project).first())
        assertNull(source.observeProjectResponses(ProjectId("p2")).first())
    }

    @Test
    fun theListOfAProjectIsReadWholeWithoutFiltersAndPaged() = runTest {
        api.onListForProject = { _, _, page ->
            ProjectJoinListDto((page.offset until minOf(page.offset + 100, 130)).map { joinDto("j$it", candidate = "") }, total = 130)
        }

        val responses = source().refreshProjectResponses(ProjectId("p/1"))

        assertEquals(130, responses.size)
        assertEquals(listOf(0, 100), api.projectRequests.map { it.third.offset })
        assertEquals(setOf("p/1"), api.projectRequests.map { it.first }.toSet())
        assertEquals(setOf(ProjectJoinFilter()), api.projectRequests.map { it.second }.toSet())
    }

    @Test
    fun everyDistinctCandidateIsAskedOnce() = runTest {
        profiles["u1"] = profile("u1", "Анна")
        profiles["u2"] = profile("u2", "Борис")
        api.onListForProject = { _, _, _ ->
            listDto(3, joinDto("a", candidate = "u1"), joinDto("b", candidate = "u2"), joinDto("c", candidate = "u1", role = "r2"))
        }

        val responses = source().refreshProjectResponses(project)

        assertEquals(listOf("u1", "u2"), fetched.sorted())
        assertEquals(listOf("Анна", "Борис", "Анна"), responses.map { it.candidate?.person?.user?.name })
    }

    @Test
    fun aCandidateWithAProfileHasItsDataAndTheIdOfTheRecord() = runTest {
        profiles["u1"] = profile(id = "", name = "Анна", skills = listOf("Kotlin", "SQL"), university = " МГУ ")
        api.onListForProject = { _, _, _ -> listDto(1, joinDto("a", candidate = "u1")) }

        val candidate = assertNotNull(source().refreshProjectResponses(project).single().candidate)

        assertTrue(candidate.hasProfile)
        assertEquals(UserId("u1"), candidate.person.user.id)
        assertEquals("МГУ", candidate.person.university)
        assertEquals(listOf("Kotlin", "SQL"), candidate.skills)
    }

    @Test
    fun aCandidateWhoseProfileFailsOrIsMissingHasNoProfileAndTheRestStillLoad() = runTest {
        profiles["ok"] = profile("ok", "Анна")
        failures["boom"] = RemoteFailure.Http(500)
        failures["offline"] = RemoteFailure.NoConnection()
        api.onListForProject = { _, _, _ ->
            listDto(4, joinDto("a", candidate = "ok"), joinDto("b", candidate = "boom"), joinDto("c", candidate = "offline"), joinDto("d", candidate = "gone"))
        }

        val responses = source().refreshProjectResponses(project)

        assertEquals(listOf(true, false, false, false), responses.map { it.candidate?.hasProfile })
        val without = assertNotNull(responses[1].candidate)
        assertEquals(UserId("boom"), without.person.user.id)
        assertEquals("", without.person.user.name)
        assertEquals(emptyList(), without.skills)
    }

    @Test
    fun aRecordWithoutACandidateIdHasNoCandidateAndAsksNoProfile() = runTest {
        api.onListForProject = { _, _, _ -> listDto(1, joinDto("a", candidate = "")) }

        val responses = source().refreshProjectResponses(project)

        assertNull(responses.single().candidate)
        assertTrue(fetched.isEmpty())
    }

    @Test
    fun a403Or404OfTheListIsUnavailableAndTheCacheStaysEmpty() = runTest {
        val source = source()
        for (status in listOf(403, 404)) {
            api.onListForProject = { _, _, _ -> throw RemoteFailure.Http(status) }

            val thrown = assertFailsWith<ProjectResponsesUnavailableException> { source.refreshProjectResponses(project) }

            assertEquals(project, thrown.projectId)
        }
        assertNull(source.observeProjectResponses(project).first())
    }

    @Test
    fun otherFailuresOfTheListPassAndKeepTheKnownRecords() = runTest {
        val source = source()
        api.onListForProject = { _, _, _ -> listDto(1, joinDto("a")) }
        source.refreshProjectResponses(project)

        for (failure in listOf(RemoteFailure.Http(500), RemoteFailure.Http(409), RemoteFailure.NoConnection())) {
            api.onListForProject = { _, _, _ -> throw failure }
            assertSame(failure, assertFailsWith<RemoteFailure> { source.refreshProjectResponses(project) })
        }
        assertEquals(1, source.observeProjectResponses(project).first()?.size)
    }

    // ---- order of the writes ----

    @Test
    fun anOldReadNeverOverwritesADecisionMadeWhileItRan() = runTest {
        val source = source()
        val gate = CompletableDeferred<Unit>()
        api.onListMine = { _, _ ->
            val old = listDto(1, joinDto("j", status = "pending"))
            gate.await()
            old
        }
        api.onDecide = { _, _ -> joinDto("j", status = "rejected") }

        val read = async { source.refreshMyJoins() }
        runCurrent()
        val decision = async { source.decide(ProjectJoinId("j"), accept = false) }
        runCurrent()
        assertEquals(emptyList(), api.decisions, "the decision waits for the read")

        gate.complete(Unit)
        read.await()
        decision.await()

        assertEquals(ProjectJoinStatus.Rejected, source.observeMyJoins().first()?.single()?.status)
        assertEquals(listOf("listMine", "decide"), api.log)
    }

    @Test
    fun twoActionsRunOneAfterTheOther() = runTest {
        val source = source()
        val gate = CompletableDeferred<Unit>()
        api.onApply = { _, _ ->
            gate.await()
            joinDto("a")
        }
        api.onWithdraw = { joinDto("b", status = "cancelled") }

        val first = async { source.apply(project, RoleId("r1"), null) }
        runCurrent()
        val second = async { source.withdraw(ProjectJoinId("b")) }
        runCurrent()
        assertEquals(emptyList(), api.withdrawals)

        gate.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf("apply", "listMine", "withdraw"), api.log, "the failed best-effort read of the list is logged too")
    }

    // ---- the time of the last look ----

    @Test
    fun theSeenTimeOnlyMovesForward() = runTest {
        val source = source()
        assertNull(source.observeInvitationsSeenAt().first())

        source.markInvitationsSeen(Instant.fromEpochSeconds(100))
        source.markInvitationsSeen(Instant.fromEpochSeconds(50))
        assertEquals(Instant.fromEpochSeconds(100), source.observeInvitationsSeenAt().first())

        source.markInvitationsSeen(Instant.fromEpochSeconds(100))
        source.markInvitationsSeen(Instant.fromEpochSeconds(200))
        assertEquals(Instant.fromEpochSeconds(200), source.observeInvitationsSeenAt().first())
    }

    @Test
    fun theRepositoryServesBothSidesOverOneSource() = runTest {
        val repository = ResponsesRepositoryImpl(source())
        api.onListMine = { _, _ -> listDto(1, joinDto("j")) }
        api.onListForProject = { _, _, _ -> listDto(1, joinDto("j", candidate = "")) }
        api.onDecide = { _, _ -> joinDto("j", status = "accepted") }

        repository.refreshMyJoins()
        repository.refreshProjectResponses(project)
        repository.decide(ProjectJoinId("j"), true)
        repository.markInvitationsSeen(Instant.fromEpochSeconds(5))

        assertEquals(ProjectJoinStatus.Accepted, repository.observeMyJoins().first()?.single()?.status)
        assertEquals(ProjectJoinStatus.Accepted, repository.observeProjectResponses(project).first()?.single()?.join?.status)
        assertEquals(Instant.fromEpochSeconds(5), repository.observeInvitationsSeenAt().first())
        assertFalse(api.log.isEmpty())
    }
}
