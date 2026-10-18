package org.matchlab.academic_net.feature.projects.data

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.FeatureUnavailableException
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.projects.domain.NewProject
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.gateway.dto.ProjectRoleInputDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto

@OptIn(ExperimentalCoroutinesApi::class)
class GatewayProjectsDataSourceTest {
    private val id = ProjectId("p1")
    private val api = ScriptedProjectsApi()
    private val assembler = MappingAssembler()
    private val source = GatewayProjectsDataSource(api, assembler)

    private val newProject = NewProject(
        title = "T",
        type = ProjectType.Scientific,
        sphere = Sphere("Biology", "Biology"),
        problem = "P",
        goal = "G",
        tags = emptyList(),
        roles = emptyList(),
        period = ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)),
    )

    // ---- what the API cannot do ----

    @Test
    fun everyCapabilityIsOffBecauseTheApiLeavesItsValuesUndefined() {
        assertEquals(ProjectsCapabilities(canCreate = false, canListMine = false, canUnpublish = false), source.capabilities)
    }

    @Test
    fun theThreeOperationsTheApiCannotDoThrowWithTheirGapAndSendNothing() = runTest {
        assertEquals("G40", assertFailsWith<FeatureUnavailableException> { source.createProject(newProject) }.gap)
        assertEquals("G42", assertFailsWith<FeatureUnavailableException> { source.unpublishProject(id) }.gap)
        // Asking for the flow is not a failure yet; collecting it is.
        val mine = source.observeMyProjects()
        assertEquals("G44", assertFailsWith<FeatureUnavailableException> { mine.first() }.gap)

        assertEquals(emptyList(), api.gets)
        assertEquals(emptyList(), api.posts)
        assertEquals(emptyList(), assembler.asked)
    }

    // ---- reading a project ----

    @Test
    fun aProjectIsReadIntoTheCacheAndShownToThoseWhoWatchIt() = runTest {
        api.onGet = { projectDto(it, revision = 4, title = "AI-навигация") }
        val seen = mutableListOf<Project?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observeProject(id).toList(seen) }
        assertEquals(listOf<Project?>(null), seen, "nothing is known until it is read")

        val loaded = source.loadProject(id)

        assertEquals("AI-навигация", loaded?.title)
        assertEquals(listOf<Project?>(null, loaded), seen)
        assertEquals(listOf("p1"), api.gets)
    }

    @Test
    fun aProjectTheViewerMayNotSeeIsNullAndLeavesTheCache() = runTest {
        for (status in listOf(400, 403, 404)) {
            val api = ScriptedProjectsApi()
            val source = GatewayProjectsDataSource(api, MappingAssembler())
            api.onGet = { projectDto(it) }
            assertNotNull(source.loadProject(id))
            api.onGet = { throw RemoteFailure.Http(status) }

            assertNull(source.loadProject(id), "$status")

            assertNull(source.observeProject(id).first(), "$status: the project is gone from the cache")
        }
    }

    @Test
    fun aFailureIsThrownAndTheCacheStaysAsItWas() = runTest {
        api.onGet = { projectDto(it, title = "Прежний") }
        source.loadProject(id)
        for (failure in listOf(RemoteFailure.Http(500), RemoteFailure.NoConnection(), RemoteFailure.Timeout(), RemoteFailure.Malformed())) {
            api.onGet = { throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure> { source.loadProject(id) })

            assertEquals("Прежний", source.observeProject(id).first()?.title)
        }
    }

    @Test
    fun anAnswerTheAppCannotReadIsNullAndLeavesTheCache() = runTest {
        api.onGet = { projectDto(it) }
        source.loadProject(id)
        api.onGet = { projectDto(it, owner = "") }

        assertNull(source.loadProject(id))

        assertNull(source.observeProject(id).first())
    }

    @Test
    fun aCancellationPassesThroughAndTheCacheStays() = runTest {
        api.onGet = { projectDto(it, title = "Прежний") }
        source.loadProject(id)
        api.onGet = { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { source.loadProject(id) }

        assertEquals("Прежний", source.observeProject(id).first()?.title)
    }

    @Test
    fun theAssemblerFailureOfTheOwnerIsThrown() = runTest {
        api.onGet = { projectDto(it) }
        assembler.failure = RemoteFailure.Http(500)

        assertFailsWith<RemoteFailure.Http> { source.loadProject(id) }

        assertNull(source.observeProject(id).first())
    }

    @Test
    fun theCacheKeepsTheProjectsApartById() = runTest {
        api.onGet = { projectDto(it, title = "Проект $it") }
        source.loadProject(ProjectId("a"))
        source.loadProject(ProjectId("b"))

        assertEquals("Проект a", source.observeProject(ProjectId("a")).first()?.title)
        assertEquals("Проект b", source.observeProject(ProjectId("b")).first()?.title)
        assertNull(source.observeProject(ProjectId("c")).first())
    }

    // ---- getProject ----

    @Test
    fun getProjectAnswersFromTheCacheWithoutARequest() = runTest {
        api.onGet = { projectDto(it) }
        val loaded = source.loadProject(id)
        api.gets.clear()

        assertEquals(loaded, source.getProject(id))

        assertEquals(emptyList(), api.gets)
    }

    @Test
    fun getProjectReadsWhatTheCacheDoesNotHave() = runTest {
        api.onGet = { projectDto(it, title = "Новый") }

        assertEquals("Новый", source.getProject(id)?.title)
        assertEquals(listOf("p1"), api.gets)
        assertEquals("Новый", source.getProject(id)?.title)
        assertEquals(listOf("p1"), api.gets, "the second time it is the cache")
    }

    @Test
    fun getProjectOfAProjectThatIsNotThereIsNull() = runTest {
        api.onGet = { throw RemoteFailure.Http(404) }

        assertNull(source.getProject(id))
    }

    // ---- an older answer never replaces a newer one ----

    @Test
    fun aSlowAnswerOfAnOlderRevisionDoesNotReplaceANewerOne() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        api.onGet = {
            if (++calls == 1) {
                gate.await()
                projectDto(it, revision = 3, title = "Старая")
            } else {
                projectDto(it, revision = 5, title = "Новая")
            }
        }
        val slow = async { source.loadProject(id) }
        runCurrent()

        assertEquals("Новая", source.loadProject(id)?.title)
        gate.complete(Unit)

        assertEquals("Новая", slow.await()?.title, "the slow load reports what the cache has now")
        assertEquals("Новая", source.observeProject(id).first()?.title)
    }

    @Test
    fun aNewerAnswerReplacesAnOlderOne() = runTest {
        api.onGet = { projectDto(it, revision = 3, title = "Старая") }
        source.loadProject(id)
        api.onGet = { projectDto(it, revision = 4, title = "Новая") }

        assertEquals("Новая", source.loadProject(id)?.title)
        assertEquals("Новая", source.observeProject(id).first()?.title)
    }

    // ---- adding a role ----

    @Test
    fun aRoleIsPostedWithTheCachedRevisionOnePlaceAndTheSkills() = runTest {
        api.onGet = { projectDto(it, revision = 4, roles = listOf(roleDto("r1"))) }
        source.loadProject(id)
        api.onAddRole = { pid, _ -> projectDto(pid, revision = 5, roles = listOf(roleDto("r1"), roleDto("r2", "Дизайнер"))) }

        val role = source.addRole(id, RoleDraft("Дизайнер", listOf("Figma", "UX")))

        assertEquals(listOf("p1" to ProjectRoleInputDto("Дизайнер", slots = 1, expectedRevision = 4, skills = listOf("Figma", "UX"))), api.posts)
        assertNull(api.posts.single().second.description)
        assertEquals(RoleId("r2"), role.id)
        assertEquals("Дизайнер", role.title)
    }

    @Test
    fun theProjectOfTheCacheIsTheAnswerAfterTheRoleIsAdded() = runTest {
        api.onGet = { projectDto(it, revision = 4, roles = listOf(roleDto("r1"))) }
        source.loadProject(id)
        api.onAddRole = { pid, _ -> projectDto(pid, revision = 5, roles = listOf(roleDto("r1"), roleDto("r2", "Дизайнер"))) }

        source.addRole(id, RoleDraft("Дизайнер"))

        assertEquals(listOf(RoleId("r1"), RoleId("r2")), source.observeProject(id).first()?.roles?.map { it.id })
        // The next change builds on the revision of the answer.
        api.onAddRole = { pid, _ -> projectDto(pid, revision = 6, roles = listOf(roleDto("r1"), roleDto("r2"), roleDto("r3", "Тестер"))) }
        source.addRole(id, RoleDraft("Тестер"))
        assertEquals(5, api.posts.last().second.expectedRevision)
    }

    @Test
    fun theRevisionZeroIsSentToo() = runTest {
        api.onGet = { projectDto(it, revision = 0) }
        source.loadProject(id)
        api.onAddRole = { pid, _ -> projectDto(pid, revision = 1, roles = listOf(roleDto("r1", "Дизайнер"))) }

        source.addRole(id, RoleDraft("Дизайнер"))

        assertEquals(0, api.posts.single().second.expectedRevision)
    }

    @Test
    fun theProjectIsReadFirstWhenTheCacheDoesNotHaveIt() = runTest {
        api.onGet = { projectDto(it, revision = 8) }
        api.onAddRole = { pid, _ -> projectDto(pid, revision = 9, roles = listOf(roleDto("r1", "Дизайнер"))) }

        source.addRole(id, RoleDraft("Дизайнер"))

        assertEquals(listOf("p1"), api.gets)
        assertEquals(8, api.posts.single().second.expectedRevision)
    }

    @Test
    fun aProjectThatIsNotThereCannotGetARoleAndNothingIsPosted() = runTest {
        api.onGet = { throw RemoteFailure.Http(404) }

        assertEquals(id, assertFailsWith<ProjectNotFoundException> { source.addRole(id, RoleDraft("Дизайнер")) }.projectId)

        assertEquals(emptyList(), api.posts)
    }

    @Test
    fun aGoneProjectAnswersA404WithNotFoundAndLeavesTheCache() = runTest {
        api.onGet = { projectDto(it) }
        source.loadProject(id)
        api.onAddRole = { _, _ -> throw RemoteFailure.Http(404) }

        assertFailsWith<ProjectNotFoundException> { source.addRole(id, RoleDraft("Дизайнер")) }

        assertNull(source.observeProject(id).first())
    }

    @Test
    fun aChangedProjectIsReadAgainAndTheConflictIsThrown() = runTest {
        var reads = 0
        api.onGet = {
            reads++
            if (reads == 1) projectDto(it, revision = 4) else projectDto(it, revision = 6, roles = listOf(roleDto("r9", "Чужая")))
        }
        source.loadProject(id)
        api.onAddRole = { _, _ -> throw RemoteFailure.Http(409) }

        val conflict = assertFailsWith<ProjectConflictException> { source.addRole(id, RoleDraft("Дизайнер")) }

        assertEquals(id, conflict.projectId)
        assertEquals(emptyList(), conflict.suppressedExceptions)
        assertEquals(listOf(RoleId("r9")), source.observeProject(id).first()?.roles?.map { it.id }, "the cache shows what the server has")
        assertEquals(1, api.posts.size, "no retry: the answer that was lost might have added the role")
    }

    @Test
    fun theNextAttemptAfterAConflictUsesTheRevisionOfTheFreshRead() = runTest {
        var reads = 0
        api.onGet = { if (++reads == 1) projectDto(it, revision = 4) else projectDto(it, revision = 6) }
        source.loadProject(id)
        api.onAddRole = { _, _ -> throw RemoteFailure.Http(409) }
        assertFailsWith<ProjectConflictException> { source.addRole(id, RoleDraft("Дизайнер")) }
        api.onAddRole = { pid, _ -> projectDto(pid, revision = 7, roles = listOf(roleDto("r1", "Дизайнер"))) }

        source.addRole(id, RoleDraft("Дизайнер"))

        assertEquals(listOf(4, 6), api.posts.map { it.second.expectedRevision })
    }

    @Test
    fun whenTheReadAfterAConflictFailsTheConflictStaysAndCarriesTheFailure() = runTest {
        var reads = 0
        val failure = RemoteFailure.Http(500)
        api.onGet = { if (++reads == 1) projectDto(it, revision = 4, title = "Прежний") else throw failure }
        source.loadProject(id)
        api.onAddRole = { _, _ -> throw RemoteFailure.Http(409) }

        val conflict = assertFailsWith<ProjectConflictException> { source.addRole(id, RoleDraft("Дизайнер")) }

        assertEquals(listOf<Throwable>(failure), conflict.suppressedExceptions)
        assertEquals("Прежний", source.observeProject(id).first()?.title)
    }

    @Test
    fun aCancellationDuringTheReadAfterAConflictPassesThrough() = runTest {
        var reads = 0
        api.onGet = { if (++reads == 1) projectDto(it, revision = 4) else throw CancellationException("cancelled") }
        source.loadProject(id)
        api.onAddRole = { _, _ -> throw RemoteFailure.Http(409) }

        assertFailsWith<CancellationException> { source.addRole(id, RoleDraft("Дизайнер")) }
    }

    @Test
    fun aProjectThatIsGoneWhenReadAgainStillMakesAConflictAndLeavesTheCache() = runTest {
        var reads = 0
        api.onGet = { if (++reads == 1) projectDto(it, revision = 4) else throw RemoteFailure.Http(404) }
        source.loadProject(id)
        api.onAddRole = { _, _ -> throw RemoteFailure.Http(409) }

        assertFailsWith<ProjectConflictException> { source.addRole(id, RoleDraft("Дизайнер")) }

        assertNull(source.observeProject(id).first())
    }

    @Test
    fun anyOtherFailureIsThrownAsItIsWithoutARetryAndTheCacheStays() = runTest {
        api.onGet = { projectDto(it, revision = 4, title = "Прежний") }
        source.loadProject(id)
        for (failure in listOf(RemoteFailure.Http(500), RemoteFailure.Http(403), RemoteFailure.Http(400), RemoteFailure.NoConnection(), RemoteFailure.Timeout())) {
            api.onAddRole = { _, _ -> throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure> { source.addRole(id, RoleDraft("Дизайнер")) })

            assertEquals("Прежний", source.observeProject(id).first()?.title)
        }
        assertEquals(5, api.posts.size)
    }

    @Test
    fun aCancellationOfThePostPassesThrough() = runTest {
        api.onGet = { projectDto(it) }
        source.loadProject(id)
        api.onAddRole = { _, _ -> throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { source.addRole(id, RoleDraft("Дизайнер")) }
    }

    // ---- the owner's profile after the write went through (API-GAP G47) ----

    private fun ownerProfile(id: String = "owner-1", name: String = "Владелец") =
        UserProfileDto(userId = id, details = UserProfileInputDto(displayName = name))

    @Test
    fun aFailureOfTheOwnersProfileAfterTheRoleWasPostedIsStillASuccessWithTheCachedOwner() = runTest {
        for (failure in listOf<Throwable>(RemoteFailure.NoConnection(), RemoteFailure.Timeout(), RemoteFailure.Http(500), IllegalStateException("boom"))) {
            val api = ScriptedProjectsApi()
            val assembler = MappingAssembler(owners = mutableMapOf("owner-1" to ownerProfile()))
            val source = GatewayProjectsDataSource(api, assembler)
            api.onGet = { projectDto(it, revision = 4, roles = listOf(roleDto("r1"))) }
            source.loadProject(id)
            api.onAddRole = { pid, _ -> projectDto(pid, revision = 5, roles = listOf(roleDto("r1"), roleDto("r2", "Дизайнер"))) }
            assembler.failure = failure // the request for the owner's profile fails; the role is on the server already

            val role = source.addRole(id, RoleDraft("Дизайнер"))

            assertEquals(RoleId("r2"), role.id, "$failure")
            assertEquals("Дизайнер", role.title)
            val cached = source.observeProject(id).first()
            assertEquals(listOf(RoleId("r1"), RoleId("r2")), cached?.roles?.map { it.id }, "the project of the cache is the answer")
            assertEquals("Владелец", cached?.initiator?.name, "the owner of the cached project stays")
            assertEquals(1, api.posts.size, "nothing is posted again")
        }
    }

    @Test
    fun theNextRoleAfterThatBuildsOnTheRevisionOfTheAnswerThatWasKept() = runTest {
        val assembler = MappingAssembler(owners = mutableMapOf("owner-1" to ownerProfile()))
        val source = GatewayProjectsDataSource(api, assembler)
        api.onGet = { projectDto(it, revision = 4) }
        source.loadProject(id)
        api.onAddRole = { pid, _ -> projectDto(pid, revision = 5, roles = listOf(roleDto("r1", "Первая"))) }
        assembler.failure = RemoteFailure.Timeout()
        source.addRole(id, RoleDraft("Первая"))
        assembler.failure = null
        api.onAddRole = { pid, _ -> projectDto(pid, revision = 6, roles = listOf(roleDto("r1", "Первая"), roleDto("r2", "Вторая"))) }

        source.addRole(id, RoleDraft("Вторая"))

        assertEquals(listOf(4, 5), api.posts.map { it.second.expectedRevision })
        assertEquals("Владелец", source.observeProject(id).first()?.initiator?.name, "the owner is the real one again")
    }

    @Test
    fun theViewersOwnProjectStaysTheViewersOwnWhenTheOwnersProfileFailedAfterTheWrite() = runTest {
        for (viewerIsOwner in listOf(true, false)) {
            val api = ScriptedProjectsApi()
            val viewer = UserId(if (viewerIsOwner) "owner-1" else "somebody-else")
            val assembler = MappingAssembler(viewer = viewer, owners = mutableMapOf("owner-1" to ownerProfile()))
            val source = GatewayProjectsDataSource(api, assembler)
            api.onGet = { projectDto(it, revision = 1) }
            source.loadProject(id)
            api.onAddRole = { pid, _ -> projectDto(pid, revision = 2, roles = listOf(roleDto("r1", "Дизайнер"))) }
            assembler.failure = RemoteFailure.NoConnection()

            source.addRole(id, RoleDraft("Дизайнер"))

            assertEquals(viewerIsOwner, source.observeProject(id).first()?.viewerIsInitiator, "the viewer is the owner: $viewerIsOwner")
        }
    }

    @Test
    fun aCachedProjectWithoutAnOwnersProfileKeepsItsEmptyNameAfterAFailureToo() = runTest {
        api.onGet = { projectDto(it, revision = 1) } // the assembler knows nobody: the initiator has no name
        source.loadProject(id)
        api.onAddRole = { pid, _ -> projectDto(pid, revision = 2, roles = listOf(roleDto("r1", "Дизайнер"))) }
        assembler.failure = RemoteFailure.Http(503)

        source.addRole(id, RoleDraft("Дизайнер"))

        val initiator = source.observeProject(id).first()?.initiator
        assertEquals("owner-1", initiator?.id?.value)
        assertEquals("", initiator?.name)
    }

    @Test
    fun aProjectReadInTheSameCallKeepsItsOwnerWhenTheOwnersProfileFailsAfterThePost() = runTest {
        // The cache is empty: the project is read first (with its owner), then the role is posted.
        val assembler = MappingAssembler(owners = mutableMapOf("owner-1" to ownerProfile()))
        val source = GatewayProjectsDataSource(api, assembler)
        api.onGet = { projectDto(it, revision = 8) }
        api.onAddRole = { pid, _ ->
            assembler.failure = RemoteFailure.Timeout() // from now on the owner cannot be asked for
            projectDto(pid, revision = 9, roles = listOf(roleDto("r1", "Дизайнер")))
        }

        val role = source.addRole(id, RoleDraft("Дизайнер"))

        assertEquals(RoleId("r1"), role.id)
        assertEquals("Владелец", source.observeProject(id).first()?.initiator?.name)
    }

    @Test
    fun anAnswerTheAppCannotReadIsMalformedWhateverTheOwnersProfileDoes() = runTest {
        api.onGet = { projectDto(it) }
        source.loadProject(id)
        val unreadableAnswers = listOf(
            projectDto(owner = ""),
            projectDto(owner = "   "),
            projectDto(id = "", owner = "owner-1"),
            projectDto(id = "  "),
        )
        for (unreadable in unreadableAnswers) {
            for (assemblerFails in listOf(false, true)) {
                assembler.failure = if (assemblerFails) RemoteFailure.Timeout() else null
                api.onAddRole = { _, _ -> unreadable }

                assertFailsWith<RemoteFailure.Malformed> { source.addRole(id, RoleDraft("Дизайнер")) }
            }
        }
    }

    @Test
    fun aCancellationWhileTheOwnersProfileIsAskedForPassesThroughAfterThePost() = runTest {
        api.onGet = { projectDto(it, revision = 4) }
        source.loadProject(id)
        api.onAddRole = { pid, _ -> projectDto(pid, revision = 5, roles = listOf(roleDto("r1", "Дизайнер"))) }
        assembler.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { source.addRole(id, RoleDraft("Дизайнер")) }

        assertEquals(1, api.posts.size)
        assertEquals(emptyList(), source.observeProject(id).first()?.roles, "a cancelled call changes nothing in the cache")
    }

    @Test
    fun afterAToleratedFailureTheWritesStillGoOneByOne() = runTest {
        val assembler = MappingAssembler(owners = mutableMapOf("owner-1" to ownerProfile()))
        val source = GatewayProjectsDataSource(api, assembler)
        api.onGet = { projectDto(it, revision = 4) }
        source.loadProject(id)
        val gate = CompletableDeferred<Unit>()
        api.onAddRole = { pid, input ->
            if (input.title == "Первая") {
                gate.await()
                projectDto(pid, revision = 5, roles = listOf(roleDto("r1", "Первая")))
            } else {
                projectDto(pid, revision = 6, roles = listOf(roleDto("r1", "Первая"), roleDto("r2", "Вторая")))
            }
        }
        assembler.failure = RemoteFailure.Timeout()

        val first = async { source.addRole(id, RoleDraft("Первая")) }
        val second = async { source.addRole(id, RoleDraft("Вторая")) }
        runCurrent()
        assertEquals(1, api.posts.size, "the second waits for the first")
        gate.complete(Unit)

        assertEquals(RoleId("r1"), first.await().id)
        assertEquals(RoleId("r2"), second.await().id)
        assertEquals(listOf(4, 5), api.posts.map { it.second.expectedRevision })
    }

    // ---- which role is the new one (API-GAP G54) ----

    @Test
    fun theOnlyNewRoleIsTakenWhateverItsTitle() = runTest {
        // The server may have normalized the title.
        api.onGet = { projectDto(it, roles = listOf(roleDto("r1"))) }
        source.loadProject(id)
        api.onAddRole = { pid, _ -> projectDto(pid, revision = 2, roles = listOf(roleDto("r1"), roleDto("r2", "Дизайнер (UX)"))) }

        assertEquals("Дизайнер (UX)", source.addRole(id, RoleDraft("Дизайнер")).title)
    }

    @Test
    fun ofSeveralNewRolesTheLastWithTheAskedTitleIsTaken() = runTest {
        api.onGet = { projectDto(it, roles = listOf(roleDto("r1"))) }
        source.loadProject(id)
        api.onAddRole = { pid, _ ->
            projectDto(
                pid,
                revision = 2,
                roles = listOf(roleDto("r1"), roleDto("r2", "Другая"), roleDto("r3", "Дизайнер"), roleDto("r4", "Дизайнер")),
            )
        }

        assertEquals(RoleId("r4"), source.addRole(id, RoleDraft("Дизайнер")).id)
    }

    @Test
    fun severalNewRolesNoneWithTheAskedTitleAreAmbiguous() = runTest {
        api.onGet = { projectDto(it, roles = listOf(roleDto("r1"))) }
        source.loadProject(id)
        api.onAddRole = { pid, _ -> projectDto(pid, revision = 2, roles = listOf(roleDto("r1"), roleDto("r2", "А"), roleDto("r3", "Б"))) }

        assertFailsWith<RemoteFailure.Malformed> { source.addRole(id, RoleDraft("Дизайнер")) }
        // The server has the roles anyway, so the cache shows them.
        assertEquals(3, source.observeProject(id).first()?.roles?.size)
    }

    @Test
    fun noNewRoleIsNotWhatTheApiPromises() = runTest {
        api.onGet = { projectDto(it, revision = 1, roles = listOf(roleDto("r1"))) }
        source.loadProject(id)
        api.onAddRole = { pid, _ -> projectDto(pid, revision = 2, roles = listOf(roleDto("r1"))) }

        assertFailsWith<RemoteFailure.Malformed> { source.addRole(id, RoleDraft("Дизайнер")) }
    }

    @Test
    fun anAnswerTheAppCannotReadIsMalformed() = runTest {
        api.onGet = { projectDto(it) }
        source.loadProject(id)
        api.onAddRole = { pid, _ -> projectDto(pid, owner = "") }

        assertFailsWith<RemoteFailure.Malformed> { source.addRole(id, RoleDraft("Дизайнер")) }
    }

    @Test
    fun aRoleWithNoRolesBeforeIsTheFirstNewOne() = runTest {
        api.onGet = { projectDto(it, roles = emptyList()) }
        source.loadProject(id)
        api.onAddRole = { pid, _ -> projectDto(pid, revision = 2, roles = listOf(roleDto("r1", "Дизайнер"))) }

        assertEquals(RoleId("r1"), source.addRole(id, RoleDraft("Дизайнер")).id)
    }

    // ---- writes go one by one ----

    @Test
    fun twoRolesAddedAtOnceGoOneAfterAnotherAndTheSecondUsesTheRevisionOfTheFirstAnswer() = runTest {
        api.onGet = { projectDto(it, revision = 4) }
        source.loadProject(id)
        val gate = CompletableDeferred<Unit>()
        api.onAddRole = { pid, input ->
            if (input.title == "Первая") {
                gate.await()
                projectDto(pid, revision = 5, roles = listOf(roleDto("r1", "Первая")))
            } else {
                projectDto(pid, revision = 6, roles = listOf(roleDto("r1", "Первая"), roleDto("r2", "Вторая")))
            }
        }

        val first = async { source.addRole(id, RoleDraft("Первая")) }
        val second = async { source.addRole(id, RoleDraft("Вторая")) }
        runCurrent()
        assertEquals(1, api.posts.size, "the second waits for the first")
        gate.complete(Unit)

        assertEquals(RoleId("r1"), first.await().id)
        assertEquals(RoleId("r2"), second.await().id)
        assertEquals(listOf(4, 5), api.posts.map { it.second.expectedRevision })
        assertEquals(listOf(RoleId("r1"), RoleId("r2")), source.observeProject(id).first()?.roles?.map { it.id })
    }

    @Test
    fun aFailedWriteDoesNotBlockTheNext() = runTest {
        api.onGet = { projectDto(it, revision = 4) }
        source.loadProject(id)
        api.onAddRole = { _, _ -> throw RemoteFailure.Http(500) }
        assertFailsWith<RemoteFailure.Http> { source.addRole(id, RoleDraft("Дизайнер")) }
        api.onAddRole = { pid, _ -> projectDto(pid, revision = 5, roles = listOf(roleDto("r1", "Дизайнер"))) }

        assertEquals(RoleId("r1"), source.addRole(id, RoleDraft("Дизайнер")).id)
    }

    // ---- spheres ----

    @Test
    fun theSpheresAreTheAppsTwelveWithTheNameAsTheId() = runTest {
        val spheres = source.getSpheres()

        assertEquals(12, spheres.size)
        assertTrue(spheres.all { it.id == it.name && it.name.isNotBlank() })
        assertEquals(spheres.size, spheres.map { it.name }.toSet().size)
        assertEquals(emptyList(), api.gets, "no request")
    }

    @Test
    fun theSpheresDoNotChangeBetweenCalls() = runTest {
        assertEquals(source.getSpheres(), source.getSpheres())
        assertFalse(source.getSpheres().isEmpty())
        assertIs<Sphere>(source.getSpheres().first())
    }
}
