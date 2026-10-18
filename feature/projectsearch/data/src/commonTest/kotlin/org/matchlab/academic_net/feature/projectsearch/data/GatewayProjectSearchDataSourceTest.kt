package org.matchlab.academic_net.feature.projectsearch.data

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectDuration
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectFilters
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectSearchCapabilities
import org.matchlab.academic_net.gateway.ProjectCatalogApi
import org.matchlab.academic_net.gateway.ProjectSearchParams
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectInputDto
import org.matchlab.academic_net.gateway.dto.ProjectListDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import org.matchlab.academic_net.gateway.mapping.AssembledProject
import org.matchlab.academic_net.gateway.mapping.ProjectAssembler
import org.matchlab.academic_net.gateway.mapping.toProject

class GatewayProjectSearchDataSourceTest {
    private class ScriptedCatalog : ProjectCatalogApi {
        var answer = ProjectListDto()
        var failure: Throwable? = null
        val searches = mutableListOf<Pair<ProjectSearchParams, PageRequest>>()

        override suspend fun search(params: ProjectSearchParams, page: PageRequest): ProjectListDto {
            searches += params to page
            failure?.let { throw it }
            return answer
        }

        override suspend fun get(id: String): ProjectDto = throw AssertionError("The search never reads one project")
    }

    private class Assembler : ProjectAssembler {
        val owners = mutableMapOf<String, UserProfileDto>()
        var failure: Throwable? = null
        val asked = mutableListOf<List<ProjectDto>>()

        override suspend fun assemble(projects: List<ProjectDto>): List<AssembledProject> {
            asked += projects
            failure?.let { throw it }
            return projects.mapNotNull { dto ->
                val owner = owners[dto.ownerUserId]
                dto.toProject(UserId("viewer"), owner)?.let { AssembledProject(it, owner) }
            }
        }
    }

    private val api = ScriptedCatalog()
    private val assembler = Assembler()
    private val source = GatewayProjectSearchDataSource(api, assembler)

    private fun dto(id: String, owner: String = "owner-$id") =
        ProjectDto(id = id, ownerUserId = owner, details = ProjectInputDto(title = "Проект $id", field = "Biology"))

    private fun owner(id: String, university: String) =
        UserProfileDto(userId = id, details = UserProfileInputDto(displayName = "Имя $id", university = university))

    // ---- what is asked ----

    @Test
    fun bothFiltersThatNeedUndefinedValuesAreOff() {
        assertEquals(ProjectSearchCapabilities(typeFilter = false, durationFilter = false), source.capabilities)
    }

    @Test
    fun anEmptyQueryAsksForThePageAndNothingElse() = runTest {
        source.search(ProjectQuery(), PageRequest(40, 20))

        assertEquals(listOf(ProjectSearchParams() to PageRequest(40, 20)), api.searches)
    }

    @Test
    fun theTextGoesAsTypedWithOnlyTheOuterSpacesCut() = runTest {
        source.search(ProjectQuery(text = "  Python, machine learning  "), PageRequest.First)

        assertEquals("Python, machine learning", api.searches.single().first.q)
    }

    @Test
    fun aBlankTextIsNotSent() = runTest {
        source.search(ProjectQuery(text = "   \t"), PageRequest.First)

        assertNull(api.searches.single().first.q)
    }

    @Test
    fun theSphereGoesAsItsNameAndABlankNameIsNotSent() = runTest {
        source.search(ProjectQuery(filters = ProjectFilters(sphere = Sphere("cs", " Computer Science "))), PageRequest.First)
        source.search(ProjectQuery(filters = ProjectFilters(sphere = Sphere("x", "  "))), PageRequest.First)
        source.search(ProjectQuery(filters = ProjectFilters(sphere = null)), PageRequest.First)

        assertEquals("Computer Science", api.searches[0].first.field)
        assertNull(api.searches[1].first.field)
        assertNull(api.searches[2].first.field)
    }

    @Test
    fun theKeywordsGoAsTagsInOrderTrimmedAndWithoutBlanks() = runTest {
        source.search(ProjectQuery(filters = ProjectFilters(keywords = listOf(" ROS ", "", "  ", "Python"))), PageRequest.First)

        assertEquals(listOf("ROS", "Python"), api.searches.single().first.tags)
    }

    @Test
    fun theTypesAndTheDurationsAreNeverSent() = runTest {
        // API-GAP G40, G45: the filters are off in the capabilities, and even a query that has them does not send them.
        val filters = ProjectFilters(
            types = setOf(ProjectType.Scientific),
            durations = setOf(ProjectDuration.UpTo3Months),
        )

        source.search(ProjectQuery(filters = filters), PageRequest.First)

        assertEquals(ProjectSearchParams(), api.searches.single().first)
    }

