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
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.Spheres
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.projects.domain.NewProject
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.gateway.ProjectSearchParams
import org.matchlab.academic_net.gateway.dto.ProjectListDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleInputDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto

@OptIn(ExperimentalCoroutinesApi::class)
class GatewayProjectsDataSourceTest {
    private val id = ProjectId("p1")
    private val api = ScriptedProjectsApi()
    private val assembler = MappingAssembler()
    private val team = ScriptedTeam()
    private val catalog = ScriptedCatalogApi()
    private val source = GatewayProjectsDataSource(api, catalog, assembler, team, TestViewer)

    private val newProject = NewProject(
        title = "T",
        type = ProjectType.Scientific,
        sphere = Spheres.all[6],
        problem = "P",
        goal = "G",
        tags = emptyList(),
        roles = emptyList(),
        period = ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)),
    )

    // ---- what is on ----

    @Test
    fun everyCapabilityIsOn() {
        assertEquals(
            ProjectsCapabilities(canCreate = true, canListMine = true, canUnpublish = true, canChangeLifecycle = true),
            source.capabilities,
        )
    }

    @Test
    fun constructingTheSourceSendsNothing() {
        assertEquals(emptyList(), api.gets)
        assertEquals(emptyList(), api.creates)
        assertEquals(emptyList(), api.transitions)
        assertEquals(emptyList(), catalog.lists)
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
            val source = GatewayProjectsDataSource(api, ScriptedCatalogApi(), MappingAssembler(), team, TestViewer)
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
            val source = GatewayProjectsDataSource(api, ScriptedCatalogApi(), assembler, team, TestViewer)
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
        val source = GatewayProjectsDataSource(api, ScriptedCatalogApi(), assembler, team, TestViewer)
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
            val source = GatewayProjectsDataSource(api, ScriptedCatalogApi(), assembler, team, TestViewer)
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
        val source = GatewayProjectsDataSource(api, ScriptedCatalogApi(), assembler, team, TestViewer)
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
        assertEquals(
            listOf("Дизайнер"),
            source.observeProject(id).first()?.roles?.map { it.title },
            "the write went through, so the cache already holds the answer even though the owner was not read",
        )
    }

    @Test
    fun afterAToleratedFailureTheWritesStillGoOneByOne() = runTest {
        val assembler = MappingAssembler(owners = mutableMapOf("owner-1" to ownerProfile()))
        val source = GatewayProjectsDataSource(api, ScriptedCatalogApi(), assembler, team, TestViewer)
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
    fun theSpheresAreTheAppsTwelve() = runTest {
        assertEquals(Spheres.all, source.getSpheres())
        assertEquals(emptyList(), api.gets, "no request")
        assertEquals(emptyList(), catalog.lists, "no request")
    }

    @Test
    fun theSpheresDoNotChangeBetweenCalls() = runTest {
        assertEquals(source.getSpheres(), source.getSpheres())
        assertFalse(source.getSpheres().isEmpty())
        assertIs<Sphere>(source.getSpheres().first())
    }

    // ---- creating a project ----

    private fun viewerOwned(id: String = "new-1", revision: Int = 1, roles: List<org.matchlab.academic_net.gateway.dto.ProjectRoleDto> = emptyList()) =
        projectDto(id, owner = TestViewer.value, revision = revision, roles = roles)

    @Test
    fun aProjectIsCreatedWithTheCodesTheMonthsAndOneSlotForEveryRole() = runTest {
        api.onCreate = { viewerOwned() }

        source.createProject(
            newProject.copy(
                type = ProjectType.Practical,
                sphere = Spheres.all[11],
                tags = listOf("ML", "Robotics"),
                roles = listOf(RoleDraft("ML Engineer", listOf("Python", "PyTorch")), RoleDraft("Дизайнер")),
                period = ProjectPeriod(YearMonth(2026, 10), YearMonth(2028, 2)),
            ),
        )

        val body = api.creates.single()
        assertEquals("T", body.title)
        assertEquals("practical", body.type)
        assertEquals("Дизайн", body.field)
        assertEquals("P", body.problem)
        assertEquals("G", body.goal)
        assertEquals(listOf("ML", "Robotics"), body.tags)
        assertEquals("2026-10-01", body.plannedStart)
        assertEquals("2028-02-29", body.plannedEnd)
        assertEquals(listOf("ML Engineer", "Дизайнер"), body.roles.map { it.title })
        assertEquals(listOf(listOf("Python", "PyTorch"), emptyList()), body.roles.map { it.skills })
        assertEquals(listOf(1, 1), body.roles.map { it.slots })
        assertEquals(1, api.creates.size)
    }

    @Test
    fun aProjectWithoutRolesIsCreatedWithAnEmptyArray() = runTest {
        api.onCreate = { viewerOwned() }

        source.createProject(newProject)

        assertEquals(emptyList(), api.creates.single().roles)
    }

    @Test
    fun theCreatedProjectIsReadFromTheAnswerAndKeptInTheCache() = runTest {
        api.onCreate = { viewerOwned(revision = 3) }

        val created = source.createProject(newProject)

        assertEquals(ProjectId("new-1"), created.id)
        assertEquals(ProjectType.Scientific, created.type)
        assertEquals(ProjectStatus.Recruiting, created.status)
        assertEquals(Spheres.all[6], created.sphere)
        assertEquals(ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)), created.period)
        assertTrue(created.viewerIsInitiator)
        assertEquals(created, source.observeProject(created.id).first())
        assertEquals(created, source.getProject(created.id))
        assertEquals(emptyList(), api.gets, "the answer of the creation is enough: no extra read")
    }

    @Test
    fun theCreatedProjectIsKeptAtTheRevisionOfTheAnswer() = runTest {
        api.onCreate = { viewerOwned(revision = 3) }
        api.onTransition = { pid, _ -> viewerOwned(pid, revision = 4).copy(status = "unpublished") }
        val created = source.createProject(newProject)

        source.unpublishProject(created.id)

        assertEquals(3, api.transitions.single().second.expectedRevision)
    }

    @Test
    fun theCreatedProjectAppearsFirstInMyProjects() = runTest {
        api.onCreate = { viewerOwned("new-1") }
        catalog.onList = { _, _ -> ProjectListDto(listOf(projectDto("old-1", owner = TestViewer.value)), total = 1) }
        val seen = mutableListOf<List<Project>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observeMyProjects().toList(seen) }
        assertEquals(listOf("old-1"), seen.last().map { it.id.value })

        source.createProject(newProject)

        assertEquals(listOf("new-1", "old-1"), seen.last().map { it.id.value })
    }

    @Test
    fun theNewestCreatedProjectIsFirst() = runTest {
        var next = 0
        api.onCreate = { viewerOwned("new-${++next}") }
        catalog.onList = { _, _ -> ProjectListDto() }

        source.createProject(newProject)
        source.createProject(newProject)

        assertEquals(listOf("new-2", "new-1"), source.observeMyProjects().first().map { it.id.value })
    }

    @Test
    fun aFailureOfTheOwnersProfileAfterTheCreationIsStillASuccess() = runTest {
        for (failure in listOf<Throwable>(RemoteFailure.NoConnection(), RemoteFailure.Timeout(), RemoteFailure.Http(500), IllegalStateException("boom"))) {
            val api = ScriptedProjectsApi()
            val assembler = MappingAssembler()
            val source = GatewayProjectsDataSource(api, ScriptedCatalogApi(), assembler, team, TestViewer)
            api.onCreate = { viewerOwned("new-1", revision = 2) }
            assembler.failure = failure // the project is on the server already

            val created = source.createProject(newProject)

            assertEquals(ProjectId("new-1"), created.id, "$failure")
            assertTrue(created.viewerIsInitiator, "the viewer is the owner of what they created")
            assertEquals(TestViewer, created.initiator.id)
            assertEquals("", created.initiator.name, "no profile: the screen writes the placeholder")
            assertEquals(created, source.observeProject(created.id).first())
            assertEquals(1, api.creates.size, "nothing is posted again")
        }
    }

    @Test
    fun aCreationAnswerTheAppCannotReadIsMalformed() = runTest {
        api.onCreate = { projectDto("new-1", owner = "") }

        assertFailsWith<RemoteFailure.Malformed> { source.createProject(newProject) }
        api.onCreate = { projectDto("", owner = TestViewer.value) }
        assertFailsWith<RemoteFailure.Malformed> { source.createProject(newProject) }
    }

    @Test
    fun aRejectedCreationIsTheHttpFailureAndChangesNothing() = runTest {
        catalog.onList = { _, _ -> ProjectListDto() }
        val seen = mutableListOf<List<Project>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observeMyProjects().toList(seen) }
        api.onCreate = { throw RemoteFailure.Http(400) }

        val failure = assertFailsWith<RemoteFailure.Http> { source.createProject(newProject) }

        assertEquals(400, failure.status)
        assertEquals(listOf(emptyList()), seen)
        assertEquals(1, api.creates.size, "no retry: the creation is not idempotent")
    }

    @Test
    fun everyOtherFailureOfTheCreationIsThrownAsItIs() = runTest {
        for (failure in listOf(RemoteFailure.NoConnection(), RemoteFailure.Timeout(), RemoteFailure.Http(500), RemoteFailure.Http(409))) {
            api.onCreate = { throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure> { source.createProject(newProject) })
        }
    }

    @Test
    fun aCancellationOfTheCreationPassesThrough() = runTest {
        api.onCreate = { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { source.createProject(newProject) }
    }

    @Test
    fun aFailedCreationDoesNotBlockTheNextWrite() = runTest {
        api.onCreate = { throw RemoteFailure.Http(500) }
        assertFailsWith<RemoteFailure.Http> { source.createProject(newProject) }
        api.onCreate = { viewerOwned() }

        assertEquals(ProjectId("new-1"), source.createProject(newProject).id)
    }

    @Test
    fun twoCreationsAtOnceGoOneAfterAnother() = runTest {
        val gate = CompletableDeferred<Unit>()
        api.onCreate = { input ->
            if (input.title == "Первый") {
                gate.await()
                viewerOwned("new-1")
            } else {
                viewerOwned("new-2")
            }
        }

        val first = async { source.createProject(newProject.copy(title = "Первый")) }
        val second = async { source.createProject(newProject.copy(title = "Второй")) }
        runCurrent()
        assertEquals(1, api.creates.size, "the second waits for the first")
        gate.complete(Unit)

        assertEquals(ProjectId("new-1"), first.await().id)
        assertEquals(ProjectId("new-2"), second.await().id)
        assertEquals(listOf("Первый", "Второй"), api.creates.map { it.title })
    }

    @Test
    fun aCreationWaitsForARoleBeingAdded() = runTest {
        api.onGet = { projectDto(it, revision = 4) }
        source.loadProject(id)
        val gate = CompletableDeferred<Unit>()
        api.onAddRole = { pid, _ ->
            gate.await()
            projectDto(pid, revision = 5, roles = listOf(roleDto("r1", "Первая")))
        }
        api.onCreate = { viewerOwned() }

        val role = async { source.addRole(id, RoleDraft("Первая")) }
        val creation = async { source.createProject(newProject) }
        runCurrent()
        assertEquals(emptyList(), api.creates, "the creation waits for the write in flight")
        gate.complete(Unit)

        role.await()
        creation.await()
        assertEquals(1, api.creates.size)
    }

    // ---- taking a project off publication ----

    @Test
    fun aProjectIsTakenOffWithTheUnpublishActionAndTheCachedRevision() = runTest {
        api.onGet = { projectDto(it, revision = 4) }
        source.loadProject(id)
        api.onTransition = { pid, _ -> projectDto(pid, revision = 5, status = "unpublished") }

        source.unpublishProject(id)

        assertEquals(listOf("p1"), api.transitions.map { it.first })
        assertEquals("unpublish", api.transitions.single().second.action)
        assertEquals(4, api.transitions.single().second.expectedRevision)
        assertEquals(listOf("p1"), api.gets, "the cache had the project: no extra read")
    }

    @Test
    fun theUnpublishedProjectReplacesTheCachedOneAndIsReturned() = runTest {
        api.onGet = { projectDto(it, revision = 4) }
        source.loadProject(id)
        api.onTransition = { pid, _ -> projectDto(pid, revision = 5, status = "unpublished") }

        val project = source.unpublishProject(id)

        assertEquals(ProjectStatus.Unpublished, project.status)
        assertEquals(project, source.observeProject(id).first())
    }

    @Test
    fun theNextWriteBuildsOnTheRevisionOfTheTransition() = runTest {
        api.onGet = { projectDto(it, revision = 4) }
        source.loadProject(id)
        api.onTransition = { pid, _ -> projectDto(pid, revision = 5, status = "unpublished") }
        api.onAddRole = { pid, _ -> projectDto(pid, revision = 6, status = "unpublished", roles = listOf(roleDto("r1", "Роль"))) }

        source.unpublishProject(id)
        source.addRole(id, RoleDraft("Роль"))

        assertEquals(5, api.posts.single().second.expectedRevision)
    }

    @Test
    fun aProjectNotInTheCacheIsReadFirst() = runTest {
        api.onGet = { projectDto(it, revision = 9) }
        api.onTransition = { pid, _ -> projectDto(pid, revision = 10, status = "unpublished") }

        source.unpublishProject(id)

        assertEquals(listOf("p1"), api.gets)
        assertEquals(9, api.transitions.single().second.expectedRevision)
    }

    @Test
    fun aProjectThatIsNotThereCannotBeTakenOffAndNothingIsPosted() = runTest {
        api.onGet = { throw RemoteFailure.Http(404) }

        val failure = assertFailsWith<ProjectNotFoundException> { source.unpublishProject(id) }

        assertEquals(id, failure.projectId)
        assertEquals(emptyList(), api.transitions)
    }

    @Test
    fun aGoneProjectOfATransitionAnswersA404WithNotFoundAndLeavesTheCache() = runTest {
        api.onGet = { projectDto(it, revision = 4) }
        source.loadProject(id)
        api.onTransition = { _, _ -> throw RemoteFailure.Http(404) }

        assertFailsWith<ProjectNotFoundException> { source.unpublishProject(id) }

        assertNull(source.observeProject(id).first())
    }

    @Test
    fun aChangedProjectOfATransitionIsReadAgainAndTheConflictIsThrown() = runTest {
        api.onGet = { projectDto(it, revision = 4, title = "Прежний") }
        source.loadProject(id)
        api.onTransition = { _, _ -> throw RemoteFailure.Http(409) }
        api.onGet = { projectDto(it, revision = 6, title = "Свежий") }

        val failure = assertFailsWith<ProjectConflictException> { source.unpublishProject(id) }

        assertEquals(id, failure.projectId)
        assertEquals(listOf("p1", "p1"), api.gets)
        assertEquals("Свежий", source.observeProject(id).first()?.title)
        assertEquals(1, api.transitions.size, "no retry")
    }

    @Test
    fun whenTheReadAfterTheConflictFailsTheConflictStaysAndCarriesTheFailure() = runTest {
        api.onGet = { projectDto(it, revision = 4) }
        source.loadProject(id)
        api.onTransition = { _, _ -> throw RemoteFailure.Http(409) }
        val readFailure = RemoteFailure.Timeout()
        api.onGet = { throw readFailure }

        val failure = assertFailsWith<ProjectConflictException> { source.unpublishProject(id) }

        assertEquals(listOf<Throwable>(readFailure), failure.suppressedExceptions)
    }

    @Test
    fun anyOtherFailureOfTheTransitionIsThrownAsItIsAndTheCacheStays() = runTest {
        api.onGet = { projectDto(it, revision = 4, title = "Прежний") }
        source.loadProject(id)
        for (failure in listOf(RemoteFailure.Http(500), RemoteFailure.Http(403), RemoteFailure.NoConnection(), RemoteFailure.Timeout())) {
            api.onTransition = { _, _ -> throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure> { source.unpublishProject(id) })

            assertEquals("Прежний", source.observeProject(id).first()?.title)
        }
        assertEquals(listOf("p1"), api.gets, "no re-read after anything but a 409")
    }

    @Test
    fun aCancellationOfTheTransitionPassesThrough() = runTest {
        api.onGet = { projectDto(it, revision = 4) }
        source.loadProject(id)
        api.onTransition = { _, _ -> throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { source.unpublishProject(id) }
    }

    @Test
    fun aFailureOfTheOwnersProfileAfterTheTransitionIsStillASuccessWithTheCachedOwner() = runTest {
        val assembler = MappingAssembler(owners = mutableMapOf("owner-1" to ownerProfile()))
        val source = GatewayProjectsDataSource(api, catalog, assembler, team, TestViewer)
        api.onGet = { projectDto(it, revision = 4) }
        source.loadProject(id)
        api.onTransition = { pid, _ -> projectDto(pid, revision = 5, status = "unpublished") }
        assembler.failure = RemoteFailure.Timeout()

        val project = source.unpublishProject(id)

        assertEquals(ProjectStatus.Unpublished, project.status)
        assertEquals("Владелец", project.initiator.name)
        assertEquals(1, api.transitions.size)
    }

    @Test
    fun aTransitionWaitsForTheWriteInFlight() = runTest {
        api.onGet = { projectDto(it, revision = 4) }
        source.loadProject(id)
        val gate = CompletableDeferred<Unit>()
        api.onAddRole = { pid, _ ->
            gate.await()
            projectDto(pid, revision = 5, roles = listOf(roleDto("r1", "Роль")))
        }
        api.onTransition = { pid, _ -> projectDto(pid, revision = 6, status = "unpublished") }

        val role = async { source.addRole(id, RoleDraft("Роль")) }
        val transition = async { source.unpublishProject(id) }
        runCurrent()
        assertEquals(emptyList(), api.transitions, "the transition waits")
        gate.complete(Unit)

        role.await()
        transition.await()
        assertEquals(5, api.transitions.single().second.expectedRevision, "it builds on the revision of the role")
    }

    // ---- the list "Я инициатор" ----

    private fun page(vararg dtos: org.matchlab.academic_net.gateway.dto.ProjectDto) = ProjectListDto(dtos.toList(), total = dtos.size.toLong())

    private fun fullPage(prefix: String, owner: String = TestViewer.value) =
        ProjectListDto((1..100).map { projectDto("$prefix-$it", owner = owner) }, total = 100_000)

    @Test
    fun theCatalogIsReadWithNoFilterAndOnlyTheViewersProjectsStay() = runTest {
        catalog.onList = { _, _ ->
            page(
                projectDto("a", owner = TestViewer.value),
                projectDto("b", owner = "somebody"),
                projectDto("c", owner = TestViewer.value),
            )
        }

        val mine = source.observeMyProjects().first()

        assertEquals(listOf("a", "c"), mine.map { it.id.value })
        assertTrue(mine.all { it.viewerIsInitiator })
        assertEquals(listOf(ProjectSearchParams() to org.matchlab.academic_net.common.paging.PageRequest(offset = 0, limit = 100)), catalog.lists)
    }

    @Test
    fun onlyTheViewersProjectsAreAssembledSoTheOtherOwnersAreNotLookedUp() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a", owner = TestViewer.value), projectDto("b", owner = "somebody")) }

        source.observeMyProjects().first()

        assertEquals(listOf(listOf("a")), assembler.asked.map { list -> list.map { it.id } })
    }

    @Test
    fun theOrderOfTheServerIsKept() = runTest {
        catalog.onList = { _, _ ->
            page(projectDto("z", owner = TestViewer.value), projectDto("a", owner = TestViewer.value), projectDto("m", owner = TestViewer.value))
        }

        assertEquals(listOf("z", "a", "m"), source.observeMyProjects().first().map { it.id.value })
    }

    @Test
    fun aProjectOfAnUnknownStatusTypeOrSphereStaysInTheList() = runTest {
        catalog.onList = { _, _ ->
            page(
                projectDto("a", owner = TestViewer.value, status = "published", type = "1", field = "Biology"),
                projectDto("b", owner = TestViewer.value, status = ""),
            )
        }

        val mine = source.observeMyProjects().first()

        assertEquals(listOf("a", "b"), mine.map { it.id.value })
        assertTrue(mine.all { it.status == null && it.viewerIsInitiator })
        assertNull(mine[0].type)
        assertNull(mine[0].sphere)
    }

    @Test
    fun anAnswerWithNoProjectsOfTheViewerIsAnEmptyList() = runTest {
        catalog.onList = { _, _ -> page(projectDto("b", owner = "somebody")) }

        assertEquals(emptyList(), source.observeMyProjects().first())
    }

    @Test
    fun anEmptyCatalogIsAnEmptyListAfterOneRequest() = runTest {
        catalog.onList = { _, _ -> ProjectListDto() }

        assertEquals(emptyList(), source.observeMyProjects().first())
        assertEquals(1, catalog.lists.size)
    }

    @Test
    fun aRepeatedIdIsShownOnce() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a", owner = TestViewer.value, title = "Первый"), projectDto("a", owner = TestViewer.value, title = "Второй"), projectDto("b", owner = TestViewer.value)) }

        val mine = source.observeMyProjects().first()

        assertEquals(listOf("a", "b"), mine.map { it.id.value })
        assertEquals("Первый", mine[0].title)
    }

    @Test
    fun aRepeatOnTheNextPageIsShownOnce() = runTest {
        catalog.onList = { _, page ->
            when (page.offset) {
                0 -> ProjectListDto((1..100).map { projectDto("p-$it", owner = TestViewer.value) }, total = 150)
                else -> ProjectListDto(listOf(projectDto("p-100", owner = TestViewer.value), projectDto("p-101", owner = TestViewer.value)), total = 150)
            }
        }

        val mine = source.observeMyProjects().first()

        assertEquals(101, mine.size)
        assertEquals(101, mine.map { it.id }.toSet().size)
        assertEquals(ProjectId("p-101"), mine.last().id)
    }

    @Test
    fun aProjectWithoutIdOrOwnerIsLeftOut() = runTest {
        catalog.onList = { _, _ -> page(projectDto("", owner = TestViewer.value), projectDto("b", owner = ""), projectDto("c", owner = TestViewer.value)) }

        assertEquals(listOf("c"), source.observeMyProjects().first().map { it.id.value })
    }

    @Test
    fun theCatalogIsReadInPagesOfOneHundredAndStopsAtThePageThatIsNotFull() = runTest {
        catalog.onList = { _, page ->
            when (page.offset) {
                0 -> fullPage("first")
                100 -> fullPage("second")
                else -> page(projectDto("last", owner = TestViewer.value))
            }
        }

        val mine = source.observeMyProjects().first()

        assertEquals(listOf(0, 100, 200), catalog.lists.map { it.second.offset })
        assertEquals(listOf(100, 100, 100), catalog.lists.map { it.second.limit })
        assertEquals(201, mine.size)
        assertEquals("last", mine.last().id.value)
    }

    @Test
    fun aFullPageThatIsTheLastOneCostsOneMoreEmptyRequest() = runTest {
        catalog.onList = { _, page -> if (page.offset == 0) fullPage("only") else ProjectListDto() }

        val mine = source.observeMyProjects().first()

        assertEquals(100, mine.size)
        assertEquals(listOf(0, 100), catalog.lists.map { it.second.offset })
    }

    @Test
    fun theCatalogIsReadForAtMostTenPages() = runTest {
        catalog.onList = { _, page -> fullPage("page-${page.offset}") }

        val mine = source.observeMyProjects().first()

        assertEquals(10, catalog.lists.size)
        assertEquals((0..9).map { it * 100 }, catalog.lists.map { it.second.offset })
        assertEquals(1000, mine.size)
    }

    @Test
    fun aPageOfOthersDoesNotStopTheReading() = runTest {
        catalog.onList = { _, page ->
            when (page.offset) {
                0 -> fullPage("others", owner = "somebody")
                else -> page(projectDto("mine", owner = TestViewer.value))
            }
        }

        assertEquals(listOf("mine"), source.observeMyProjects().first().map { it.id.value })
        assertEquals(2, catalog.lists.size)
    }

    @Test
    fun theCatalogIsReadOnceForSeveralCollectors() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a", owner = TestViewer.value)) }

        source.observeMyProjects().first()
        source.observeMyProjects().first()
        val third = async { source.observeMyProjects().first() }
        val fourth = async { source.observeMyProjects().first() }

        assertEquals(listOf("a"), third.await().map { it.id.value })
        assertEquals(listOf("a"), fourth.await().map { it.id.value })
        assertEquals(1, catalog.lists.size)
    }

    @Test
    fun twoCollectorsAtTheSameTimeReadTheCatalogOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        catalog.onList = { _, _ ->
            gate.await()
            page(projectDto("a", owner = TestViewer.value))
        }

        val first = async { source.observeMyProjects().first() }
        val second = async { source.observeMyProjects().first() }
        runCurrent()
        gate.complete(Unit)

        assertEquals(first.await(), second.await())
        assertEquals(1, catalog.lists.size)
    }

    @Test
    fun theFailureOfTheFirstReadGoesToTheCollector() = runTest {
        for (failure in listOf(RemoteFailure.NoConnection(), RemoteFailure.Timeout(), RemoteFailure.Http(500), RemoteFailure.Malformed())) {
            val catalog = ScriptedCatalogApi()
            val source = GatewayProjectsDataSource(api, catalog, assembler, team, TestViewer)
            catalog.onList = { _, _ -> throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure> { source.observeMyProjects().first() })
        }
    }

    @Test
    fun aFailureOnALaterPageGoesToTheCollectorToo() = runTest {
        catalog.onList = { _, page -> if (page.offset == 0) fullPage("first") else throw RemoteFailure.Timeout() }

        assertFailsWith<RemoteFailure.Timeout> { source.observeMyProjects().first() }
    }

    @Test
    fun collectingAgainAfterAFailureReadsTheCatalogAgain() = runTest {
        catalog.onList = { _, _ -> throw RemoteFailure.NoConnection() }
        assertFailsWith<RemoteFailure.NoConnection> { source.observeMyProjects().first() }
        catalog.onList = { _, _ -> page(projectDto("a", owner = TestViewer.value)) }

        assertEquals(listOf("a"), source.observeMyProjects().first().map { it.id.value })
        assertEquals(2, catalog.lists.size)
    }

    @Test
    fun aFailedFirstReadKeepsNothingInTheCache() = runTest {
        catalog.onList = { _, _ -> throw RemoteFailure.Timeout() }
        assertFailsWith<RemoteFailure.Timeout> { source.observeMyProjects().first() }

        assertNull(source.observeProject(ProjectId("a")).first())
    }

    @Test
    fun aCancellationOfTheReadPassesThrough() = runTest {
        catalog.onList = { _, _ -> throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { source.observeMyProjects().first() }
    }

    @Test
    fun theProjectsOfTheCatalogAreInTheCacheForTheirPage() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a", owner = TestViewer.value, title = "Мой")) }

        source.observeMyProjects().first()

        assertEquals("Мой", source.getProject(ProjectId("a"))?.title)
        assertEquals(emptyList(), api.gets, "no request: the list filled the cache")
    }

    @Test
    fun aProjectTakenOffPublicationStaysInTheListWithItsNewStatus() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a", owner = TestViewer.value, revision = 2)) }
        api.onTransition = { pid, _ -> projectDto(pid, owner = TestViewer.value, revision = 3, status = "unpublished") }
        val seen = mutableListOf<List<Project>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observeMyProjects().toList(seen) }
        assertEquals(listOf(ProjectStatus.Recruiting), seen.last().map { it.status })

        source.unpublishProject(ProjectId("a"))

        assertEquals(listOf("a"), seen.last().map { it.id.value })
        assertEquals(listOf(ProjectStatus.Unpublished), seen.last().map { it.status })
        assertEquals(2, api.transitions.single().second.expectedRevision, "the revision the list was read at")
    }

    @Test
    fun aProjectThatIsGoneLeavesTheList() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a", owner = TestViewer.value), projectDto("b", owner = TestViewer.value)) }
        api.onTransition = { _, _ -> throw RemoteFailure.Http(404) }
        val seen = mutableListOf<List<Project>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observeMyProjects().toList(seen) }

        assertFailsWith<ProjectNotFoundException> { source.unpublishProject(ProjectId("a")) }

        assertEquals(listOf("b"), seen.last().map { it.id.value })
    }

    @Test
    fun aRoleAddedShowsInTheList() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a", owner = TestViewer.value, revision = 2)) }
        api.onAddRole = { pid, _ -> projectDto(pid, owner = TestViewer.value, revision = 3, roles = listOf(roleDto("r1", "Дизайнер"))) }
        val seen = mutableListOf<List<Project>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observeMyProjects().toList(seen) }

        source.addRole(ProjectId("a"), RoleDraft("Дизайнер"))

        assertEquals(listOf("Дизайнер"), seen.last().single().roles.map { it.title })
    }

    @Test
    fun theListDoesNotRepeatWhenNothingChanged() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a", owner = TestViewer.value)) }
        api.onGet = { projectDto(it, owner = TestViewer.value) }
        val seen = mutableListOf<List<Project>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observeMyProjects().toList(seen) }

        source.loadProject(ProjectId("a"))

        assertEquals(1, seen.size)
    }
}
