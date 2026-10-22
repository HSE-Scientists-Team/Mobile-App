package org.matchlab.academic_net

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoin
import org.matchlab.academic_net.common.model.ProjectJoinId
import org.matchlab.academic_net.common.model.ProjectJoinStatus
import org.matchlab.academic_net.common.model.ProjectJoinType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.responses.domain.MyJoinsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private fun join(
    id: String,
    project: String = "p1",
    role: String = "r1",
    type: ProjectJoinType? = ProjectJoinType.Application,
    status: ProjectJoinStatus? = ProjectJoinStatus.Pending,
) = ProjectJoin(ProjectJoinId(id), ProjectId(project), RoleId(role), null, type, status, "", null)

private class FakeMyJoins(initial: List<ProjectJoin>? = null) : MyJoinsRepository {
    val joins = MutableStateFlow(initial)
    var refreshed: List<ProjectJoin> = initial.orEmpty()
    var refreshFailure: Throwable? = null
    var refreshes = 0

    override fun observeMyJoins(): Flow<List<ProjectJoin>?> = joins

    override suspend fun refreshMyJoins(): List<ProjectJoin> {
        refreshes++
        refreshFailure?.let { throw it }
        return refreshed
    }

    override suspend fun apply(projectId: ProjectId, roleId: RoleId, message: String?): ProjectJoin = throw AssertionError()
    override suspend fun withdraw(id: ProjectJoinId): ProjectJoin = throw AssertionError()
    override suspend fun decide(id: ProjectJoinId, accept: Boolean): ProjectJoin = throw AssertionError()
    override fun observeInvitationsSeenAt(): Flow<Instant?> = throw AssertionError()
    override fun markInvitationsSeen(upTo: Instant) = throw AssertionError()
}

/** The adapters that connect the responses to the projects and to the project page. */
class ResponsesAdaptersTest {
    private val signedIn = Session(UserId("me"), "me@hse.ru", AccessToken("token"))

    @Test
    fun aGuestHasNoAppliedRolesAndTheListOfApplicationsIsNotAsked() = runTest {
        val joins = FakeMyJoins(listOf(join("a", "p1", "r1", status = ProjectJoinStatus.Pending)))

        val roles = ResponsesAppliedRolesSource(joins, Session.Guest).observeAppliedRoles(ProjectId("p1")).first()

        assertEquals(emptySet(), roles)
    }

    // ---- ResponsesAppliedRolesSource ----

    @Test
    fun theAppliedRolesAreThoseOfWaitingApplicationsToTheProject() = runTest {
        val joins = FakeMyJoins(
            listOf(
                join("a", "p1", "r1", status = ProjectJoinStatus.Pending),
                join("b", "p1", "r2", status = null),
                join("c", "p1", "r3", status = ProjectJoinStatus.Accepted),
                join("d", "p1", "r4", status = ProjectJoinStatus.Rejected),
                join("e", "p1", "r5", status = ProjectJoinStatus.Cancelled),
                join("f", "p1", "r6", type = ProjectJoinType.Invitation),
                join("g", "p1", "r7", type = null),
                join("h", "p2", "r8"),
            ),
        )

        val roles = ResponsesAppliedRolesSource(joins, signedIn).observeAppliedRoles(ProjectId("p1")).first()

        assertEquals(setOf(RoleId("r1"), RoleId("r2")), roles)
    }

    @Test
    fun theAppliedRolesAreEmptyWhileTheListIsUnknownAndFollowItWithoutRepeats() = runTest {
        val joins = FakeMyJoins(null)
        val seen = mutableListOf<Set<RoleId>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            ResponsesAppliedRolesSource(joins, signedIn).observeAppliedRoles(ProjectId("p1")).toList(seen)
        }

        joins.joins.value = listOf(join("a"))
        joins.joins.value = listOf(join("a"), join("other", "p2"))
        joins.joins.value = listOf(join("a", status = ProjectJoinStatus.Cancelled))

        assertEquals(listOf(emptySet(), setOf(RoleId("r1")), emptySet()), seen)
    }
}
