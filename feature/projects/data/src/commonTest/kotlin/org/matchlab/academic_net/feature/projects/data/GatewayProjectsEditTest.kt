package org.matchlab.academic_net.feature.projects.data

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectMember
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Spheres
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.projects.domain.MemberNotRemovableException
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.domain.ProjectInfo
import org.matchlab.academic_net.feature.projects.domain.ProjectLifecycleAction
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.feature.projects.domain.RoleNotRemovableException
import org.matchlab.academic_net.gateway.dto.ProjectListDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import org.matchlab.academic_net.gateway.mapping.TeamMemberProfile

/** The lifecycle, the description, the roles and the team of the projects source (blocks 0 to 4 of sprint 2). */
@OptIn(ExperimentalCoroutinesApi::class)
class GatewayProjectsEditTest {
    private val id = ProjectId("p1")
    private val api = ScriptedProjectsApi()
    private val assembler = MappingAssembler()
    private val catalog = ScriptedCatalogApi()
    private val team = ScriptedTeam()
    private val source = GatewayProjectsDataSource(api, catalog, assembler, team, TestViewer)

    private val info = ProjectInfo(
        title = "Новое название",
        type = ProjectType.Practical,
        sphere = Spheres.all[2],
        problem = "Новая проблема",
        goal = "Новая цель",
        tags = listOf("a", "b"),
        period = ProjectPeriod(YearMonth(2027, 2), YearMonth(2027, 12)),
    )

    private suspend fun cached(revision: Int = 4, roles: List<org.matchlab.academic_net.gateway.dto.ProjectRoleDto> = emptyList()) {
        api.onGet = { projectDto(it, revision = revision, roles = roles) }
        source.loadProject(id)
    }

    // ---- lifecycle ----

    @Test
    fun eachActionSendsItsCodeAndTheCachedRevision() = runTest {
        cached(revision = 4)
        api.onTransition = { pid, input -> projectDto(pid, revision = input.expectedRevision + 1, status = "started") }

        source.changeLifecycle(id, ProjectLifecycleAction.Start)
        source.changeLifecycle(id, ProjectLifecycleAction.Complete)
        source.changeLifecycle(id, ProjectLifecycleAction.Cancel)

        assertEquals(listOf("start", "complete", "cancel"), api.transitions.map { it.second.action })
        assertEquals(listOf(4, 5, 6), api.transitions.map { it.second.expectedRevision })
    }

    @Test
    fun theMovedProjectIsInTheCacheWithItsNewStatus() = runTest {
        cached()
        api.onTransition = { pid, _ -> projectDto(pid, revision = 5, status = "finished") }

        val project = source.changeLifecycle(id, ProjectLifecycleAction.Complete)

        assertEquals(ProjectStatus.Finished, project.status)
        assertEquals(project, source.observeProject(id).first())
    }

    @Test
    fun aGoneProjectIsNotFoundAndLeavesTheCache() = runTest {
        cached()
        api.onTransition = { _, _ -> throw RemoteFailure.Http(404) }

        assertFailsWith<ProjectNotFoundException> { source.changeLifecycle(id, ProjectLifecycleAction.Start) }

        assertNull(source.observeProject(id).first())
    }

    @Test
    fun aRefusedMoveIsReadAgainAndIsAConflictWithoutARetry() = runTest {
        cached()
        api.onTransition = { _, _ -> throw RemoteFailure.Http(409) }
        api.onGet = { projectDto(it, revision = 9, status = "cancelled", title = "Свежий") }

        assertFailsWith<ProjectConflictException> { source.changeLifecycle(id, ProjectLifecycleAction.Start) }

        assertEquals(1, api.transitions.size)
        assertEquals("Свежий", source.observeProject(id).first()?.title)
        assertEquals(ProjectStatus.Cancelled, source.observeProject(id).first()?.status)
    }

    @Test
    fun anyOtherFailureOfAMoveIsThrownAsItIs() = runTest {
        cached()
        for (failure in listOf(RemoteFailure.Http(400), RemoteFailure.Http(500), RemoteFailure.Timeout(), RemoteFailure.NoConnection())) {
            api.onTransition = { _, _ -> throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure> { source.changeLifecycle(id, ProjectLifecycleAction.Cancel) })
        }
        assertEquals(1, api.gets.size, "no re-read unless a 409")
    }

