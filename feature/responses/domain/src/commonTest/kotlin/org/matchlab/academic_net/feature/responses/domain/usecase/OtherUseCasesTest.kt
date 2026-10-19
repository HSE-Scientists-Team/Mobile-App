package org.matchlab.academic_net.feature.responses.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.FeatureUnavailableException
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.responses.domain.FakeMyJoins
import org.matchlab.academic_net.feature.responses.domain.FakeProjectJoins
import org.matchlab.academic_net.feature.responses.domain.FakeProjectSource
import org.matchlab.academic_net.feature.responses.domain.JoinMessageMaxLength
import org.matchlab.academic_net.feature.responses.domain.JoinRefusal
import org.matchlab.academic_net.feature.responses.domain.JoinRefusedException
import org.matchlab.academic_net.feature.responses.domain.ProjectResponsesUnavailableException
import org.matchlab.academic_net.feature.responses.domain.join
import org.matchlab.academic_net.feature.responses.domain.project
import org.matchlab.academic_net.feature.responses.domain.role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RefreshMyResponsesUseCaseTest {
    @Test
    fun successAndFailureAreResults() = runTest {
        val joins = FakeMyJoins(emptyList())
        assertTrue(RefreshMyResponsesUseCase(joins)().isSuccess)

        val cause = RemoteFailure.NoConnection()
        joins.refreshFailure = cause
        assertSame(cause, RefreshMyResponsesUseCase(joins)().exceptionOrNull())
    }

    @Test
    fun aCancellationPassesThrough() = runTest {
        val joins = FakeMyJoins(emptyList())
        joins.refreshFailure = CancellationException("c")
        assertFailsWith<CancellationException> { RefreshMyResponsesUseCase(joins)() }
    }
}

class GetRoleToApplyUseCaseTest {
    private val projects = FakeProjectSource(listOf(project("p1", listOf(role("r1"), role("r2")))))
    private val get = GetRoleToApplyUseCase(projects)

    @Test
    fun theProjectAndTheRoleAreFound() = runTest {
        val found = assertIs<LoadResult.Found<RoleToApply>>(get(ProjectId("p1"), RoleId("r2")))
        assertEquals("p1", found.value.project.id.value)
        assertEquals("r2", found.value.role.id.value)
    }

    @Test
    fun anUnknownRoleOrProjectIsNotFound() = runTest {
        assertEquals(LoadResult.NotFound, get(ProjectId("p1"), RoleId("zzz")))
        assertEquals(LoadResult.NotFound, get(ProjectId("hidden"), RoleId("r1")))
    }

    @Test
    fun aFailureIsAFailure() = runTest {
        val cause = RemoteFailure.Timeout()
        projects.getFailures[ProjectId("p1")] = cause
        assertSame(cause, assertIs<LoadResult.Failure>(get(ProjectId("p1"), RoleId("r1"))).cause)
    }
}

class ApplyToRoleUseCaseTest {
    private val joins = FakeMyJoins()
    private val apply = ApplyToRoleUseCase(joins)

    @Test
    fun theMessageIsTrimmedAndSent() = runTest {
        assertIs<SubmitResult.Success<*>>(apply(ProjectId("p1"), RoleId("r1"), "  Привет  "))
        assertEquals(listOf("apply p1 r1 Привет"), joins.calls)
    }

    @Test
    fun aBlankMessageIsSentAsNone() = runTest {
        apply(ProjectId("p1"), RoleId("r1"), "")
        apply(ProjectId("p1"), RoleId("r1"), " \n\t ")
        assertEquals(listOf("apply p1 r1 <none>", "apply p1 r1 <none>"), joins.calls)
    }

    @Test
    fun exactlyTheLimitIsSentAndOneMoreIsInvalidWithoutARequest() = runTest {
        assertIs<SubmitResult.Success<*>>(apply(ProjectId("p1"), RoleId("r1"), "x".repeat(JoinMessageMaxLength)))
        assertEquals(1, joins.calls.size)

        val result = apply(ProjectId("p1"), RoleId("r1"), "x".repeat(JoinMessageMaxLength + 1))

        assertEquals(SubmitResult.Invalid(setOf(JoinMessageError.TooLong)), result)
        assertEquals(1, joins.calls.size, "nothing was sent")
    }

    @Test
    fun theLimitIsCountedAfterTheTrim() = runTest {
        val padded = " " + "x".repeat(JoinMessageMaxLength) + "  "
        assertIs<SubmitResult.Success<*>>(apply(ProjectId("p1"), RoleId("r1"), padded))
    }

    @Test
    fun aRefusalStaysTheCauseOfTheFailure() = runTest {
        val refusal = JoinRefusedException(JoinRefusal.Conflict)
        joins.failure = refusal

        assertSame(refusal, assertIs<SubmitResult.Failure>(apply(ProjectId("p1"), RoleId("r1"), "hi")).cause)
    }

    @Test
    fun aCancellationPassesThrough() = runTest {
        joins.failure = CancellationException("c")
        assertFailsWith<CancellationException> { apply(ProjectId("p1"), RoleId("r1"), "hi") }
    }
}

class RefreshProjectResponsesUseCaseTest {
    private val joins = FakeProjectJoins()
    private val projects = FakeProjectSource(listOf(project("p1")))
    private val refresh = RefreshProjectResponsesUseCase(joins, projects)

    @Test
    fun aVisibleProjectReadsTheRecords() = runTest {
        assertEquals(RefreshResponsesResult.Done, refresh(ProjectId("p1")))
        assertEquals(listOf("refresh p1"), joins.calls)
    }

    @Test
    fun anInvisibleProjectIsNotFoundWithoutReadingTheRecords() = runTest {
        assertEquals(RefreshResponsesResult.NotFound, refresh(ProjectId("hidden")))
        assertTrue(joins.calls.isEmpty())
    }

