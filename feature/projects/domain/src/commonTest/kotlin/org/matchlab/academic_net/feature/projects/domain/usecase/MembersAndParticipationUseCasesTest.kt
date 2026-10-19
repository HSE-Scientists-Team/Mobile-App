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
import org.matchlab.academic_net.feature.projects.domain.ParticipationSource
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
    private fun joined(id: String, viewer: Boolean = true, initiator: Boolean = false): Project =
        testProject(id).copy(viewerIsInitiator = initiator, members = listOf(member("me", viewer = viewer)))

    private fun useCase(repository: FakeProjectsRepository, ids: List<String>, asked: MutableList<Unit> = mutableListOf()) =
        GetParticipatingProjectsUseCase(
            ParticipationSource { asked += Unit; ids.map(::ProjectId) },
            repository,
        )

    @Test
    fun theProjectsKeepTheOrderOfTheIdsNotOfTheRepository() = runTest {
        val repository = FakeProjectsRepository(listOf(joined("a", initiator = false), joined("b"), joined("c")))

        val result = assertIs<ParticipatingProjectsResult.Loaded>(useCase(repository, listOf("c", "a", "b"))())

        assertEquals(listOf("c", "a", "b"), result.projects.map { it.id.value })
    }

    @Test
    fun onlyProjectsWhereTheViewerIsAMemberAndNotTheInitiatorStay() = runTest {
        val repository = FakeProjectsRepository(
            listOf(
                joined("member"),
                joined("left", viewer = false),
                joined("owner", initiator = true),
            ),
        )

        val result = assertIs<ParticipatingProjectsResult.Loaded>(useCase(repository, listOf("member", "left", "owner"))())

        assertEquals(listOf("member"), result.projects.map { it.id.value })
    }

    @Test
    fun aProjectTheViewerCannotSeeIsLeftOut() = runTest {
        val repository = FakeProjectsRepository(listOf(joined("a")))

        val result = assertIs<ParticipatingProjectsResult.Loaded>(useCase(repository, listOf("gone", "a"))())

        assertEquals(listOf("a"), result.projects.map { it.id.value })
    }

    @Test
    fun noRecordsIsAnEmptyListAndReadsNothing() = runTest {
        val repository = FakeProjectsRepository()

        val result = assertIs<ParticipatingProjectsResult.Loaded>(useCase(repository, emptyList())())

        assertTrue(result.projects.isEmpty())
        assertTrue(repository.loaded.isEmpty())
    }

    @Test
    fun everyProjectIsReadAfreshThroughLoadProject() = runTest {
        val repository = FakeProjectsRepository(listOf(joined("a"), joined("b")))

        useCase(repository, listOf("a", "b"))()

        assertEquals(setOf(ProjectId("a"), ProjectId("b")), repository.loaded.toSet())
    }

    @Test
    fun oneFailedProjectFailsTheWholeList() = runTest {
        val cause = RemoteFailure.Http(500)
        val repository = FakeProjectsRepository(listOf(joined("a"), joined("b")))
        repository.loadFailures[ProjectId("b")] = cause

        val result = useCase(repository, listOf("a", "b"))()

        assertSame(cause, assertIs<ParticipatingProjectsResult.Failure>(result).cause)
    }

    @Test
    fun aFailureOfTheSourceIsAFailure() = runTest {
        val cause = RemoteFailure.NoConnection()
        val useCase = GetParticipatingProjectsUseCase(ParticipationSource { throw cause }, FakeProjectsRepository())

        assertSame(cause, assertIs<ParticipatingProjectsResult.Failure>(useCase()).cause)
    }

    @Test
    fun aCancellationPassesThrough() = runTest {
        val useCase = GetParticipatingProjectsUseCase(ParticipationSource { throw CancellationException("c") }, FakeProjectsRepository())
        assertFailsWith<CancellationException> { useCase() }
    }
}