    // ---- what comes back ----

    @Test
    fun theProjectsComeInTheOrderOfTheServerWithTheUniversityOfTheirOwner() = runTest {
        api.answer = ProjectListDto(items = listOf(dto("b"), dto("a"), dto("c")), total = 3)
        assembler.owners["owner-a"] = owner("owner-a", " НИУ ВШЭ ")
        assembler.owners["owner-b"] = owner("owner-b", "МГУ")

        val page = source.search(ProjectQuery(), PageRequest.First)

        assertEquals(listOf("b", "a", "c"), page.items.map { it.project.id.value })
        assertEquals(listOf("МГУ", "НИУ ВШЭ", ""), page.items.map { it.initiatorUniversity })
    }

    @Test
    fun theUniversityIsBlankWhenTheOwnerHasNoneOrNoProfile() = runTest {
        api.answer = ProjectListDto(items = listOf(dto("a"), dto("b")), total = 2)
        assembler.owners["owner-a"] = UserProfileDto(userId = "owner-a", details = UserProfileInputDto(displayName = "A", university = null))

        val page = source.search(ProjectQuery(), PageRequest.First)

        assertEquals(listOf("", ""), page.items.map { it.initiatorUniversity })
    }

    @Test
    fun thePageKeepsItsRequestTheTotalAndTheNextOffset() = runTest {
        api.answer = ProjectListDto(items = List(20) { dto("p$it") }, total = 57)

        val page = source.search(ProjectQuery(), PageRequest(20, 20))

        assertEquals(PageRequest(20, 20), page.request)
        assertEquals(57, page.total)
        assertEquals(20, page.received)
        assertEquals(PageRequest(40, 20), page.next())
        assertTrue(page.hasMore)
    }

    @Test
    fun theLastPageHasNoNext() = runTest {
        api.answer = ProjectListDto(items = List(7) { dto("p$it") }, total = 47)

        val page = source.search(ProjectQuery(), PageRequest(40, 20))

        assertEquals(47, page.total)
        assertFalse(page.hasMore)
        assertNull(page.next())
    }

    @Test
    fun aProjectTheAppCannotReadIsNotShownButTheOffsetCountsIt() = runTest {
        api.answer = ProjectListDto(items = listOf(dto("a"), dto("b", owner = ""), dto("", owner = "x"), dto("d")), total = 10)

        val page = source.search(ProjectQuery(), PageRequest(0, 20))

        assertEquals(listOf("a", "d"), page.items.map { it.project.id.value })
        assertEquals(4, page.received, "the next page starts after what the server sent, not after what is shown")
        assertEquals(PageRequest(4, 20), page.next())
    }

    @Test
    fun aTotalThatIsTooSmallDoesNotEndTheListEarly() = runTest {
        api.answer = ProjectListDto(items = List(20) { dto("p$it") }, total = 0)

        val page = source.search(ProjectQuery(), PageRequest(20, 20))

        assertEquals(40, page.total, "at least what has been received so far")
        assertFalse(page.hasMore)
    }

    @Test
    fun anEmptyAnswerIsAnEmptyPage() = runTest {
        val page = source.search(ProjectQuery(text = "ничего"), PageRequest.First)

        assertEquals(emptyList(), page.items)
        assertEquals(0, page.total)
        assertFalse(page.hasMore)
        assertEquals(emptyList(), assembler.asked.flatten(), "nobody to look up")
    }

    // ---- failures ----

    @Test
    fun aFailureOfTheSearchIsThrownAsItIs() = runTest {
        for (failure in listOf(RemoteFailure.Http(500), RemoteFailure.Http(400), RemoteFailure.NoConnection(), RemoteFailure.Timeout(), RemoteFailure.Malformed())) {
            api.failure = failure

            assertSame(failure, assertFailsWith<RemoteFailure> { source.search(ProjectQuery(), PageRequest.First) })
        }
    }

    @Test
    fun aFailureOfTheOwnersLookupFailsTheWholePage() = runTest {
        val failure = RemoteFailure.Http(500)
        api.answer = ProjectListDto(items = listOf(dto("a")), total = 1)
        assembler.failure = failure

        assertSame(failure, assertFailsWith<RemoteFailure.Http> { source.search(ProjectQuery(), PageRequest.First) })
    }

    @Test
    fun aCancellationPassesThrough() = runTest {
        api.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { source.search(ProjectQuery(), PageRequest.First) }
    }
}