    @Test
    fun aRefusedListIsNotFound() = runTest {
        joins.refreshFailure = ProjectResponsesUnavailableException(ProjectId("p1"))
        assertEquals(RefreshResponsesResult.NotFound, refresh(ProjectId("p1")))
    }

    @Test
    fun otherFailuresOfEitherReadAreFailures() = runTest {
        val cause = RemoteFailure.Http(500)
        joins.refreshFailure = cause
        assertSame(cause, assertIs<RefreshResponsesResult.Failure>(refresh(ProjectId("p1"))).cause)

        val other = RemoteFailure.NoConnection()
        projects.getFailures[ProjectId("p1")] = other
        assertSame(other, assertIs<RefreshResponsesResult.Failure>(refresh(ProjectId("p1"))).cause)
    }
}

class GetInviteOptionsUseCaseTest {
    private val projects = FakeProjectSource()
    private val get = GetInviteOptionsUseCase(projects)

    private suspend fun options(): List<InviteProject> = assertIs<LoadResult.Found<List<InviteProject>>>(get()).value

    @Test
    fun onlyOpenProjectsOfTheViewerWithAVacantRoleAreOffered() = runTest {
        val vacant = listOf(role("free"), role("taken", 1, 1))
        projects.myProjects = listOf(
            project("ok", vacant, ProjectStatus.Recruiting),
            project("unknown", vacant, status = null),
            project("paused", vacant, ProjectStatus.Unpublished),
            project("started", vacant, ProjectStatus.Started),
            project("finished", vacant, ProjectStatus.Finished),
            project("cancelled", vacant, ProjectStatus.Cancelled),
            project("full", listOf(role("taken", 1, 1))),
            project("noRoles", emptyList()),
            project("notMine", vacant, viewerIsInitiator = false),
        )

        val list = options()

        assertEquals(listOf("ok", "unknown"), list.map { it.project.id.value })
        assertEquals(listOf("free"), list.first().roles.map { it.id.value }, "only the vacant roles")
    }

    @Test
    fun theOrderOfTheProjectsIsKept() = runTest {
        projects.myProjects = listOf(project("b"), project("a"), project("c"))
        assertEquals(listOf("b", "a", "c"), options().map { it.project.id.value })
    }

    @Test
    fun aServerThatCannotListMineGivesAnEmptyList() = runTest {
        projects.myProjectsFailure = FeatureUnavailableException("mine")
        assertEquals(emptyList(), options())
    }

    @Test
    fun otherFailuresAreFailures() = runTest {
        val cause = RemoteFailure.NoConnection()
        projects.myProjectsFailure = cause
        assertSame(cause, assertIs<LoadResult.Failure>(get()).cause)
    }

    @Test
    fun aCancellationPassesThrough() = runTest {
        projects.myProjectsFailure = CancellationException("c")
        assertFailsWith<CancellationException> { get() }
    }
}

class InviteToProjectUseCaseTest {
    private val joins = FakeProjectJoins()
    private val invite = InviteToProjectUseCase(joins)
    private val candidate = UserId("u9")

    private fun draft(message: String = "", project: String? = "p1", role: String? = "r1") =
        InvitationDraft(project?.let(::ProjectId), role?.let(::RoleId), message)

    @Test
    fun aFullDraftIsSentWithTheTrimmedMessage() = runTest {
        assertIs<SubmitResult.Success<*>>(invite(candidate, draft("  Давай  ")))
        assertEquals(listOf("invite p1 r1 u9 Давай"), joins.calls)
    }

    @Test
    fun aBlankMessageIsSentAsNone() = runTest {
        invite(candidate, draft("   "))
        assertEquals(listOf("invite p1 r1 u9 <none>"), joins.calls)
    }

    @Test
    fun missingPartsAreInvalidWithoutARequest() = runTest {
        assertEquals(SubmitResult.Invalid(setOf(InvitationError.ProjectMissing)), invite(candidate, draft(project = null)))
        assertEquals(SubmitResult.Invalid(setOf(InvitationError.RoleMissing)), invite(candidate, draft(role = null)))
        assertEquals(
            SubmitResult.Invalid(setOf(InvitationError.ProjectMissing, InvitationError.RoleMissing)),
            invite(candidate, InvitationDraft()),
        )
        assertTrue(joins.calls.isEmpty())
    }

    @Test
    fun theMessageLimitIs500AfterTheTrim() = runTest {
        assertIs<SubmitResult.Success<*>>(invite(candidate, draft("x".repeat(JoinMessageMaxLength))))
        joins.calls.clear()

        val result = invite(candidate, draft("x".repeat(JoinMessageMaxLength + 1)))

        assertEquals(SubmitResult.Invalid(setOf(InvitationError.MessageTooLong)), result)
        assertTrue(joins.calls.isEmpty())
    }

    @Test
    fun allErrorsAreReportedTogether() = runTest {
        val result = invite(candidate, InvitationDraft(message = "x".repeat(JoinMessageMaxLength + 1)))

        assertEquals(
            SubmitResult.Invalid(setOf(InvitationError.ProjectMissing, InvitationError.RoleMissing, InvitationError.MessageTooLong)),
            result,
        )
    }

    @Test
    fun aRefusalStaysTheCauseOfTheFailure() = runTest {
        val refusal = JoinRefusedException(JoinRefusal.NotAllowed)
        joins.failure = refusal

        assertSame(refusal, assertIs<SubmitResult.Failure>(invite(candidate, draft())).cause)
    }

    @Test
    fun aCancellationPassesThrough() = runTest {
        joins.failure = CancellationException("c")
        assertFailsWith<CancellationException> { invite(candidate, draft()) }
    }
}
