package org.matchlab.academic_net.feature.projectdetails.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.result.RemoteFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

private class LeavingRepository : ProjectDetailsRepository {
    val left = mutableListOf<ProjectId>()
    var failure: Throwable? = null

    override suspend fun getProjectDetails(id: ProjectId): ProjectDetails? = null

    override suspend fun getTeam(project: Project) = emptyList<TeamMember>()

    override suspend fun leaveProject(id: ProjectId) {
        left += id
        failure?.let { throw it }
    }
}

class LeaveProjectUseCaseTest {
    private val repository = LeavingRepository()
    private val leave = LeaveProjectUseCase(repository)
    private val id = ProjectId("p-1")

    @Test
    fun aSuccessfulLeaveIsLeftAndAsksTheRepositoryOnce() = runTest {
        assertEquals(LeaveProjectResult.Left, leave(id))
        assertEquals(listOf(id), repository.left)
    }

    @Test
    fun aRefusalIsNotAllowed() = runTest {
        repository.failure = LeaveNotAllowedException(id)
        assertEquals(LeaveProjectResult.NotAllowed, leave(id))
    }

    @Test
    fun aGoneProjectIsNotFound() = runTest {
        repository.failure = ProjectGoneException(id)
        assertEquals(LeaveProjectResult.NotFound, leave(id))
    }

    @Test
    fun anyOtherFailureKeepsItsCause() = runTest {
        val cause = RemoteFailure.NoConnection()
        repository.failure = cause

        val result = assertIs<LeaveProjectResult.Failure>(leave(id))

        assertSame(cause, result.cause)
    }

    @Test
    fun aCancellationIsNotTurnedIntoAFailure() = runTest {
        repository.failure = CancellationException("cancelled")
        assertFailsWith<CancellationException> { leave(id) }
    }
}

class ObserveAppliedRolesUseCaseTest {
    @Test
    fun itFollowsTheSourceOfTheProject() = runTest {
        val roles = MutableStateFlow(setOf(RoleId("r-1")))
        val asked = mutableListOf<ProjectId>()
        val observe = ObserveAppliedRolesUseCase { projectId -> asked += projectId; roles }

        val flow = observe(ProjectId("p-9"))

        assertEquals(setOf(RoleId("r-1")), flow.first())
        roles.value = emptySet()
        assertEquals(emptySet(), flow.first())
        assertEquals(listOf(ProjectId("p-9")), asked)
    }
}