    @Test
    fun aMoveOfAProjectNotInTheCacheReadsItFirstAndOfOneNotThereSendsNothing() = runTest {
        api.onGet = { projectDto(it, revision = 8) }
        api.onTransition = { pid, _ -> projectDto(pid, revision = 9, status = "started") }
        source.changeLifecycle(id, ProjectLifecycleAction.Start)
        assertEquals(8, api.transitions.single().second.expectedRevision)

        val other = ProjectId("gone")
        api.onGet = { throw RemoteFailure.Http(404) }
        assertFailsWith<ProjectNotFoundException> { source.changeLifecycle(other, ProjectLifecycleAction.Start) }
        assertEquals(1, api.transitions.size)
    }

    // ---- updateProject ----

    @Test
    fun theDescriptionIsPutWithCodesDatesTagsAndTheCachedRevision() = runTest {
        cached(revision = 4)
        api.onUpdate = { pid, _ -> projectDto(pid, revision = 5) }

        source.updateProject(id, info)

        val (pid, body) = api.updates.single()
        assertEquals("p1", pid)
        assertEquals("Новое название", body.title)
        assertEquals("practical", body.type)
        assertEquals(Spheres.all[2].id, body.field)
        assertEquals("Новая проблема", body.problem)
        assertEquals("Новая цель", body.goal)
        assertEquals(listOf("a", "b"), body.tags)
        assertEquals("2027-02-01", body.plannedStart)
        assertEquals("2027-12-31", body.plannedEnd)
        assertEquals(4, body.expectedRevision)
    }

    @Test
    fun noTagsGoAsAnEmptyListAndTheAnswerReplacesTheCache() = runTest {
        cached()
        api.onUpdate = { pid, _ -> projectDto(pid, revision = 5, title = "Ответ") }

        val project = source.updateProject(id, info.copy(tags = emptyList()))

        assertEquals(emptyList(), api.updates.single().second.tags)
        assertEquals("Ответ", project.title)
        assertEquals(project, source.observeProject(id).first())
    }

    @Test
    fun theNextWriteAfterAnUpdateBuildsOnItsRevision() = runTest {
        cached(revision = 4)
        api.onUpdate = { pid, _ -> projectDto(pid, revision = 5) }
        api.onTransition = { pid, _ -> projectDto(pid, revision = 6, status = "started") }

        source.updateProject(id, info)
        source.changeLifecycle(id, ProjectLifecycleAction.Start)

        assertEquals(5, api.transitions.single().second.expectedRevision)
    }

    @Test
    fun anUpdateOfAGoneProjectIsNotFoundAndAConflictIsReadAgain() = runTest {
        cached()
        api.onUpdate = { _, _ -> throw RemoteFailure.Http(404) }
        assertFailsWith<ProjectNotFoundException> { source.updateProject(id, info) }
        assertNull(source.observeProject(id).first())

        cached()
        api.onUpdate = { _, _ -> throw RemoteFailure.Http(409) }
        api.onGet = { projectDto(it, revision = 8, title = "Свежий") }
        assertFailsWith<ProjectConflictException> { source.updateProject(id, info) }
        assertEquals("Свежий", source.observeProject(id).first()?.title)
    }

    @Test
    fun anotherFailureOfAnUpdateIsThrownAsItIs() = runTest {
        cached()
        val failure = RemoteFailure.Http(400)
        api.onUpdate = { _, _ -> throw failure }

        assertSame(failure, assertFailsWith<RemoteFailure> { source.updateProject(id, info) })
    }

