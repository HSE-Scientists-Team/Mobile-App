package org.matchlab.academic_net.feature.projects.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectMember
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.projects.domain.FakeProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.MemberNotRemovableException
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.TestUser
import org.matchlab.academic_net.feature.projects.domain.testProject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

private fun member(user: String, viewer: Boolean = false) = ProjectMember(UserId(user), RoleId("r"), "Role", viewer)

class RemoveMemberUseCaseTest {
    private val project = testProject("p1").copy(
        initiator = TestUser,
        members = listOf(member("me", viewer = true), member("u1"), member("u2")),
    )
    private val repository = FakeProjectsRepository(listOf(project))
    private val remove = RemoveMemberUseCase(repository)

    @Test
    fun aMemberIsRemovedAndTheProjectComesBack() = runTest {
        val result = assertIs<RemoveMemberResult.Removed>(remove(ProjectId("p1"), UserId("u1")))

        assertEquals(listOf(UserId("me"), UserId("u2")), result.project.members.map { it.userId })
        assertEquals(listOf(ProjectId("p1") to UserId("u1")), repository.removedMembers)
    }

    @Test
    fun theInitiatorIsNotAllowedWithoutARequest() = runTest {
        assertEquals(RemoveMemberResult.NotAllowed, remove(ProjectId("p1"), TestUser.id))
        assertTrue(repository.removedMembers.isEmpty())
    }

    @Test
    fun theViewerThemselvesIsNotAllowedWithoutARequest() = runTest {
        assertEquals(RemoveMemberResult.NotAllowed, remove(ProjectId("p1"), UserId("me")))
        assertTrue(repository.removedMembers.isEmpty())
    }

    @Test
    fun anotherMemberWhoIsNotTheViewerIsNotBlockedByTheCheck() = runTest {
        assertIs<RemoveMemberResult.Removed>(remove(ProjectId("p1"), UserId("u2")))
    }

    @Test
    fun anUnknownProjectIsNotFoundWithoutARequest() = runTest {
        assertEquals(RemoveMemberResult.NotFound, remove(ProjectId("nope"), UserId("u1")))
        assertTrue(repository.removedMembers.isEmpty())
    }

    @Test
    fun aRefusalOfTheServerIsNotAllowed() = runTest {
        repository.removeMemberFailure = MemberNotRemovableException(ProjectId("p1"), UserId("u1"))
        assertEquals(RemoveMemberResult.NotAllowed, remove(ProjectId("p1"), UserId("u1")))
    }

    @Test
    fun aProjectGoneDuringTheCallIsNotFound() = runTest {
        repository.removeMemberFailure = ProjectNotFoundException(ProjectId("p1"))
        assertEquals(RemoveMemberResult.NotFound, remove(ProjectId("p1"), UserId("u1")))
    }

    @Test
    fun otherFailuresKeepTheirCause() = runTest {
        val cause = RemoteFailure.Timeout()
        repository.removeMemberFailure = cause
        assertSame(cause, assertIs<RemoveMemberResult.Failure>(remove(ProjectId("p1"), UserId("u1"))).cause)
    }

    @Test
    fun aFailureOfReadingTheProjectIsAFailureWithoutARequest() = runTest {
        val cause = RemoteFailure.NoConnection()
        repository.failure = cause

        assertSame(cause, assertIs<RemoveMemberResult.Failure>(remove(ProjectId("p1"), UserId("u1"))).cause)
        assertTrue(repository.removedMembers.isEmpty())
    }

    @Test
    fun aCancellationPassesThrough() = runTest {
        repository.removeMemberFailure = CancellationException("cancelled")
        assertFailsWith<CancellationException> { remove(ProjectId("p1"), UserId("u1")) }
    }
}

class GetParticipatingProjectsUseCaseTest {
    private fun joined(id: String): Project =
        testProject(id).copy(viewerIsInitiator = false, members = listOf(member("me", viewer = true)))

    @Test
    fun theProjectsAreThoseOfTheRepositoryInItsOrder() = runTest {
        val repository = FakeProjectsRepository()
        repository.participating = listOf(joined("c"), joined("a"), joined("b"))

        val result = assertIs<ParticipatingProjectsResult.Loaded>(GetParticipatingProjectsUseCase(repository)())

        assertEquals(listOf("c", "a", "b"), result.projects.map { it.id.value })
    }

    @Test
    fun theServersListIsNotFilteredAgainOnTheClient() = runTest {
        // The server decides who takes part (API-GAP G52): a project the client cannot confirm is still shown.
        val repository = FakeProjectsRepository()
        val unconfirmed = testProject("x").copy(viewerIsInitiator = false, members = emptyList())
        repository.participating = listOf(unconfirmed, testProject("y").copy(viewerIsInitiator = true))

        val result = assertIs<ParticipatingProjectsResult.Loaded>(GetParticipatingProjectsUseCase(repository)())

        assertEquals(listOf("x", "y"), result.projects.map { it.id.value })
    }

    @Test
    fun noProjectsIsAnEmptyList() = runTest {
        val result = assertIs<ParticipatingProjectsResult.Loaded>(GetParticipatingProjectsUseCase(FakeProjectsRepository())())

        assertTrue(result.projects.isEmpty())
    }

    @Test
    fun everyCallAsksAgain() = runTest {
        val repository = FakeProjectsRepository()
        val useCase = GetParticipatingProjectsUseCase(repository)

        useCase()
        useCase()

        assertEquals(2, repository.participatingCalls)
    }

    @Test
    fun aFailureOfTheRepositoryIsAFailure() = runTest {
        val cause = RemoteFailure.NoConnection()
        val repository = FakeProjectsRepository()
        repository.failure = cause

        assertSame(cause, assertIs<ParticipatingProjectsResult.Failure>(GetParticipatingProjectsUseCase(repository)()).cause)
    }

    @Test
    fun aCancellationPassesThrough() = runTest {
        val repository = FakeProjectsRepository()
        repository.failure = CancellationException("c")

        assertFailsWith<CancellationException> { GetParticipatingProjectsUseCase(repository)() }
    }
}
