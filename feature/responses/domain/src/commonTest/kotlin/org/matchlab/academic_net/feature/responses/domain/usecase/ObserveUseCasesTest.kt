package org.matchlab.academic_net.feature.responses.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoinStatus
import org.matchlab.academic_net.common.model.ProjectJoinType
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.responses.domain.FakeMyJoins
import org.matchlab.academic_net.feature.responses.domain.FakeProjectJoins
import org.matchlab.academic_net.feature.responses.domain.FakeProjectSource
import org.matchlab.academic_net.feature.responses.domain.ProjectResponse
import org.matchlab.academic_net.feature.responses.domain.at
import org.matchlab.academic_net.feature.responses.domain.candidate
import org.matchlab.academic_net.feature.responses.domain.join
import org.matchlab.academic_net.feature.responses.domain.project
import org.matchlab.academic_net.feature.responses.domain.role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObserveMyResponsesUseCaseTest {
    private val joins = FakeMyJoins()

    @Test
    fun nothingReadYetIsNull() = runTest {
        assertNull(ObserveMyResponsesUseCase(joins, FakeProjectSource())().first())
    }

    @Test
    fun everyDistinctProjectIsAskedOnce() = runTest {
        joins.joins.value = listOf(join("a", "p1", "r1"), join("b", "p1", "r2"), join("c", "p2", "r1"))
        val projects = FakeProjectSource(
            listOf(project("p1", listOf(role("r1"), role("r2"))), project("p2", listOf(role("r1")))),
        )

        val list = assertNotNull(ObserveMyResponsesUseCase(joins, projects)().first())

        assertEquals(listOf(ProjectId("p1"), ProjectId("p2")), projects.getCalls.sortedBy { it.value })
        assertEquals(listOf("a", "b", "c"), list.map { it.join.id.value })
        assertEquals(listOf("Role r1", "Role r2", "Role r1"), list.map { it.role?.title })
    }

    @Test
    fun aRecordWhoseProjectIsNotVisibleDisappears() = runTest {
        joins.joins.value = listOf(join("a", "p1"), join("b", "hidden"))

        val list = assertNotNull(ObserveMyResponsesUseCase(joins, FakeProjectSource(listOf(project("p1"))))().first())

        assertEquals(listOf("a"), list.map { it.join.id.value })
    }

    @Test
    fun aRoleTheProjectNoLongerHasIsNullButTheRecordStays() = runTest {
        joins.joins.value = listOf(join("a", "p1", "gone"))

        val list = assertNotNull(ObserveMyResponsesUseCase(joins, FakeProjectSource(listOf(project("p1"))))().first())

        assertEquals(1, list.size)
        assertNull(list.single().role)
    }

    @Test
    fun aFailureOfAProjectReadFailsTheFlow() = runTest {
        joins.joins.value = listOf(join("a", "p1"), join("b", "p2"))
        val projects = FakeProjectSource(listOf(project("p1"), project("p2")))
        projects.getFailures[ProjectId("p2")] = RemoteFailure.NoConnection()

        assertFailsWith<RemoteFailure.NoConnection> { ObserveMyResponsesUseCase(joins, projects)().first() }
    }

    @Test
    fun anEmptyListIsAnEmptyListNotNull() = runTest {
        joins.joins.value = emptyList()

        assertEquals(emptyList(), ObserveMyResponsesUseCase(joins, FakeProjectSource())().first())
    }
}

class ObserveHasNewInvitationsUseCaseTest {
    private val joins = FakeMyJoins(emptyList())

    private suspend fun has(): Boolean = ObserveHasNewInvitationsUseCase(joins)().first()

    private fun invitation(
        createdAt: Long? = 100,
        status: ProjectJoinStatus? = ProjectJoinStatus.Pending,
        type: ProjectJoinType? = ProjectJoinType.Invitation,
    ) = join(type = type, status = status, createdAt = createdAt?.let(::at))

    @Test
    fun anUnreadListHasNoDot() = runTest {
        joins.joins.value = null
        assertFalse(has())
    }

    @Test
    fun anInvitationIsNewWhenNothingWasSeen() = runTest {
        joins.joins.value = listOf(invitation())
        assertTrue(has())
    }

    @Test
    fun anInvitationIsNewOnlyWhenLaterThanTheLastSeen() = runTest {
        joins.joins.value = listOf(invitation(createdAt = 100))

        joins.seenAt.value = at(99)
        assertTrue(has())
        joins.seenAt.value = at(100)
        assertFalse(has(), "equal is not new")
        joins.seenAt.value = at(101)
        assertFalse(has())
    }

