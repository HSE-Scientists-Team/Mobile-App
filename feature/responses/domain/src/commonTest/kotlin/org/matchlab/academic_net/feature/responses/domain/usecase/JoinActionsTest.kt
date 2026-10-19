package org.matchlab.academic_net.feature.responses.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectJoinStatus
import org.matchlab.academic_net.common.model.ProjectJoinType
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.responses.domain.FakeMyJoins
import org.matchlab.academic_net.feature.responses.domain.FakeProjectJoins
import org.matchlab.academic_net.feature.responses.domain.FakeProjectSource
import org.matchlab.academic_net.feature.responses.domain.JoinRefusal
import org.matchlab.academic_net.feature.responses.domain.JoinRefusedException
import org.matchlab.academic_net.feature.responses.domain.join
import org.matchlab.academic_net.feature.responses.domain.project
import org.matchlab.academic_net.feature.responses.domain.role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WithdrawApplicationUseCaseTest {
    private val joins = FakeMyJoins(listOf(join()))
    private val withdraw = WithdrawApplicationUseCase(joins)

    @Test
    fun aWithdrawnApplicationComesBackAsTheRecordOfTheRepository() = runTest {
        joins.result = join(status = ProjectJoinStatus.Cancelled)

        val result = assertIs<JoinActionResult.Done>(withdraw(join()))

        assertEquals(ProjectJoinStatus.Cancelled, result.join.status)
        assertEquals(listOf("withdraw j1"), joins.calls)
    }

    @Test
    fun everyRefusalIsRefusedAndTheListIsReadAgain() = runTest {
        for (refusal in JoinRefusal.entries) {
            joins.calls.clear()
            joins.failure = JoinRefusedException(refusal)

            assertEquals(JoinActionResult.Refused(refusal), withdraw(join()))
            assertEquals(listOf("withdraw j1", "refresh"), joins.calls)
        }
    }

    @Test
    fun aFailedReadAfterARefusalDoesNotChangeTheOutcome() = runTest {
        joins.failure = JoinRefusedException(JoinRefusal.Conflict)
        joins.refreshFailure = RemoteFailure.NoConnection()

        assertEquals(JoinActionResult.Refused(JoinRefusal.Conflict), withdraw(join()))
    }

    @Test
    fun otherFailuresAreFailuresAndTheListIsNotRead() = runTest {
        val cause = RemoteFailure.Http(500)
        joins.failure = cause

        assertSame(cause, assertIs<JoinActionResult.Failure>(withdraw(join())).cause)
        assertEquals(0, joins.refreshes)
    }

    @Test
    fun aCancellationPassesThrough() = runTest {
        joins.failure = CancellationException("cancelled")
        assertFailsWith<CancellationException> { withdraw(join()) }
    }
}

class DecideInvitationUseCaseTest {
    private val joins = FakeMyJoins(listOf(join(type = ProjectJoinType.Invitation)))
    private val projects = FakeProjectSource(listOf(project()))
    private val decide = DecideInvitationUseCase(joins, projects)
    private val invitation = join(type = ProjectJoinType.Invitation)

    @Test
    fun acceptingSendsTheDecisionAndReadsTheProjectAgain() = runTest {
        joins.result = join(type = ProjectJoinType.Invitation, status = ProjectJoinStatus.Accepted)

        val result = assertIs<JoinActionResult.Done>(decide(invitation, accept = true))

        assertEquals(ProjectJoinStatus.Accepted, result.join.status)
        assertEquals(listOf("decide j1 true"), joins.calls)
        assertEquals(listOf(ProjectId("p1")), projects.refreshed)
    }

    @Test
    fun decliningReadsTheProjectAgainToo() = runTest {
        assertIs<JoinActionResult.Done>(decide(invitation, accept = false))

        assertEquals(listOf("decide j1 false"), joins.calls)
        assertEquals(listOf(ProjectId("p1")), projects.refreshed, "the counters of the project changed")
    }

    @Test
    fun aRefusalReadsTheListAndNotTheProject() = runTest {
        joins.failure = JoinRefusedException(JoinRefusal.NotFound)

        assertEquals(JoinActionResult.Refused(JoinRefusal.NotFound), decide(invitation, accept = true))
        assertEquals(listOf("decide j1 true", "refresh"), joins.calls)
        assertTrue(projects.refreshed.isEmpty())
    }

