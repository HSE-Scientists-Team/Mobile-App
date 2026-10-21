package org.matchlab.academic_net.feature.projects.data

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.ProjectSearchParams
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectListDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import org.matchlab.academic_net.gateway.mapping.TeamMemberProfile

/**
 * 06b "Я участник": `getParticipatingProjects` reads `GET /projects?mine=participant` in pages, afresh on each call, and
 * puts the projects into the cache. The list "Я инициатор" (`mine=owner`) is covered in [GatewayProjectsDataSourceTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GatewayParticipatingProjectsTest {
    private val api = ScriptedProjectsApi()
    private val assembler = MappingAssembler()
    private val team = ScriptedTeam()
    private val catalog = ScriptedCatalogApi()
    private val source = GatewayProjectsDataSource(api, catalog, assembler, team, TestViewer)

    private fun page(vararg dtos: ProjectDto) = ProjectListDto(dtos.toList(), total = dtos.size.toLong())

    private fun fullPage(prefix: String) =
        ProjectListDto((1..100).map { projectDto("$prefix-$it", owner = "owner-1") }, total = 100_000)

    private fun ids(projects: List<org.matchlab.academic_net.common.model.Project>) = projects.map { it.id.value }

    // ---- the request ----

    @Test
    fun theListIsReadWithTheParticipantFilterInTheFirstPageOfOneHundred() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a")) }

        source.getParticipatingProjects()

        assertEquals(
            listOf(ProjectSearchParams(mine = "participant") to PageRequest(offset = 0, limit = 100)),
            catalog.lists,
        )
    }

    @Test
    fun theOwnerFilterIsNeverSentByTheParticipantRead() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a")) }

        source.getParticipatingProjects()

        assertTrue(catalog.lists.none { it.first.mine == "owner" })
        assertTrue(catalog.lists.all { it.first.q == null && it.first.status == null && it.first.tags.isEmpty() })
    }

    @Test
    fun theServersOrderIsKeptAndTheProjectsKeepTheirOwners() = runTest {
        catalog.onList = { _, _ ->
            page(projectDto("z", owner = "o-1"), projectDto("a", owner = "o-2"), projectDto("m", owner = "o-1"))
        }

        val projects = source.getParticipatingProjects()

        assertEquals(listOf("z", "a", "m"), ids(projects))
        assertEquals(listOf("o-1", "o-2", "o-1"), projects.map { it.initiator.id.value })
        assertTrue(projects.none { it.viewerIsInitiator })
    }

    @Test
    fun everyProjectIsAssembledWithItsOwnerInOneBatch() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a", owner = "o-1"), projectDto("b", owner = "o-2")) }

        source.getParticipatingProjects()

        assertEquals(listOf(listOf("a", "b")), assembler.asked.map { list -> list.map { it.id } })
    }

    @Test
    fun theOwnersProfileIsOnTheProjectWhenTheAssemblerFoundIt() = runTest {
        assembler.owners["o-1"] = UserProfileDto(userId = "o-1", details = UserProfileInputDto(displayName = "Анна"))
        catalog.onList = { _, _ -> page(projectDto("a", owner = "o-1")) }

        assertEquals("Анна", source.getParticipatingProjects().single().initiator.name)
    }

    // ---- empty and failing answers ----

    @Test
    fun anEmptyAnswerIsAnEmptyListAfterOneRequest() = runTest {
        catalog.onList = { _, _ -> ProjectListDto() }

        assertEquals(emptyList(), source.getParticipatingProjects())
        assertEquals(1, catalog.lists.size)
        assertEquals(emptyList(), assembler.asked, "nothing to assemble")
    }

    @Test
    fun aFailureOfTheRequestIsThrownAsItIs() = runTest {
        for (failure in listOf(RemoteFailure.NoConnection(), RemoteFailure.Timeout(), RemoteFailure.Http(401), RemoteFailure.Http(500), RemoteFailure.Malformed())) {
            catalog.onList = { _, _ -> throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure> { source.getParticipatingProjects() })
        }
    }

    @Test
    fun aFailureOnALaterPageFailsTheWholeRead() = runTest {
        catalog.onList = { _, page -> if (page.offset == 0) fullPage("first") else throw RemoteFailure.Timeout() }

        assertFailsWith<RemoteFailure.Timeout> { source.getParticipatingProjects() }
    }

    @Test
    fun aFailureOfTheAssemblerIsThrown() = runTest {
        val failure = RemoteFailure.Http(502)
        assembler.failure = failure
        catalog.onList = { _, _ -> page(projectDto("a")) }

        assertSame(failure, assertFailsWith<RemoteFailure> { source.getParticipatingProjects() })
    }

    @Test
    fun aFailedReadLeavesTheCacheEmpty() = runTest {
        catalog.onList = { _, page -> if (page.offset == 0) fullPage("first") else throw RemoteFailure.Timeout() }
        assertFailsWith<RemoteFailure.Timeout> { source.getParticipatingProjects() }

        assertNull(source.observeProject(ProjectId("first-1")).first(), "the first page was not kept: the read failed as a whole")
    }

    @Test
    fun aCancellationPassesThrough() = runTest {
        catalog.onList = { _, _ -> throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { source.getParticipatingProjects() }
    }

    // ---- pages ----

    @Test
    fun fullPagesGoOnAndAPartialPageStopsTheRead() = runTest {
        catalog.onList = { _, page ->
            when (page.offset) {
                0 -> fullPage("first")
                100 -> fullPage("second")
                else -> page(projectDto("last"))
            }
        }

        val projects = source.getParticipatingProjects()

        assertEquals(listOf(0, 100, 200), catalog.lists.map { it.second.offset })
        assertEquals(listOf(100, 100, 100), catalog.lists.map { it.second.limit })
        assertEquals(listOf("participant"), catalog.lists.map { it.first.mine }.distinct())
        assertEquals(201, projects.size)
        assertEquals("last", projects.last().id.value)
    }

    @Test
    fun aPageOfNinetyNineStopsTheRead() = runTest {
        catalog.onList = { _, _ -> ProjectListDto((1..99).map { projectDto("p-$it") }, total = 5_000) }

        assertEquals(99, source.getParticipatingProjects().size)
        assertEquals(1, catalog.lists.size, "the size of the page decides, not total")
    }

    @Test
    fun aFullPageThatIsTheLastOneCostsOneMoreEmptyRequest() = runTest {
        catalog.onList = { _, page -> if (page.offset == 0) fullPage("only") else ProjectListDto() }

        assertEquals(100, source.getParticipatingProjects().size)
        assertEquals(listOf(0, 100), catalog.lists.map { it.second.offset })
    }

    @Test
    fun atMostTenPagesAreRead() = runTest {
        catalog.onList = { _, page -> fullPage("page-${page.offset}") }

        val projects = source.getParticipatingProjects()

        assertEquals((0..9).map { it * 100 }, catalog.lists.map { it.second.offset })
        assertEquals(1000, projects.size)
    }

    // ---- repeated ids and unreadable projects ----

    @Test
    fun aRepeatedIdInOnePageIsShownOnceAndTheFirstWins() = runTest {
        catalog.onList = { _, _ ->
            page(projectDto("a", title = "Первый"), projectDto("a", title = "Второй"), projectDto("b"))
        }

        val projects = source.getParticipatingProjects()

        assertEquals(listOf("a", "b"), ids(projects))
        assertEquals("Первый", projects.first().title)
        assertEquals(listOf(listOf("a", "b")), assembler.asked.map { list -> list.map { it.id } }, "a repeat is not assembled twice")
    }

    @Test
    fun aRepeatOnTheNextPageIsShownOnce() = runTest {
        catalog.onList = { _, page ->
            when (page.offset) {
                0 -> ProjectListDto((1..100).map { projectDto("p-$it", title = "первый $it") }, total = 150)
                else -> ProjectListDto(listOf(projectDto("p-100", title = "повтор"), projectDto("p-101")), total = 150)
            }
        }

        val projects = source.getParticipatingProjects()

        assertEquals(101, projects.size)
        assertEquals(101, projects.map { it.id }.toSet().size)
        assertEquals("первый 100", projects.first { it.id.value == "p-100" }.title)
        assertEquals(ProjectId("p-101"), projects.last().id)
    }

    @Test
    fun aProjectWithoutAnIdIsDropped() = runTest {
        catalog.onList = { _, _ -> page(projectDto(""), projectDto("   "), projectDto("c")) }

        assertEquals(listOf("c"), ids(source.getParticipatingProjects()))
    }

    @Test
    fun aProjectWithoutAnOwnerIsDropped() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a", owner = ""), projectDto("c")) }

        val projects = source.getParticipatingProjects()

        assertEquals(listOf("c"), ids(projects))
        assertNull(source.observeProject(ProjectId("a")).first(), "a dropped project is not cached either")
    }

    @Test
    fun aPageOfUnreadableProjectsDoesNotStopTheRead() = runTest {
        catalog.onList = { _, page ->
            when (page.offset) {
                0 -> ProjectListDto((1..100).map { projectDto("x-$it", owner = "") }, total = 101)
                else -> page(projectDto("ok"))
            }
        }

        assertEquals(listOf("ok"), ids(source.getParticipatingProjects()))
        assertEquals(2, catalog.lists.size)
    }

    @Test
    fun aProjectOfAnUnknownStatusTypeOrSphereStaysInTheList() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a", status = "published", type = "1", field = "Biology")) }

        val project = source.getParticipatingProjects().single()

        assertNull(project.status)
        assertNull(project.type)
        assertNull(project.sphere)
    }

    // ---- afresh ----

    @Test
    fun everyCallAsksTheServerAgain() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a")) }

        source.getParticipatingProjects()
        source.getParticipatingProjects()

        assertEquals(2, catalog.lists.size)
    }

    @Test
    fun theSecondCallShowsWhatTheServerHasNow() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a"), projectDto("b")) }
        assertEquals(listOf("a", "b"), ids(source.getParticipatingProjects()))
        catalog.onList = { _, _ -> page(projectDto("b"), projectDto("c")) }

        assertEquals(listOf("b", "c"), ids(source.getParticipatingProjects()), "a project the viewer left is not kept")
    }

    @Test
    fun aFailureAfterASuccessIsThrownAndTheNextCallReadsAgain() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a")) }
        source.getParticipatingProjects()
        catalog.onList = { _, _ -> throw RemoteFailure.NoConnection() }
        assertFailsWith<RemoteFailure.NoConnection> { source.getParticipatingProjects() }
        catalog.onList = { _, _ -> page(projectDto("a"), projectDto("b")) }

        assertEquals(listOf("a", "b"), ids(source.getParticipatingProjects()))
    }

    // ---- the cache ----

    @Test
    fun aProjectOfTheListOpensWithGetProjectWithoutARequest() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a", title = "Чужой проект")) }

        source.getParticipatingProjects()

        assertEquals("Чужой проект", source.getProject(ProjectId("a"))?.title)
        assertEquals(emptyList(), api.gets, "no GET /projects/{id}: the list filled the cache")
    }

    @Test
    fun aProjectOfTheListIsWatchedThroughObserveProject() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a", title = "Чужой проект")) }

        source.getParticipatingProjects()

        assertEquals("Чужой проект", source.observeProject(ProjectId("a")).first()?.title)
    }

    @Test
    fun theRevisionOfTheListIsTheOneAWriteBuildsOn() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a", revision = 7)) }
        api.onTransition = { id, _ -> projectDto(id, revision = 8, status = "unpublished") }

        source.getParticipatingProjects()
        source.unpublishProject(ProjectId("a"))

        assertEquals(7, api.transitions.single().second.expectedRevision)
        assertEquals(emptyList(), api.gets, "the project was in the cache, no read before the write")
    }

    @Test
    fun aNewerRevisionOfTheListReplacesTheCachedProject() = runTest {
        api.onGet = { projectDto(it, revision = 1, title = "Старое") }
        source.loadProject(ProjectId("a"))
        catalog.onList = { _, _ -> page(projectDto("a", revision = 2, title = "Новое")) }

        val projects = source.getParticipatingProjects()

        assertEquals("Новое", projects.single().title)
        assertEquals("Новое", source.observeProject(ProjectId("a")).first()?.title)
    }

    @Test
    fun theCachedProjectWithItsTeamStaysAtTheSameRevision() = runTest {
        api.onGet = { projectDto(it, revision = 3, roles = listOf(roleDto("r1")), members = listOf(memberDto("u1", "r1"))) }
        team.onRead = { members ->
            members.map {
                TeamMemberProfile(it, UserProfileDto(userId = it.userId.value, details = UserProfileInputDto(displayName = "Анна")))
            }
        }
        source.loadProject(ProjectId("a"))
        // The list has the same revision, but not the people of the team (the assembler does not read them).
        catalog.onList = { _, _ -> page(projectDto("a", revision = 3, roles = listOf(roleDto("r1")), members = listOf(memberDto("u1", "r1")))) }

        val project = source.getParticipatingProjects().single()

        assertEquals(listOf("Анна"), project.roles.single().members.map { it.user.name })
        assertEquals(listOf("Анна"), source.getProject(ProjectId("a"))!!.roles.single().members.map { it.user.name })
        assertEquals(1, team.reads.size, "the list reads no team")
    }

    @Test
    fun anOlderRevisionOfTheListDoesNotReplaceTheCachedProject() = runTest {
        api.onGet = { projectDto(it, revision = 5, title = "Свежее") }
        source.loadProject(ProjectId("a"))
        catalog.onList = { _, _ -> page(projectDto("a", revision = 4, title = "Устаревшее")) }

        val projects = source.getParticipatingProjects()

        assertEquals("Свежее", projects.single().title, "the list shows what the cache has")
        assertEquals("Свежее", source.getProject(ProjectId("a"))?.title)
    }

    // ---- the list "Я инициатор" is a separate list ----

    @Test
    fun theParticipatingProjectsDoNotJoinTheListOfTheViewersOwn() = runTest {
        catalog.onList = { params, _ ->
            if (params.mine == "participant") page(projectDto("joined", owner = "o-1")) else page(projectDto("own", owner = TestViewer.value))
        }

        source.getParticipatingProjects()
        val mine = source.observeMyProjects().first()

        assertEquals(listOf("own"), ids(mine))
        assertEquals(listOf("participant", "owner"), catalog.lists.map { it.first.mine })
    }

    @Test
    fun readingTheOwnListFirstDoesNotCacheTheParticipatingAnswer() = runTest {
        catalog.onList = { params, _ ->
            if (params.mine == "participant") page(projectDto("joined", owner = "o-1")) else page(projectDto("own", owner = TestViewer.value))
        }
        source.observeMyProjects().first()

        assertEquals(listOf("joined"), ids(source.getParticipatingProjects()))
        assertEquals(listOf("owner", "participant"), catalog.lists.map { it.first.mine })
        assertEquals(listOf("own"), ids(source.observeMyProjects().first()), "the own list was read once and stays")
    }

    // ---- the owner filter and its guard ----

    @Test
    fun theOwnerReadAsksForOwnerAndNotForParticipant() = runTest {
        catalog.onList = { _, _ -> page(projectDto("a", owner = TestViewer.value)) }

        source.observeMyProjects().first()

        assertEquals(listOf("owner"), catalog.lists.map { it.first.mine })
    }

    @Test
    fun anOwnAnswerWithAProjectOfAnotherOwnerKeepsOnlyTheViewersAndLooksUpOnlyThem() = runTest {
        catalog.onList = { _, _ ->
            page(projectDto("foreign", owner = "somebody"), projectDto("own", owner = TestViewer.value))
        }

        val mine = source.observeMyProjects().first()

        assertEquals(listOf("own"), ids(mine))
        assertEquals(listOf(listOf("own")), assembler.asked.map { list -> list.map { it.id } })
        assertNull(source.observeProject(ProjectId("foreign")).first(), "the foreign project is not cached")
    }

    @Test
    fun aForeignProjectDoesNotUseUpTheIdOfALaterOwnOneWithTheSameId() = runTest {
        // The guard runs before the repeat check: a foreign "a" must not hide the viewer's own "a" that comes after it.
        catalog.onList = { _, _ -> page(projectDto("a", owner = "somebody"), projectDto("a", owner = TestViewer.value)) }

        val mine = source.observeMyProjects().first()

        assertEquals(listOf("a"), ids(mine))
        assertTrue(mine.single().viewerIsInitiator)
    }
}