    @Test
    fun anApplicationOrAnUntypedRecordIsNeverNew() = runTest {
        joins.joins.value = listOf(invitation(type = ProjectJoinType.Application), invitation(type = null))
        assertFalse(has())
    }

    @Test
    fun aRecordWithoutATimeIsNeverNew() = runTest {
        joins.joins.value = listOf(invitation(createdAt = null))
        assertFalse(has())
    }

    @Test
    fun onlyWaitingInvitationsCount() = runTest {
        for (status in listOf(ProjectJoinStatus.Accepted, ProjectJoinStatus.Declined, ProjectJoinStatus.Withdrawn)) {
            joins.joins.value = listOf(invitation(status = status))
            assertFalse(has(), "$status")
        }
        for (status in listOf(ProjectJoinStatus.Pending, null)) {
            joins.joins.value = listOf(invitation(status = status))
            assertTrue(has(), "$status")
        }
    }

    @Test
    fun theDotGoesOffWhenTheInvitationsAreSeenAndEmitsOnlyChanges() = runTest {
        joins.joins.value = listOf(invitation(createdAt = 100))
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            ObserveHasNewInvitationsUseCase(joins)().toList(seen)
        }

        joins.seenAt.value = at(50)
        joins.seenAt.value = at(100)

        assertEquals(listOf(true, false), seen)
    }
}

class MarkInvitationsSeenUseCaseTest {
    private val joins = FakeMyJoins()
    private val mark = MarkInvitationsSeenUseCase(joins)

    @Test
    fun theNewestInvitationIsMarked() {
        mark(
            listOf(
                join("a", type = ProjectJoinType.Invitation, createdAt = at(10)),
                join("b", type = ProjectJoinType.Invitation, createdAt = at(30)),
                join("c", type = ProjectJoinType.Invitation, createdAt = at(20)),
            ),
        )

        assertEquals(listOf(at(30)), joins.marked)
    }

    @Test
    fun applicationsAndUntypedRecordsAreIgnoredEvenWhenNewer() {
        mark(
            listOf(
                join("a", type = ProjectJoinType.Invitation, createdAt = at(10)),
                join("b", type = ProjectJoinType.Application, createdAt = at(99)),
                join("c", type = null, createdAt = at(98)),
            ),
        )

        assertEquals(listOf(at(10)), joins.marked)
    }

    @Test
    fun invitationsWithoutATimeAreIgnored() {
        mark(listOf(join("a", type = ProjectJoinType.Invitation, createdAt = null)))
        assertTrue(joins.marked.isEmpty())
    }

    @Test
    fun noInvitationsMarkNothing() {
        mark(emptyList())
        mark(listOf(join(type = ProjectJoinType.Application, createdAt = at(1))))
        assertTrue(joins.marked.isEmpty())
    }
}

class ObserveProjectResponsesUseCaseTest {
    private val joins = FakeProjectJoins()
    private val projects = FakeProjectSource(listOf(project("p1")))
    private val observe = ObserveProjectResponsesUseCase(joins, projects)

    private fun response(id: String, type: ProjectJoinType?) =
        ProjectResponse(join(id, type = type), candidate())

    @Test
    fun nothingIsShownUntilBothAreKnown() = runTest {
        assertNull(observe(ProjectId("p1")).first(), "no records yet")

        joins.responses.value = mapOf(ProjectId("nope") to emptyList())
        assertNull(ObserveProjectResponsesUseCase(joins, FakeProjectSource())(ProjectId("nope")).first(), "no project yet")
    }

    @Test
    fun invitationsOfTheInitiatorAreLeftOutAndUntypedRecordsStay() = runTest {
        joins.responses.value = mapOf(
            ProjectId("p1") to listOf(
                response("app", ProjectJoinType.Application),
                response("inv", ProjectJoinType.Invitation),
                response("none", null),
            ),
        )

        val result = assertNotNull(observe(ProjectId("p1")).first())

        assertEquals("p1", result.project.id.value)
        assertEquals(listOf("app", "none"), result.responses.map { it.join.id.value })
    }

    @Test
    fun noRecordsIsAnEmptyListNotNull() = runTest {
        joins.responses.value = mapOf(ProjectId("p1") to emptyList())

        assertEquals(emptyList(), assertNotNull(observe(ProjectId("p1")).first()).responses)
    }
}
