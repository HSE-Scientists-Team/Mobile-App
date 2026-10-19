package org.matchlab.academic_net.feature.projects.ui.management

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectMember
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.RoleMember
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.projects.domain.MemberNotRemovableException
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.usecase.AddRoleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ChangeProjectLifecycleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.LoadProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.RemoveMemberUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UnpublishProjectUseCase
import org.matchlab.academic_net.feature.projects.ui.FakeProjectsRepository
import org.matchlab.academic_net.feature.projects.ui.Gate
import org.matchlab.academic_net.feature.projects.ui.caps
import org.matchlab.academic_net.feature.projects.ui.installMainDispatcher
import org.matchlab.academic_net.feature.projects.ui.keepCollecting
import org.matchlab.academic_net.feature.projects.ui.project
import org.matchlab.academic_net.feature.projects.ui.role
import org.matchlab.academic_net.feature.projects.ui.uninstallMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 07b: "Выгнать" (07i, 07u, 07v), the row "Отклики · N" and the guards of the removal. */
class ProjectMembersViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val id = ProjectId("p1")
    private val repository = FakeProjectsRepository(caps(canUnpublish = true))
    private val initiatorId = UserId("owner-p1")

    private fun TestScope.viewModel(): ProjectManagementViewModel {
        val viewModel = ProjectManagementViewModel(
            projectId = id,
            observeProject = ObserveProjectUseCase(repository),
            loadProject = LoadProjectUseCase(repository),
            unpublishProject = UnpublishProjectUseCase(repository),
            addRole = AddRoleUseCase(repository),
            changeLifecycle = ChangeProjectLifecycleUseCase(repository),
            removeMember = RemoveMemberUseCase(repository),
            capabilities = repository.capabilities,
        )
        keepCollecting(viewModel.uiState)
        return viewModel
    }

    private val ProjectManagementViewModel.state get() = uiState.value

    private fun person(id: String, name: String = "Имя $id", profile: Boolean = true) =
        RoleMember(UserSummary(UserId(id), name), rating = if (profile) 4.5 else null, completedProjects = 1, hasProfile = profile)

    private fun serve(viewerIsInitiator: Boolean = true, pendingJoinCount: Int = 0, vararg members: RoleMember) {
        val team = members.toList() + person(initiatorId.value, "Автор")
        val base = project(
            "p1",
            viewerIsInitiator = viewerIsInitiator,
            roles = listOf(role("r1", slots = team.size, occupied = team.size, members = team)),
        )
        repository.server[id] = base.copy(
            members = team.map { ProjectMember(it.user.id, RoleId("r1"), "Роль", isViewer = false) },
            pendingJoinCount = pendingJoinCount,
        )
    }

    private fun ProjectManagementViewModel.memberItem(user: String) =
        state.project!!.roles.flatMap { it.members }.first { it.userId.value == user }

    // ---- who can be kicked and contacted ----

    @Test
    fun theInitiatorMayKickEveryoneButTheInitiator() = runTest {
        serve(members = arrayOf(person("u1"), person("u2", profile = false)))

        val viewModel = viewModel()

        assertTrue(viewModel.memberItem("u1").canKick)
        assertTrue(viewModel.memberItem("u2").canKick, "a person without a profile is kicked all the same")
        assertFalse(viewModel.memberItem(initiatorId.value).canKick)
    }

    @Test
    fun aViewerWhoIsNotTheInitiatorKicksNobody() = runTest {
        serve(viewerIsInitiator = false, members = arrayOf(person("u1")))

        val viewModel = viewModel()

        assertFalse(viewModel.memberItem("u1").canKick)
        viewModel.onKickClick(UserId("u1"))
        assertNull(viewModel.state.kickDialog)
    }

    @Test
    fun theChatIsOfferedForAKnownPersonThatIsNotTheInitiator() = runTest {
        serve(members = arrayOf(person("u1"), person("u2", profile = false), person("  ")))

        val viewModel = viewModel()

        assertTrue(viewModel.memberItem("u1").canContact)
        assertFalse(viewModel.memberItem("u2").canContact, "no profile")
        assertFalse(viewModel.memberItem("  ").canContact, "blank id")
        assertFalse(viewModel.memberItem(initiatorId.value).canContact)
    }

    // ---- the row "Отклики · N" ----

    @Test
    fun onlyTheInitiatorHasTheRowOfResponsesWithTheServersNumber() = runTest {
        serve(pendingJoinCount = 4, members = arrayOf(person("u1")))
        with(viewModel().state.project!!) {
            assertTrue(showsResponses)
            assertEquals(4, pendingJoinCount)
        }

        serve(viewerIsInitiator = false, pendingJoinCount = 4, members = arrayOf(person("u1")))
        repository.cache.value = emptyMap()
        assertFalse(viewModel().state.project!!.showsResponses)
    }

    // ---- the dialog ----

    @Test
    fun theDialogNamesTheMemberAndClosesWithoutKicking() = runTest {
        serve(members = arrayOf(person("u1", "Анна")))
        val viewModel = viewModel()

        viewModel.onKickClick(UserId("u1"))
        assertEquals(KickTarget(UserId("u1"), "Анна"), viewModel.state.kickDialog)
        viewModel.onKickDismiss()

        assertNull(viewModel.state.kickDialog)
        assertTrue(repository.removedMembers.isEmpty())
    }

    @Test
    fun theInitiatorAndStrangersHaveNoDialog() = runTest {
        serve(members = arrayOf(person("u1")))
        val viewModel = viewModel()

        viewModel.onKickClick(initiatorId)
        viewModel.onKickClick(UserId("stranger"))

        assertNull(viewModel.state.kickDialog)
    }

    @Test
    fun confirmingWithoutTheDialogDoesNothing() = runTest {
        serve(members = arrayOf(person("u1")))
        val viewModel = viewModel()

        viewModel.onKickConfirm()

        assertTrue(repository.removedMembers.isEmpty())
        assertNull(viewModel.state.removingMember)
    }

    // ---- removing ----

    @Test
    fun confirmingRemovesTheMemberOnceAndClearsTheState() = runTest {
        serve(members = arrayOf(person("u1")))
        val viewModel = viewModel()
        viewModel.onKickClick(UserId("u1"))

        viewModel.onKickConfirm()

        assertEquals(listOf(id to UserId("u1")), repository.removedMembers)
        with(viewModel.state) {
            assertNull(kickDialog)
            assertNull(removingMember)
            assertNull(memberFailure)
            assertEquals(false, actionFailed)
        }
    }

    @Test
    fun aSecondConfirmWhileTheRemovalRunsRemovesNobodyTwice() = runTest {
        serve(members = arrayOf(person("u1"), person("u2")))
        repository.removeMemberGate = Gate()
        val viewModel = viewModel()
        viewModel.onKickClick(UserId("u1"))

        viewModel.onKickConfirm()
        assertEquals(UserId("u1"), viewModel.state.removingMember, "the arc of 07u")
        viewModel.onKickConfirm()
        viewModel.onKickClick(UserId("u2"))
        assertNull(viewModel.state.kickDialog, "no new dialog while one removal runs")
        viewModel.onKickConfirm()

        repository.removeMemberGate!!.open()
        assertEquals(1, repository.removedMembers.size)
        assertNull(viewModel.state.removingMember)
    }

    @Test
    fun theDialogCannotBeConfirmedTwiceEvenAfterTheRemovalEnded() = runTest {
        serve(members = arrayOf(person("u1")))
        val viewModel = viewModel()
        viewModel.onKickClick(UserId("u1"))

        viewModel.onKickConfirm()
        viewModel.onKickConfirm()

        assertEquals(1, repository.removedMembers.size)
    }

    @Test
    fun aRefusalIsNotAllowed() = runTest {
        serve(members = arrayOf(person("u1")))
        repository.removeMemberFailure = MemberNotRemovableException(id, UserId("u1"))
        val viewModel = viewModel()
        viewModel.onKickClick(UserId("u1"))

        viewModel.onKickConfirm()

        assertEquals(MemberFailure.NotAllowed, viewModel.state.memberFailure)
        assertNull(viewModel.state.removingMember)
        assertTrue(viewModel.state.project != null, "the screen stays")
    }

    @Test
    fun aGoneProjectIsTheScreenProjectNotFound() = runTest {
        serve(members = arrayOf(person("u1")))
        val viewModel = viewModel()
        viewModel.onKickClick(UserId("u1"))
        repository.removeMemberFailure = ProjectNotFoundException(id)

        viewModel.onKickConfirm()

        with(viewModel.state) {
            assertNull(project)
            assertFalse(isLoading)
            assertNull(loadFailure)
        }
    }

    @Test
    fun anyOtherFailureKeepsItsCauseAndTheNextClickClearsIt() = runTest {
        serve(members = arrayOf(person("u1")))
        val cause = IllegalStateException("offline")
        repository.removeMemberFailure = cause
        val viewModel = viewModel()
        viewModel.onKickClick(UserId("u1"))

        viewModel.onKickConfirm()

        assertSame(cause, assertIs<MemberFailure.Failed>(viewModel.state.memberFailure).cause)
        viewModel.onKickClick(UserId("u1"))
        assertNull(viewModel.state.memberFailure)
        assertEquals(KickTarget(UserId("u1"), "Имя u1"), viewModel.state.kickDialog)
        repository.removeMemberFailure = null
        viewModel.onKickConfirm()
        assertEquals(2, repository.removedMembers.size, "the user may try again")
    }
}