    @Test
    fun aCancellationOfAnUpdatePassesThrough() = runTest {
        cached()
        api.onUpdate = { _, _ -> throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { source.updateProject(id, info) }
    }

    // ---- updateRole ----

    @Test
    fun aRoleIsPutWithTheNewTitleAndSkillsAndTheSlotsAndDescriptionOfTheCache() = runTest {
        cached(roles = listOf(roleDto("r1", "Старая", slots = 3, occupied = 1, description = "Описание роли")))
        api.onUpdateRole = { pid, _, _ -> projectDto(pid, revision = 5, roles = listOf(roleDto("r1", "Новая", slots = 3, occupied = 1, description = "Описание роли", skills = listOf("Kotlin")))) }

        val role = source.updateRole(id, RoleId("r1"), RoleDraft("Новая", listOf("Kotlin")))

        val (pid, rid, body) = api.roleUpdates.single()
        assertEquals("p1", pid)
        assertEquals("r1", rid)
        assertEquals("Новая", body.title)
        assertEquals(3, body.slots)
        assertEquals(4, body.expectedRevision)
        assertEquals(listOf("Kotlin"), body.skills)
        assertEquals("Описание роли", body.description)
        assertEquals("Новая", role.title)
        assertEquals(listOf("Kotlin"), role.skills)
    }

    @Test
    fun anEmptyDescriptionIsNotSentBack() = runTest {
        cached(roles = listOf(roleDto("r1", description = ""), roleDto("r2")))
        api.onUpdateRole = { pid, _, _ -> projectDto(pid, revision = 5, roles = listOf(roleDto("r1"), roleDto("r2"))) }

        source.updateRole(id, RoleId("r1"), RoleDraft("Роль"))

        assertNull(api.roleUpdates.single().third.description)
    }

    @Test
    fun theDescriptionOfTheLatestAnswerIsTheOneSentNextTime() = runTest {
        cached(roles = listOf(roleDto("r1", description = "Первое")))
        api.onUpdateRole = { pid, _, _ -> projectDto(pid, revision = 5, roles = listOf(roleDto("r1", description = "Второе"))) }
        source.updateRole(id, RoleId("r1"), RoleDraft("A"))

        api.onUpdateRole = { pid, _, _ -> projectDto(pid, revision = 6, roles = listOf(roleDto("r1", description = "Второе"))) }
        source.updateRole(id, RoleId("r1"), RoleDraft("B"))

        assertEquals("Первое", api.roleUpdates[0].third.description)
        assertEquals("Второе", api.roleUpdates[1].third.description)
        assertEquals(5, api.roleUpdates[1].third.expectedRevision)
    }

    @Test
    fun aRoleThatIsNotInTheCachedProjectIsAConflictAfterReadingAndNothingIsPut() = runTest {
        cached(roles = listOf(roleDto("r1")))
        api.onGet = { projectDto(it, revision = 7, roles = listOf(roleDto("r1"), roleDto("r9"))) }

        assertFailsWith<ProjectConflictException> { source.updateRole(id, RoleId("ghost"), RoleDraft("Роль")) }

        assertEquals(emptyList(), api.roleUpdates)
        assertEquals(listOf(RoleId("r1"), RoleId("r9")), source.observeProject(id).first()?.roles?.map { it.id })
    }

    @Test
    fun aGoneProjectOfARoleUpdateIsNotFound() = runTest {
        api.onGet = { throw RemoteFailure.Http(404) }

        assertFailsWith<ProjectNotFoundException> { source.updateRole(id, RoleId("r1"), RoleDraft("Роль")) }
        assertEquals(emptyList(), api.roleUpdates)
    }

    @Test
    fun aRoleUpdateAnswering404ReadsTheProjectAgain() = runTest {
        // The project is gone.
        cached(roles = listOf(roleDto("r1")))
        api.onUpdateRole = { _, _, _ -> throw RemoteFailure.Http(404) }
        api.onGet = { throw RemoteFailure.Http(404) }
        assertFailsWith<ProjectNotFoundException> { source.updateRole(id, RoleId("r1"), RoleDraft("Роль")) }
        assertNull(source.observeProject(id).first())

        // The project is there, the role is not: a conflict.
        cached(roles = listOf(roleDto("r1")))
        api.onGet = { projectDto(it, revision = 8, roles = emptyList()) }
        assertFailsWith<ProjectConflictException> { source.updateRole(id, RoleId("r1"), RoleDraft("Роль")) }
        assertEquals(emptyList(), source.observeProject(id).first()?.roles)
    }

    @Test
    fun aRoleUpdateAnswering409ReadsTheProjectAgainAndIsAConflict() = runTest {
        cached(roles = listOf(roleDto("r1")))
        api.onUpdateRole = { _, _, _ -> throw RemoteFailure.Http(409) }
        api.onGet = { projectDto(it, revision = 8, title = "Свежий", roles = listOf(roleDto("r1"))) }

        assertFailsWith<ProjectConflictException> { source.updateRole(id, RoleId("r1"), RoleDraft("Роль")) }

        assertEquals("Свежий", source.observeProject(id).first()?.title)
        assertEquals(1, api.roleUpdates.size)
    }

    @Test
    fun anotherFailureOfARoleUpdateIsThrownAsItIs() = runTest {
        cached(roles = listOf(roleDto("r1")))
        val failure = RemoteFailure.Http(500)
        api.onUpdateRole = { _, _, _ -> throw failure }

        assertSame(failure, assertFailsWith<RemoteFailure> { source.updateRole(id, RoleId("r1"), RoleDraft("Роль")) })
        assertEquals(1, api.gets.size)
    }

    @Test
    fun anAnswerWithoutTheRoleIsMalformed() = runTest {
        cached(roles = listOf(roleDto("r1")))
        api.onUpdateRole = { pid, _, _ -> projectDto(pid, revision = 5, roles = listOf(roleDto("other"))) }

        assertFailsWith<RemoteFailure.Malformed> { source.updateRole(id, RoleId("r1"), RoleDraft("Роль")) }
    }

    // ---- removeRole ----

    @Test
    fun aRoleIsDeletedWithTheCachedRevisionAndTheAnswerIsKept() = runTest {
        cached(revision = 4, roles = listOf(roleDto("r1"), roleDto("r2")))
        api.onDeleteRole = { pid, _, _ -> projectDto(pid, revision = 5, roles = listOf(roleDto("r2"))) }

        val project = source.removeRole(id, RoleId("r1"))

        assertEquals(listOf(Triple("p1", "r1", 4)), api.roleDeletes)
        assertEquals(listOf(RoleId("r2")), project.roles.map { it.id })
        assertEquals(project, source.observeProject(id).first())
    }

    @Test
    fun aRefusedDeletionIsReadAgainAndIsNotRemovable() = runTest {
        for (status in listOf(403, 409)) {
            cached(roles = listOf(roleDto("r1")))
            api.onDeleteRole = { _, _, _ -> throw RemoteFailure.Http(status) }
            api.onGet = { projectDto(it, revision = 8, title = "Свежий $status", roles = listOf(roleDto("r1", occupied = 1))) }

            val failure = assertFailsWith<RoleNotRemovableException> { source.removeRole(id, RoleId("r1")) }

            assertEquals(RoleId("r1"), failure.roleId)
            assertEquals(id, failure.projectId)
            assertEquals("Свежий $status", source.observeProject(id).first()?.title, "$status")
        }
    }

    @Test
    fun aRefusedDeletionStaysNotRemovableEvenWhenTheReadAgainFails() = runTest {
        cached(roles = listOf(roleDto("r1")))
        api.onDeleteRole = { _, _, _ -> throw RemoteFailure.Http(409) }
        api.onGet = { throw RemoteFailure.Timeout() }

        assertFailsWith<RoleNotRemovableException> { source.removeRole(id, RoleId("r1")) }
    }

    @Test
    fun aDeletionAnswering404ReturnsTheProjectReadAgainOrIsNotFound() = runTest {
        cached(roles = listOf(roleDto("r1")))
        api.onDeleteRole = { _, _, _ -> throw RemoteFailure.Http(404) }
        api.onGet = { projectDto(it, revision = 8, roles = emptyList()) }

        val project = source.removeRole(id, RoleId("r1"))

        assertEquals(emptyList(), project.roles)
        assertEquals(project, source.observeProject(id).first())

        cached(roles = listOf(roleDto("r1")))
        api.onGet = { throw RemoteFailure.Http(404) }
        assertFailsWith<ProjectNotFoundException> { source.removeRole(id, RoleId("r1")) }
        assertNull(source.observeProject(id).first())
    }

    @Test
    fun anotherFailureOfADeletionIsThrownAsItIs() = runTest {
        cached(roles = listOf(roleDto("r1")))
        val failure = RemoteFailure.Http(500)
        api.onDeleteRole = { _, _, _ -> throw failure }

        assertSame(failure, assertFailsWith<RemoteFailure> { source.removeRole(id, RoleId("r1")) })
        assertEquals(1, api.gets.size)
    }

    @Test
    fun aDeletionOfAProjectNotThereSendsNothing() = runTest {
        api.onGet = { throw RemoteFailure.Http(403) }

        assertFailsWith<ProjectNotFoundException> { source.removeRole(id, RoleId("r1")) }
        assertEquals(emptyList(), api.roleDeletes)
    }

    // ---- removeMember ----

    private fun withMembers(vararg users: String) = users.map { memberDto(it, role = "r1", title = "Роль") }

    @Test
    fun aMemberIsRemovedAndTheAnswerOfTheServerIsTheNewProject() = runTest {
        cached(revision = 4, roles = listOf(roleDto("r1", slots = 2, occupied = 2)))
        api.onRemoveMember = { pid, _ ->
            projectDto(pid, revision = 5, roles = listOf(roleDto("r1", slots = 2, occupied = 1)), members = withMembers("u2"))
        }

        val project = source.removeMember(id, UserId("u1"))

        assertEquals(listOf("p1" to "u1"), api.memberRemovals)
        assertEquals(listOf(UserId("u2")), project.members.map { it.userId })
        assertEquals(1, project.roles.single().occupied)
        assertEquals(project, source.observeProject(id).first())
    }

    @Test
    fun theRevisionOfTheAnswerIsTheOneTheNextWriteSends() = runTest {
        cached(revision = 4, roles = listOf(roleDto("r1")))
        api.onRemoveMember = { pid, _ -> projectDto(pid, revision = 9, roles = listOf(roleDto("r1"))) }
        api.onUpdate = { pid, _ -> projectDto(pid, revision = 10) }

        source.removeMember(id, UserId("u1"))
        source.updateProject(id, info)

        assertEquals(9, api.updates.single().second.expectedRevision)
    }

    @Test
    fun aRefusedRemovalIsReadAgainAndIsNotRemovable() = runTest {
        for (status in listOf(403, 409)) {
            cached(roles = listOf(roleDto("r1")))
            api.onRemoveMember = { _, _ -> throw RemoteFailure.Http(status) }
            api.onGet = { projectDto(it, revision = 8, title = "Свежий $status", roles = listOf(roleDto("r1"))) }

            val failure = assertFailsWith<MemberNotRemovableException> { source.removeMember(id, UserId("u1")) }

            assertEquals(UserId("u1"), failure.userId)
            assertEquals(id, failure.projectId)
            assertEquals("Свежий $status", source.observeProject(id).first()?.title, "$status")
        }
    }

    @Test
    fun aRefusedRemovalStaysNotRemovableWhenTheReadAgainFailsButIsNotFoundWhenTheProjectIsGone() = runTest {
        cached(roles = listOf(roleDto("r1")))
        api.onRemoveMember = { _, _ -> throw RemoteFailure.Http(403) }
        api.onGet = { throw RemoteFailure.Timeout() }
        assertFailsWith<MemberNotRemovableException> { source.removeMember(id, UserId("u1")) }

        api.onGet = { throw RemoteFailure.Http(404) }
        assertFailsWith<ProjectNotFoundException> { source.removeMember(id, UserId("u1")) }
        assertNull(source.observeProject(id).first())
    }

    @Test
    fun aRemovalAnswering404ReturnsTheProjectReadAgainOrIsNotFound() = runTest {
        cached(roles = listOf(roleDto("r1")))
        api.onRemoveMember = { _, _ -> throw RemoteFailure.Http(404) }
        api.onGet = { projectDto(it, revision = 8, title = "Ещё есть", members = emptyList()) }

        val project = source.removeMember(id, UserId("u1"))

        assertEquals("Ещё есть", project.title)
        assertEquals(project, source.observeProject(id).first())

        cached(roles = listOf(roleDto("r1")))
        api.onGet = { throw RemoteFailure.Http(404) }
        assertFailsWith<ProjectNotFoundException> { source.removeMember(id, UserId("u1")) }
        assertNull(source.observeProject(id).first())
    }

    @Test
    fun anotherFailureOfARemovalIsThrownAsItIsWithoutReadingAgain() = runTest {
        cached(roles = listOf(roleDto("r1")))
        val failure = RemoteFailure.Http(500)
        api.onRemoveMember = { _, _ -> throw failure }

        assertSame(failure, assertFailsWith<RemoteFailure> { source.removeMember(id, UserId("u1")) })
        assertEquals(1, api.gets.size)
        assertEquals(1, api.memberRemovals.size, "no automatic retry")
    }

    @Test
    fun aRemovalFromAProjectNotThereSendsNothing() = runTest {
        api.onGet = { throw RemoteFailure.Http(404) }

        assertFailsWith<ProjectNotFoundException> { source.removeMember(id, UserId("u1")) }
        assertEquals(emptyList(), api.memberRemovals)
    }

    @Test
    fun anUnreadableAnswerOfARemovalIsMalformed() = runTest {
        cached(roles = listOf(roleDto("r1")))
        api.onRemoveMember = { _, _ -> projectDto("").copy(ownerUserId = "") }

        assertFailsWith<RemoteFailure.Malformed> { source.removeMember(id, UserId("u1")) }
    }

    @Test
    fun aRemovalAndALifecycleMoveGoOneByOne() = runTest {
        cached(revision = 4, roles = listOf(roleDto("r1")))
        val gate = CompletableDeferred<Unit>()
        api.onRemoveMember = { pid, _ ->
            gate.await()
            projectDto(pid, revision = 5)
        }
        api.onTransition = { pid, input -> projectDto(pid, revision = input.expectedRevision + 1, status = "started") }

        val removal = async { source.removeMember(id, UserId("u1")) }
        val move = async { source.changeLifecycle(id, ProjectLifecycleAction.Start) }
        runCurrent()
        assertEquals(emptyList(), api.transitions, "the move waits for the removal")
        gate.complete(Unit)
        removal.await()
        move.await()

        assertEquals(5, api.transitions.single().second.expectedRevision)
    }

    // ---- writes one by one ----

    @Test
    fun aRoleUpdateWaitsForTheLifecycleMoveAndUsesItsRevision() = runTest {
        cached(revision = 4, roles = listOf(roleDto("r1")))
        val gate = CompletableDeferred<Unit>()
        api.onTransition = { pid, _ ->
            gate.await()
            projectDto(pid, revision = 5, status = "started", roles = listOf(roleDto("r1")))
        }
        api.onUpdateRole = { pid, _, _ -> projectDto(pid, revision = 6, status = "started", roles = listOf(roleDto("r1"))) }

        val move = async { source.changeLifecycle(id, ProjectLifecycleAction.Start) }
        val edit = async { source.updateRole(id, RoleId("r1"), RoleDraft("Новая")) }
        runCurrent()
        assertEquals(emptyList(), api.roleUpdates, "the role update waits")
        gate.complete(Unit)
        move.await()
        edit.await()

        assertEquals(5, api.roleUpdates.single().third.expectedRevision)
    }

    @Test
    fun aDeletionAndAnUpdateOfTheDescriptionGoOneByOne() = runTest {
        cached(revision = 4, roles = listOf(roleDto("r1")))
        val gate = CompletableDeferred<Unit>()
        api.onDeleteRole = { pid, _, _ ->
            gate.await()
            projectDto(pid, revision = 5)
        }
        api.onUpdate = { pid, _ -> projectDto(pid, revision = 6) }

        val delete = async { source.removeRole(id, RoleId("r1")) }
        val update = async { source.updateProject(id, info) }
        runCurrent()
        assertEquals(emptyList(), api.updates)
        gate.complete(Unit)
        delete.await()
        update.await()

        assertEquals(5, api.updates.single().second.expectedRevision)
    }

    @Test
    fun aFailedWriteDoesNotBlockTheNextOne() = runTest {
        cached(roles = listOf(roleDto("r1")))
        api.onDeleteRole = { _, _, _ -> throw RemoteFailure.Http(500) }
        assertFailsWith<RemoteFailure> { source.removeRole(id, RoleId("r1")) }
        api.onUpdate = { pid, _ -> projectDto(pid, revision = 5) }

        source.updateProject(id, info)

        assertEquals(1, api.updates.size)
    }

    // ---- the team ----

    private fun profile(user: String, name: String, rating: Double = 4.0, reviews: Int = 1, done: Int = 2) = UserProfileDto(
        userId = user,
        details = UserProfileInputDto(displayName = name),
        rating = rating,
        reviewCount = reviews,
        completedProjectCount = done,
    )

    private fun teamOf(vararg names: Pair<String, String>): suspend (List<ProjectMember>) -> List<TeamMemberProfile> {
        val profiles = names.toMap()
        return { members ->
            members.map { TeamMemberProfile(it, profiles[it.userId.value]?.let { name -> profile(it.userId.value, name) }) }
        }
    }

    private fun withMembers(revision: Int = 1, owner: String = "owner-1") = projectDto(
        "p1",
        owner = owner,
        revision = revision,
        roles = listOf(roleDto("r1", slots = 2, occupied = 1), roleDto("r2")),
        members = listOf(memberDto("u1", "r1"), memberDto("u2", "r2"), memberDto("u3", "r1")),
    )

    @Test
    fun readingOneProjectFillsTheMembersOfItsRoles() = runTest {
        api.onGet = { withMembers() }
        team.onRead = teamOf("u1" to "Анна", "u2" to "Борис")

        val project = assertIs<org.matchlab.academic_net.common.model.Project>(source.loadProject(id))

        assertEquals(listOf("Анна", ""), project.roles[0].members.map { it.user.name })
        assertEquals(listOf(true, false), project.roles[0].members.map { it.hasProfile })
        assertEquals(listOf("Борис"), project.roles[1].members.map { it.user.name })
        assertEquals(1, project.roles[0].occupied, "occupied stays the server's")
        assertEquals(1, team.reads.size)
        assertEquals(listOf("u1", "u2", "u3"), team.reads.single().map { it.userId.value })
    }

    @Test
    fun aProjectWithoutMembersAsksForNoTeam() = runTest {
        api.onGet = { projectDto(it) }

        source.loadProject(id)

        assertEquals(emptyList(), team.reads)
    }

    @Test
    fun aFailingTeamReaderLeavesTheProjectWithoutPeopleOnItsRoles() = runTest {
        api.onGet = { withMembers() }
        team.onRead = { throw RemoteFailure.Timeout() }

        val project = source.loadProject(id)

        assertEquals(emptyList(), project?.roles?.flatMap { it.members })
        assertEquals(3, project?.members?.size)
    }

    @Test
    fun theAnswerOfAWriteGetsItsTeamToo() = runTest {
        cached(revision = 1)
        team.onRead = teamOf("u1" to "Анна")
        api.onTransition = { _, _ -> withMembers(revision = 2) }

        val project = source.changeLifecycle(id, ProjectLifecycleAction.Start)

        assertEquals(listOf("Анна", ""), project.roles[0].members.map { it.user.name })
    }

    @Test
    fun aWriteAnswerWithAFailingTeamReaderIsStillASuccess() = runTest {
        cached(revision = 1)
        team.onRead = { throw IllegalStateException("boom") }
        api.onTransition = { _, _ -> withMembers(revision = 2) }

        val project = source.changeLifecycle(id, ProjectLifecycleAction.Start)

        assertEquals(emptyList(), project.roles.flatMap { it.members })
        assertEquals(ProjectStatus.Recruiting, project.status)
    }

    @Test
    fun aCancellationOfTheTeamReaderPassesThrough() = runTest {
        api.onGet = { withMembers() }
        team.onRead = { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { source.loadProject(id) }
    }

    @Test
    fun theListOfMyProjectsDoesNotReadTheTeam() = runTest {
        catalog.onList = { _, _ ->
            ProjectListDto(listOf(withMembers(owner = TestViewer.value)), total = 1)
        }

        val mine = source.observeMyProjects().first()

        assertEquals(emptyList(), team.reads)
        assertEquals(emptyList(), mine.single().roles.flatMap { it.members })
        assertEquals(3, mine.single().members.size)
    }

    @Test
    fun theListDoesNotOverwriteAProjectOfTheSameRevisionThatHasItsTeam() = runTest {
        api.onGet = { withMembers(revision = 3, owner = TestViewer.value) }
        team.onRead = teamOf("u1" to "Анна")
        source.loadProject(id)
        catalog.onList = { _, _ -> ProjectListDto(listOf(withMembers(revision = 3, owner = TestViewer.value)), total = 1) }

        val mine = source.observeMyProjects().first()

        assertEquals(listOf("Анна", ""), mine.single().roles[0].members.map { it.user.name })
    }

    @Test
    fun theListDoesOverwriteAnOlderRevision() = runTest {
        api.onGet = { withMembers(revision = 3, owner = TestViewer.value) }
        team.onRead = teamOf("u1" to "Анна")
        source.loadProject(id)
        catalog.onList = { _, _ ->
            ProjectListDto(listOf(withMembers(revision = 4, owner = TestViewer.value).copy(status = "unpublished")), total = 1)
        }

        val mine = source.observeMyProjects().first()

        assertEquals(ProjectStatus.Unpublished, mine.single().status)
        assertEquals(emptyList(), mine.single().roles.flatMap { it.members })
    }

    @Test
    fun theCatalogIsStillReadInPagesOfOneHundred() = runTest {
        catalog.onList = { _, _ -> ProjectListDto(emptyList(), total = 0) }

        source.observeMyProjects().first()

        assertEquals(listOf(PageRequest(offset = 0, limit = 100)), catalog.lists.map { it.second })
        assertTrue(team.reads.isEmpty())
    }
}