    @Test
    fun aFailureIsAFailure() = runTest {
        val cause = RemoteFailure.Timeout()
        joins.failure = cause

        assertSame(cause, assertIs<JoinActionResult.Failure>(decide(invitation, accept = true)).cause)
        assertTrue(projects.refreshed.isEmpty())
    }
}

class DecideApplicationUseCaseTest {
    private val joins = FakeProjectJoins()
    private val projects = FakeProjectSource(listOf(project(roles = listOf(role("r1", slots = 1, occupied = 0), role("full", 1, 1)))))
    private val decide = DecideApplicationUseCase(joins, projects)

    @Test
    fun acceptingIntoAFullRoleSendsNothing() = runTest {
        assertEquals(JoinActionResult.RoleFull, decide(join(roleId = "full"), accept = true))

        assertTrue(joins.calls.isEmpty())
        assertTrue(projects.refreshed.isEmpty())
    }

    @Test
    fun decliningIntoAFullRoleIsAllowed() = runTest {
        joins.result = join(roleId = "full", status = ProjectJoinStatus.Rejected)

        assertIs<JoinActionResult.Done>(decide(join(roleId = "full"), accept = false))

        assertEquals(listOf("decide j1 false"), joins.calls)
        assertTrue(projects.getCalls.isEmpty(), "the project is not asked when declining")
        assertEquals(listOf(ProjectId("p1")), projects.refreshed, "but the project is read again: pendingJoinCount changed")
    }

    @Test
    fun acceptingIntoAVacantRoleSendsAndReadsTheProjectAgain() = runTest {
        joins.result = join(status = ProjectJoinStatus.Accepted)

        val result = assertIs<JoinActionResult.Done>(decide(join(), accept = true))

        assertEquals(ProjectJoinStatus.Accepted, result.join.status)
        assertEquals(listOf("decide j1 true"), joins.calls)
        assertEquals(listOf(ProjectId("p1")), projects.refreshed)
    }

    @Test
    fun aRoleTheProjectNoLongerHasDoesNotBlockTheRequest() = runTest {
        assertIs<JoinActionResult.Done>(decide(join(roleId = "ghost"), accept = true))
        assertEquals(listOf("decide j1 true"), joins.calls)
    }

    @Test
    fun aProjectTheViewerCannotSeeDoesNotBlockTheRequest() = runTest {
        assertIs<JoinActionResult.Done>(decide(join(projectId = "other"), accept = true))
        assertEquals(listOf("decide j1 true"), joins.calls)
    }

    @Test
    fun aFailureToReadTheProjectIsAFailureWithoutARequest() = runTest {
        val cause = RemoteFailure.NoConnection()
        projects.getFailures[ProjectId("p1")] = cause

        assertSame(cause, assertIs<JoinActionResult.Failure>(decide(join(), accept = true)).cause)
        assertTrue(joins.calls.isEmpty())
    }

    @Test
    fun aRefusalReadsTheRecordsOfTheProjectAgain() = runTest {
        joins.failure = JoinRefusedException(JoinRefusal.Conflict)

        assertEquals(JoinActionResult.Refused(JoinRefusal.Conflict), decide(join(), accept = true))
        assertEquals(listOf("decide j1 true", "refresh p1"), joins.calls)
        assertTrue(projects.refreshed.isEmpty())
    }

    @Test
    fun aFailureOfTheRereadingAfterARefusalIsIgnored() = runTest {
        joins.failure = JoinRefusedException(JoinRefusal.NotAllowed)
        joins.refreshFailure = RemoteFailure.Http(500)

        assertEquals(JoinActionResult.Refused(JoinRefusal.NotAllowed), decide(join(), accept = false))
    }

    @Test
    fun otherFailuresAreFailures() = runTest {
        val cause = RemoteFailure.Http(500)
        joins.failure = cause

        assertSame(cause, assertIs<JoinActionResult.Failure>(decide(join(), accept = false)).cause)
        assertEquals(listOf("decide j1 false"), joins.calls)
    }

    @Test
    fun aCancellationPassesThrough() = runTest {
        joins.failure = CancellationException("cancelled")
        assertFailsWith<CancellationException> { decide(join(), accept = false) }
    }

    @Test
    fun aStatusOfTheProjectDoesNotMatterForTheRoleCheck() = runTest {
        val closed = FakeProjectSource(listOf(project(status = ProjectStatus.Started, roles = listOf(role("r1", 1, 0)))))

        assertIs<JoinActionResult.Done>(DecideApplicationUseCase(joins, closed)(join(), accept = true))
    }
}
