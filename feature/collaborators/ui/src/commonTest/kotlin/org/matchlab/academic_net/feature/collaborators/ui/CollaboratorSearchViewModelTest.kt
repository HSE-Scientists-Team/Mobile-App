package org.matchlab.academic_net.feature.collaborators.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorFilters
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorQuery
import org.matchlab.academic_net.feature.collaborators.domain.RoleNotFoundException
import org.matchlab.academic_net.feature.collaborators.domain.RoleReference
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirement
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * "08 Поиск коллабораторов" and its filters sheet "08b" against a fake server that lists seven people `u1`..`u7`, three to
 * a page. Time is virtual: the typing waits 300 ms, and so does the count of "Показать N".
 */
class CollaboratorSearchViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val repository = FakeCollaboratorsRepository(people(7))
    private val provider = FakeRoleProvider()
    private val role = RoleReference(ProjectId("p1"), RoleId("r1"))
    private val cause = IllegalStateException("no network")

    private fun viewModel(withRole: Boolean = false, pageSize: Int = 3) =
        CollaboratorSearchViewModel(role.takeIf { withRole }, uiDependencies(repository, provider), pageSize)

    private val CollaboratorSearchViewModel.state get() = uiState.value
    private val CollaboratorSearchUiState.ids get() = results.map { it.user.id.value }

    private fun firstPage(query: CollaboratorQuery = CollaboratorQuery()) = SearchCall(query, PageRequest(0, 3))

    // ---- the start without a role ----

    @Test
    fun withoutARoleTheFirstPageIsAskedAtOnceAndShown() {
        val viewModel = viewModel()

        assertEquals(listOf(firstPage()), repository.searches)
        with(viewModel.state) {
            assertFalse(isLoading)
            assertNull(error)
            assertNull(roleTitle)
            assertEquals("", query)
            assertEquals(CollaboratorFilters(), filters)
            assertEquals(userIds(1, 3), ids)
            assertEquals(7, total)
            assertTrue(hasMore)
            assertFalse(isLoadingMore)
            assertNull(loadMoreFailure)
            assertEquals(1, resultsVersion)
            assertNull(filtersSheet)
        }
    }

    @Test
    fun theScreenIsLoadingUntilTheFirstAnswerComes() {
        repository.gated = true

        val viewModel = viewModel()

        with(viewModel.state) {
            assertTrue(isLoading)
            assertEquals(emptyList(), results)
            assertEquals(0, resultsVersion)
        }
        repository.gates.single().open()
        with(viewModel.state) {
            assertFalse(isLoading)
            assertEquals(userIds(1, 3), ids)
            assertEquals(1, resultsVersion)
        }
    }

    @Test
    fun withoutAnyoneThereIsAnEmptyListNotAnError() {
        repository.server = emptyList()

        val viewModel = viewModel()

        with(viewModel.state) {
            assertFalse(isLoading)
            assertNull(error)
            assertEquals(emptyList(), results)
            assertEquals(0, total)
            assertFalse(hasMore)
        }
    }

    @Test
    fun aPageSizeOutsideOneToAHundredIsRefusedBeforeAnyRequest() {
        for (bad in listOf(0, -1, 101, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { viewModel(pageSize = bad) }
        }
        assertEquals(emptyList(), repository.searches)
    }

    @Test
    fun theBoundsOfThePageSizeAreTheLimitOfTheFirstRequest() {
        viewModel(pageSize = 1)
        viewModel(pageSize = 100)

        assertEquals(listOf(1, 100), repository.searches.map { it.page.limit })
    }

    @Test
    fun withoutAPageSizeAPageIsTwenty() {
        CollaboratorSearchViewModel(null, uiDependencies(repository, provider))

        assertEquals(PageRequest(offset = 0, limit = 20), repository.searches.single().page)
    }

    // ---- the start with a role ----

    @Test
    fun theSkillsOfTheRoleBecomeTheTextOfTheSearchBarAndThePageIsAskedWithThem() {
        val viewModel = viewModel(withRole = true)

        assertEquals(listOf(role.projectId to role.roleId), provider.asked)
        assertEquals("ML Engineer", viewModel.state.roleTitle)
        assertEquals("Python, Computer Vision", viewModel.state.query)
        assertEquals(listOf(firstPage(CollaboratorQuery("Python, Computer Vision"))), repository.searches)
        assertEquals(userIds(1, 3), viewModel.state.ids)
        assertFalse(viewModel.state.isLoading)
    }

    @Test
    fun aRoleWithoutSkillsLeavesTheSearchBarEmpty() {
        provider.requirement = RoleRequirement("Проект", "Дизайнер", emptyList())

        val viewModel = viewModel(withRole = true)

        assertEquals("", viewModel.state.query)
        assertEquals("Дизайнер", viewModel.state.roleTitle)
        assertEquals(listOf(firstPage()), repository.searches)
    }

    @Test
    fun theScreenIsLoadingWhileTheRoleComes() {
        provider.gate = Gate()

        val viewModel = viewModel(withRole = true)

        assertTrue(viewModel.state.isLoading)
        assertNull(viewModel.state.roleTitle)
        assertEquals(emptyList(), repository.searches)
    }

    @Test
    fun aTextTypedWhileTheRoleLoadsIsNotReplacedBySkillsAndIsSearchedOnce() = runTest {
        provider.gate = Gate()
        val viewModel = viewModel(withRole = true)

        viewModel.onQueryChange("my text")
        provider.gate!!.open()

        assertEquals("my text", viewModel.state.query)
        assertEquals("ML Engineer", viewModel.state.roleTitle)
        assertEquals(listOf(firstPage(CollaboratorQuery("my text"))), repository.searches)
        // The search that waited for the end of the typing is gone: the role started the search at once.
        passTime(1_000)
        assertEquals(1, repository.searches.size)
    }

    @Test
    fun aFailureOfTheRoleIsAFullScreenErrorAndNothingIsSearched() {
        provider.failure = cause

        val viewModel = viewModel(withRole = true)

        with(viewModel.state) {
            assertSame(cause, error)
            assertFalse(isLoading)
            assertNull(roleTitle)
            assertEquals(emptyList(), results)
        }
        assertEquals(emptyList(), repository.searches)
    }

    @Test
    fun aRoleThatDoesNotExistIsAnErrorToo() {
        provider.requirement = null

        val viewModel = viewModel(withRole = true)

        val error = assertIs<RoleNotFoundException>(viewModel.state.error)
        assertEquals(role.projectId, error.projectId)
        assertEquals(role.roleId, error.roleId)
    }

    @Test
    fun retryAfterAFailureOfTheRoleAsksTheRoleAgainAndThenSearches() {
        provider.failure = cause
        val viewModel = viewModel(withRole = true)
        provider.failure = null
        provider.gate = Gate()

        viewModel.onRetry()

        with(viewModel.state) {
            assertTrue(isLoading)
            assertNull(error)
        }
        assertEquals(2, provider.asked.size)
        provider.gate!!.open()
        with(viewModel.state) {
            assertFalse(isLoading)
            assertNull(error)
            assertEquals("Python, Computer Vision", query)
            assertEquals(userIds(1, 3), ids)
        }
        assertEquals(listOf(firstPage(CollaboratorQuery("Python, Computer Vision"))), repository.searches)
    }

    @Test
    fun aDoubleRetryOfTheRoleAsksItOnce() {
        provider.failure = cause
        val viewModel = viewModel(withRole = true)
        provider.failure = null
        provider.gate = Gate()

        viewModel.onRetry()
        viewModel.onRetry()

        assertEquals(2, provider.asked.size)
    }

    @Test
    fun retryAfterAFailedFirstPageDoesNotAskTheRoleAgain() {
        repository.failure = cause
        val viewModel = viewModel(withRole = true)
        assertSame(cause, viewModel.state.error)
        assertEquals("ML Engineer", viewModel.state.roleTitle)
        repository.failure = null

        viewModel.onRetry()

        assertEquals(1, provider.asked.size)
        assertEquals(2, repository.searches.size)
        assertNull(viewModel.state.error)
        assertEquals(userIds(1, 3), viewModel.state.ids)
    }

    // ---- nothing is asked for before the first answer ----

    @Test
    fun loadMoreAndItsRetryDoNothingWhileTheRoleLoads() {
        provider.gate = Gate()
        val viewModel = viewModel(withRole = true)

        viewModel.onLoadMore()
        viewModel.onRetryLoadMore()

        assertEquals(emptyList(), repository.searches)
    }

    @Test
    fun loadMoreAndItsRetryDoNothingWhileTheFirstPageLoads() {
        repository.gated = true
        val viewModel = viewModel()

        viewModel.onLoadMore()
        viewModel.onRetryLoadMore()

        assertEquals(1, repository.searches.size)
    }

    @Test
    fun retryWithoutAnErrorDoesNothingWhateverIsLoading() {
        provider.gate = Gate()
        val whileTheRoleLoads = viewModel(withRole = true)

        whileTheRoleLoads.onRetry()

        assertEquals(1, provider.asked.size)

        repository.gated = true
        val whileTheFirstPageLoads = viewModel()
        whileTheFirstPageLoads.onRetry()

        assertEquals(1, repository.searches.size)
    }

    @Test
    fun retryOnAScreenWithoutAnErrorDoesNothing() {
        val viewModel = viewModel(withRole = true)

        viewModel.onRetry()

        assertEquals(1, provider.asked.size)
        assertEquals(1, repository.searches.size)
        assertEquals(1, viewModel.state.resultsVersion)
    }

    // ---- typing ----

    @Test
    fun theTypedTextIsInTheStateAtOnceAndTheOldListStaysUntilTheAnswer() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("kotlin")

        with(viewModel.state) {
            assertEquals("kotlin", query)
            assertFalse(isLoading)
            assertEquals(userIds(1, 3), ids)
        }
        assertEquals(1, repository.searches.size)
    }

    @Test
    fun threeEditsWithinTheDelayMakeOneSearchWithTheLastText() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("k")
        passTime(100)
        viewModel.onQueryChange("ko")
        passTime(100)
        viewModel.onQueryChange("kot")

        passTime(299)
        assertEquals(1, repository.searches.size)

        passTime(1)
        assertEquals(2, repository.searches.size)
        assertEquals(firstPage(CollaboratorQuery("kot")), repository.searches.last())

        passTime(10_000)
        assertEquals(2, repository.searches.size)
    }

    @Test
    fun aSearchSubmitAsksAtOnceAndTheWaitingSearchIsGone() = runTest {
        val viewModel = viewModel()
        viewModel.onQueryChange("kot")

        viewModel.onSearchSubmit()

        assertEquals(2, repository.searches.size)
        assertEquals(firstPage(CollaboratorQuery("kot")), repository.searches.last())
        passTime(1_000)
        assertEquals(2, repository.searches.size)
    }

    @Test
    fun applyingTheFiltersWhileATypedSearchWaitsMakesOneSearchWithTheTextAndTheFilters() = runTest {
        val viewModel = viewModel()
        val draft = CollaboratorFilters(hasPublications = true)

        viewModel.onQueryChange("kot")
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(draft)
        viewModel.onFiltersApply()

        // The filters were applied before the typed search could start: one search, and it has both.
        assertEquals(2, repository.searches.size)
        assertEquals(firstPage(CollaboratorQuery("kot", draft)), repository.searches.last())
        passTime(1_000)
        assertEquals(2, repository.searches.size)
        assertEquals(emptyList(), repository.counts)
    }

    @Test
    fun typingSearchSubmitAndApplyDoNotShowTheFullScreenLoading() = runTest {
        val viewModel = viewModel()
        repository.gated = true

        viewModel.onQueryChange("a")
        passTime(300)
        with(viewModel.state) {
            assertFalse(isLoading)
            assertEquals(userIds(1, 3), ids)
        }

        viewModel.onSearchSubmit()
        with(viewModel.state) {
            assertFalse(isLoading)
            assertEquals(userIds(1, 3), ids)
        }

        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(CollaboratorFilters(verifiedProfile = true))
        viewModel.onFiltersApply()
        with(viewModel.state) {
            assertFalse(isLoading)
            assertEquals(userIds(1, 3), ids)
        }
    }

    // ---- the next pages ----

    @Test
    fun theNextPagesAreAppendedUntilTheListIsOver() {
        val viewModel = viewModel()

        viewModel.onLoadMore()

        assertEquals(SearchCall(CollaboratorQuery(), PageRequest(3, 3)), repository.searches.last())
        with(viewModel.state) {
            assertEquals(userIds(1, 6), ids)
            assertEquals(7, total)
            assertTrue(hasMore)
            assertFalse(isLoadingMore)
            assertEquals(1, resultsVersion)
        }

        viewModel.onLoadMore()

        assertEquals(SearchCall(CollaboratorQuery(), PageRequest(6, 3)), repository.searches.last())
        with(viewModel.state) {
            assertEquals(userIds(1, 7), ids)
            assertFalse(hasMore)
            assertFalse(isLoadingMore)
            assertEquals(1, resultsVersion)
        }

        viewModel.onLoadMore()

        assertEquals(3, repository.searches.size)
    }

    @Test
    fun aPersonWhoShiftedIntoTheNextPageIsShownOnce() {
        // A person joined at the top meanwhile: "u3" is the last of the first page and the first of the second.
        repository.serve = { _, page ->
            if (page.offset == 3) Page(listOf(person("u3"), person("u4"), person("u5")), 7, page) else repository.sliceOfServer(page)
        }
        val viewModel = viewModel()

        viewModel.onLoadMore()

        assertEquals(userIds(1, 5), viewModel.state.ids)
        // The page had three records, so the next one starts after them.
        viewModel.onLoadMore()
        assertEquals(PageRequest(6, 3), repository.searches.last().page)
        assertEquals(userIds(1, 5) + "u7", viewModel.state.ids)
    }

    @Test
    fun theNextPageIsAskedForTheQueryOfTheListOnTheScreen() = runTest {
        val viewModel = viewModel()
        val filters = CollaboratorFilters(verifiedProfile = true)
        viewModel.onQueryChange("kot")
        passTime(300)
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(filters)
        viewModel.onFiltersApply()

        viewModel.onLoadMore()

        assertEquals(SearchCall(CollaboratorQuery("kot", filters), PageRequest(3, 3)), repository.searches.last())
    }

    @Test
    fun aSecondLoadMoreWhileOneIsOnItsWayAsksNothing() {
        val viewModel = viewModel()
        repository.gated = true

        viewModel.onLoadMore()
        assertTrue(viewModel.state.isLoadingMore)
        viewModel.onLoadMore()
        viewModel.onLoadMore()

        assertEquals(2, repository.searches.size)
        repository.gates.single().open()
        with(viewModel.state) {
            assertFalse(isLoadingMore)
            assertEquals(userIds(1, 6), ids)
        }
    }

    @Test
    fun nothingIsAskedForOnceTheListIsOver() {
        repository.server = people(2)
        val viewModel = viewModel()
        assertFalse(viewModel.state.hasMore)

        viewModel.onLoadMore()

        assertEquals(1, repository.searches.size)
    }

    @Test
    fun nothingIsAskedForWhileATypedSearchWaitsAndAskedForTheNewQueryAfterIt() = runTest {
        val viewModel = viewModel()
        viewModel.onQueryChange("x")

        viewModel.onLoadMore()

        assertEquals(1, repository.searches.size)
        passTime(300)
        assertEquals(2, repository.searches.size)

        viewModel.onLoadMore()

        assertEquals(SearchCall(CollaboratorQuery("x"), PageRequest(3, 3)), repository.searches.last())
    }

    @Test
    fun nothingIsAskedForWhileANewSearchIsOnItsWay() {
        val viewModel = viewModel()
        repository.gated = true
        viewModel.onSearchSubmit()

        viewModel.onLoadMore()

        assertEquals(2, repository.searches.size)
    }

    @Test
    fun nothingIsAskedForAfterAFailedFirstPage() {
        repository.failure = cause
        val viewModel = viewModel()

        viewModel.onLoadMore()
        viewModel.onRetryLoadMore()

        assertEquals(1, repository.searches.size)
    }

    // ---- a next page that fails ----

    @Test
    fun aFailedNextPageKeepsTheListAndNothingIsAskedUntilRetry() {
        val viewModel = viewModel()
        repository.failure = cause

        viewModel.onLoadMore()

        with(viewModel.state) {
            assertSame(cause, loadMoreFailure)
            assertFalse(isLoadingMore)
            assertEquals(userIds(1, 3), ids)
            assertTrue(hasMore)
            assertNull(error)
        }
        // The end of the list is still near and the screen asks again.
        viewModel.onLoadMore()
        assertEquals(2, repository.searches.size)

        repository.failure = null
        viewModel.onRetryLoadMore()

        assertEquals(SearchCall(CollaboratorQuery(), PageRequest(3, 3)), repository.searches.last())
        assertEquals(3, repository.searches.size)
        with(viewModel.state) {
            assertNull(loadMoreFailure)
            assertEquals(userIds(1, 6), ids)
        }
    }

    @Test
    fun aDoubleRetryOfTheFooterAsksOnce() {
        val viewModel = viewModel()
        repository.failure = cause
        viewModel.onLoadMore()
        repository.failure = null
        repository.gated = true

        viewModel.onRetryLoadMore()
        viewModel.onRetryLoadMore()

        assertEquals(3, repository.searches.size)
        assertNull(viewModel.state.loadMoreFailure)
        assertTrue(viewModel.state.isLoadingMore)
    }

    @Test
    fun aRetryThatFailsAgainShowsTheFooterFailureAgain() {
        val viewModel = viewModel()
        repository.failure = cause
        viewModel.onLoadMore()

        viewModel.onRetryLoadMore()

        assertSame(cause, viewModel.state.loadMoreFailure)
        assertFalse(viewModel.state.isLoadingMore)
        assertEquals(3, repository.searches.size)
    }

    @Test
    fun retryOfTheFooterWithoutAFailureDoesNothing() {
        val viewModel = viewModel()

        viewModel.onRetryLoadMore()

        assertEquals(1, repository.searches.size)
    }

    @Test
    fun aNewSearchTakesTheFooterFailureAway() = runTest {
        val viewModel = viewModel()
        repository.failure = cause
        viewModel.onLoadMore()
        repository.failure = null

        viewModel.onSearchSubmit()

        with(viewModel.state) {
            assertNull(loadMoreFailure)
            assertEquals(userIds(1, 3), ids)
            assertEquals(2, resultsVersion)
        }
    }

    // ---- searches that overtake each other ----

    @Test
    fun aNewTextWhileLoadingMoreCancelsTheLoadingOfThePage() = runTest {
        val viewModel = viewModel()
        repository.gated = true
        viewModel.onLoadMore()
        assertTrue(viewModel.state.isLoadingMore)

        viewModel.onQueryChange("x")

        assertFalse(viewModel.state.isLoadingMore)
        repository.gates.single().open()
        with(viewModel.state) {
            assertEquals(userIds(1, 3), ids)
            assertNull(loadMoreFailure)
            assertEquals(1, resultsVersion)
        }
    }

    @Test
    fun theAnswerOfAnOlderSearchNeverOverwritesANewerOne() = runTest {
        val viewModel = viewModel()
        repository.gated = true
        repository.lateAnswers = true
        val answered = mutableListOf<String>()
        repository.serve = { query, page ->
            answered += query.text
            when (query.text) {
                "a" -> Page(listOf(person("a1"), person("a2")), 2, page)
                "b" -> Page(listOf(person("b1")), 1, page)
                else -> repository.sliceOfServer(page)
            }
        }
        viewModel.onQueryChange("a")
        passTime(300)
        viewModel.onQueryChange("b")
        passTime(300)
        assertEquals(2, repository.gates.size)

        repository.gates[1].open()
        assertEquals(listOf("b1"), viewModel.state.ids)
        assertEquals(2, viewModel.state.resultsVersion)

        // The request for "a" was given up, but its answer arrives last.
        repository.gates[0].open()
        assertEquals(listOf("b", "a"), answered, "the old answer was delivered, and the screen ignored it")
        with(viewModel.state) {
            assertEquals(listOf("b1"), ids)
            assertEquals(1, total)
            assertFalse(hasMore)
            assertEquals(2, resultsVersion)
            assertEquals("b", query)
        }
    }

    @Test
    fun theFailureOfAnOlderSearchNeverReplacesANewerResult() = runTest {
        val viewModel = viewModel()
        repository.gated = true
        repository.lateAnswers = true
        repository.serve = { query, page ->
            if (query.text == "a") throw cause else Page(listOf(person("b1")), 1, page)
        }
        viewModel.onQueryChange("a")
        passTime(300)
        viewModel.onQueryChange("b")
        passTime(300)
        repository.gates[1].open()

        repository.gates[0].open()

        with(viewModel.state) {
            assertNull(error)
            assertEquals(listOf("b1"), ids)
        }
    }

    @Test
    fun theAnswerOfAnOlderPageNeverTouchesANewSearch() {
        val viewModel = viewModel()
        repository.gated = true
        repository.lateAnswers = true
        val answered = mutableListOf<Int>()
        repository.serve = { _, page ->
            answered += page.offset
            repository.sliceOfServer(page)
        }
        viewModel.onLoadMore()
        viewModel.onSearchSubmit()
        assertEquals(2, repository.gates.size)

        repository.gates[1].open()
        assertEquals(userIds(1, 3), viewModel.state.ids)
        assertFalse(viewModel.state.isLoadingMore)

        // The page 4..6 of the old list arrives last and is not appended to the new one.
        repository.gates[0].open()
        assertEquals(listOf(0, 3), answered, "the old page was delivered, and the screen ignored it")
        with(viewModel.state) {
            assertEquals(userIds(1, 3), ids)
            assertEquals(7, total)
            assertTrue(hasMore)
            assertFalse(isLoadingMore)
            assertNull(loadMoreFailure)
        }
    }

    @Test
    fun theFailureOfAnOlderPageNeverShowsAFooterFailureOfANewSearch() {
        val viewModel = viewModel()
        repository.gated = true
        repository.lateAnswers = true
        repository.serve = { _, page -> if (page.offset == 3) throw cause else repository.sliceOfServer(page) }
        viewModel.onLoadMore()
        viewModel.onSearchSubmit()
        repository.gates[1].open()

        repository.gates[0].open()

        assertNull(viewModel.state.loadMoreFailure)
        assertEquals(userIds(1, 3), viewModel.state.ids)
    }

    // ---- a first page that fails ----

    @Test
    fun aFailedFirstPageIsAFullScreenErrorWithNoResults() {
        repository.failure = cause

        val viewModel = viewModel()

        with(viewModel.state) {
            assertSame(cause, error)
            assertFalse(isLoading)
            assertEquals(emptyList(), results)
            assertEquals(0, total)
            assertFalse(hasMore)
            assertEquals(0, resultsVersion)
        }
    }

    @Test
    fun retryShowsTheLoadingAndThenTheResults() {
        repository.failure = cause
        val viewModel = viewModel()
        repository.failure = null
        repository.gated = true

        viewModel.onRetry()

        with(viewModel.state) {
            assertTrue(isLoading)
            assertNull(error)
        }
        repository.gates.single().open()
        with(viewModel.state) {
            assertFalse(isLoading)
            assertNull(error)
            assertEquals(userIds(1, 3), ids)
            assertEquals(1, resultsVersion)
        }
    }

    @Test
    fun aDoubleRetryAsksOnce() {
        repository.failure = cause
        val viewModel = viewModel()
        repository.failure = null
        repository.gated = true

        viewModel.onRetry()
        viewModel.onRetry()

        assertEquals(2, repository.searches.size)
    }

    @Test
    fun retryThatFailsAgainIsTheErrorAgain() {
        repository.failure = cause
        val viewModel = viewModel()

        viewModel.onRetry()

        assertSame(cause, viewModel.state.error)
        assertFalse(viewModel.state.isLoading)
        assertEquals(2, repository.searches.size)
    }

    @Test
    fun aFailedNewSearchTakesAwayThePreviousResultsAndTheTotal() = runTest {
        val viewModel = viewModel()
        repository.failure = cause

        viewModel.onQueryChange("x")
        assertEquals(userIds(1, 3), viewModel.state.ids) // the old list stays until the answer
        passTime(300)

        with(viewModel.state) {
            assertSame(cause, error)
            assertFalse(isLoading)
            assertEquals(emptyList(), results)
            assertEquals(0, total)
            assertFalse(hasMore)
            assertEquals(1, resultsVersion)
        }
    }

    @Test
    fun aSuccessAfterAnErrorClearsTheError() = runTest {
        val viewModel = viewModel()
        repository.failure = cause
        viewModel.onQueryChange("x")
        passTime(300)
        assertSame(cause, viewModel.state.error)
        repository.failure = null
        repository.gated = true

        viewModel.onQueryChange("y")
        passTime(300)
        // A typed search does not take the screen over: the error stays until the answer.
        with(viewModel.state) {
            assertSame(cause, error)
            assertFalse(isLoading)
        }
        repository.gates.single().open()

        with(viewModel.state) {
            assertNull(error)
            assertEquals(userIds(1, 3), ids)
            assertEquals(7, total)
            assertEquals(2, resultsVersion)
        }
    }

    @Test
    fun retryWaitsWhileATypedSearchIsPending() = runTest {
        repository.failure = cause
        val viewModel = viewModel()
        repository.failure = null
        viewModel.onQueryChange("z")

        viewModel.onRetry()

        assertEquals(1, repository.searches.size)
        passTime(300)
        assertEquals(2, repository.searches.size)
        assertEquals("z", repository.searches.last().query.text)
        assertNull(viewModel.state.error)
    }

    // ---- the version of the results ----

    @Test
    fun theVersionGrowsWithEachNewFirstPageAndNotWithTheNextOnes() = runTest {
        val viewModel = viewModel()
        assertEquals(1, viewModel.state.resultsVersion)

        viewModel.onLoadMore()
        assertEquals(1, viewModel.state.resultsVersion)

        viewModel.onSearchSubmit()
        assertEquals(2, viewModel.state.resultsVersion)

        viewModel.onQueryChange("x")
        passTime(300)
        assertEquals(3, viewModel.state.resultsVersion)

        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(CollaboratorFilters(verifiedProfile = true))
        viewModel.onFiltersApply()
        assertEquals(4, viewModel.state.resultsVersion)

        repository.failure = cause
        viewModel.onSearchSubmit()
        assertEquals(4, viewModel.state.resultsVersion)
    }

    // ---- the filters sheet ----

    private val draft = CollaboratorFilters(verifiedProfile = true)

    @Test
    fun theSheetOpenedOverTheCurrentResultsShowsTheirTotalWithoutARequest() = runTest {
        val viewModel = viewModel()

        viewModel.onFiltersClick()

        assertEquals(FiltersSheetState(CollaboratorFilters(), previewCount = 7), viewModel.state.filtersSheet)
        passTime(1_000)
        assertEquals(emptyList(), repository.counts)
    }

    @Test
    fun theSheetStartsFromTheFiltersOfTheSearch() = runTest {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(draft)
        viewModel.onFiltersApply()

        viewModel.onFiltersClick()

        assertEquals(FiltersSheetState(draft, previewCount = 7), viewModel.state.filtersSheet)
        assertEquals(emptyList(), repository.counts)
    }

    @Test
    fun editingTheDraftHidesTheNumberAndCountsOnceAfterTheDelay() = runTest {
        val viewModel = viewModel()
        repository.countAnswer = { 5 }
        viewModel.onFiltersClick()

        viewModel.onFiltersDraftChange(draft)

        assertEquals(FiltersSheetState(draft, previewCount = null), viewModel.state.filtersSheet)
        passTime(299)
        assertEquals(emptyList(), repository.counts)
        passTime(1)
        assertEquals(listOf(CollaboratorQuery("", draft)), repository.counts)
        assertEquals(FiltersSheetState(draft, previewCount = 5), viewModel.state.filtersSheet)
    }

    @Test
    fun severalEditsWithinTheDelayCountOnlyTheLastDraft() = runTest {
        val viewModel = viewModel()
        val second = CollaboratorFilters(hasPublications = true)
        val third = CollaboratorFilters(hasPublications = true, university = "МГУ")
        repository.countAnswer = { 8 }
        viewModel.onFiltersClick()

        viewModel.onFiltersDraftChange(draft)
        passTime(200)
        viewModel.onFiltersDraftChange(second)
        passTime(200)
        viewModel.onFiltersDraftChange(third)
        passTime(300)

        assertEquals(listOf(CollaboratorQuery("", third)), repository.counts)
        assertEquals(FiltersSheetState(third, previewCount = 8), viewModel.state.filtersSheet)
    }

    @Test
    fun theCountIsOfTheTextOfTheSearchBar() = runTest {
        val viewModel = viewModel()
        viewModel.onQueryChange("kot")
        passTime(300)
        viewModel.onFiltersClick()

        viewModel.onFiltersDraftChange(draft)
        passTime(300)

        assertEquals(listOf(CollaboratorQuery("kot", draft)), repository.counts)
    }

    @Test
    fun theCountOfADraftThatWasChangedMeanwhileIsNotApplied() = runTest {
        val viewModel = viewModel()
        val first = CollaboratorFilters(verifiedProfile = true)
        val second = CollaboratorFilters(hasPublications = true)
        repository.countGated = true
        repository.lateAnswers = true
        repository.countAnswer = { query -> if (query.filters == first) 11 else 22 }
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(first)
        passTime(300)
        viewModel.onFiltersDraftChange(second)
        passTime(300)
        assertEquals(2, repository.countGates.size)

        repository.countGates[0].open()
        assertEquals(FiltersSheetState(second, previewCount = null), viewModel.state.filtersSheet)

        repository.countGates[1].open()
        assertEquals(FiltersSheetState(second, previewCount = 22), viewModel.state.filtersSheet)
    }

    @Test
    fun aFailedCountLeavesTheButtonWithoutANumberAndShowsNoError() = runTest {
        val viewModel = viewModel()
        repository.countFailure = cause
        viewModel.onFiltersClick()

        viewModel.onFiltersDraftChange(draft)
        passTime(300)

        assertEquals(FiltersSheetState(draft, previewCount = null), viewModel.state.filtersSheet)
        assertNull(viewModel.state.error)
        assertEquals(1, repository.counts.size)
    }

    @Test
    fun closingTheSheetCancelsTheCountThatWaits() = runTest {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(draft)

        viewModel.onFiltersDismiss()

        assertNull(viewModel.state.filtersSheet)
        passTime(1_000)
        assertEquals(emptyList(), repository.counts)
        assertEquals(CollaboratorFilters(), viewModel.state.filters)
    }

    @Test
    fun theCountThatArrivesAfterTheSheetWasClosedDoesNotBringItBack() = runTest {
        val viewModel = viewModel()
        repository.countGated = true
        repository.lateAnswers = true
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(draft)
        passTime(300)
        assertEquals(1, repository.countGates.size)

        viewModel.onFiltersDismiss()
        repository.countGates.single().open()

        assertNull(viewModel.state.filtersSheet)
    }

    @Test
    fun applyingTheDraftClosesTheSheetSetsTheFiltersAndSearchesAtOnce() = runTest {
        val viewModel = viewModel()
        val applied = CollaboratorFilters(verifiedProfile = true, university = "МГУ")
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(applied)

        viewModel.onFiltersApply()

        with(viewModel.state) {
            assertEquals(applied, filters)
            assertNull(filtersSheet)
            assertFalse(isLoading)
            assertEquals(2, resultsVersion)
        }
        assertEquals(firstPage(CollaboratorQuery("", applied)), repository.searches.last())
        assertEquals(2, repository.searches.size)
        // The count that waited is cancelled with the sheet.
        passTime(1_000)
        assertEquals(emptyList(), repository.counts)
        assertEquals(2, repository.searches.size)
    }

    @Test
    fun applyingWithoutASheetDoesNothing() {
        val viewModel = viewModel()

        viewModel.onFiltersApply()

        assertEquals(1, repository.searches.size)
        assertEquals(CollaboratorFilters(), viewModel.state.filters)
    }

    @Test
    fun resetPutsEveryFilterOfTheDraftOffAndCountsThem() = runTest {
        val viewModel = viewModel()
        repository.countAnswer = { 7 }
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(draft)
        passTime(300)

        viewModel.onFiltersReset()

        assertEquals(FiltersSheetState(CollaboratorFilters(), previewCount = null), viewModel.state.filtersSheet)
        passTime(300)
        assertEquals(CollaboratorQuery("", CollaboratorFilters()), repository.counts.last())
        assertEquals(FiltersSheetState(CollaboratorFilters(), previewCount = 7), viewModel.state.filtersSheet)
    }

    @Test
    fun aSheetOpenedWhileATypedSearchWaitsCountsInsteadOfShowingTheOldTotal() = runTest {
        val viewModel = viewModel()
        repository.countAnswer = { 9 }
        viewModel.onQueryChange("x")

        viewModel.onFiltersClick()

        assertEquals(FiltersSheetState(CollaboratorFilters(), previewCount = null), viewModel.state.filtersSheet)
        assertEquals(emptyList(), repository.counts)
        passTime(300)
        assertEquals(listOf(CollaboratorQuery("x", CollaboratorFilters())), repository.counts)
        assertEquals(9, viewModel.state.filtersSheet?.previewCount)
    }

    @Test
    fun aSheetOpenedOverAnErrorCounts() = runTest {
        repository.failure = cause
        val viewModel = viewModel()
        repository.countAnswer = { 4 }

        viewModel.onFiltersClick()

        assertNull(viewModel.state.filtersSheet?.previewCount)
        passTime(300)
        assertEquals(listOf(CollaboratorQuery()), repository.counts)
        assertEquals(4, viewModel.state.filtersSheet?.previewCount)
    }

    @Test
    fun aSheetOpenedWhileTheFirstPageLoadsCounts() = runTest {
        repository.gated = true
        val viewModel = viewModel()

        viewModel.onFiltersClick()

        assertNull(viewModel.state.filtersSheet?.previewCount)
        passTime(300)
        assertEquals(1, repository.counts.size)
    }

    @Test
    fun theTypesOfWorksOfTheDraftAreHandedOnAsTheyAre() = runTest {
        val viewModel = viewModel()
        val typed = CollaboratorFilters(workTypes = setOf(ProjectType.Scientific))
        viewModel.onFiltersClick()

        viewModel.onFiltersDraftChange(typed)
        passTime(300)
        viewModel.onFiltersApply()

        // It is the data source that does not send them (API-GAP G11); the screen hides the group, so the draft has none.
        assertEquals(setOf(ProjectType.Scientific), repository.counts.single().filters.workTypes)
        assertEquals(typed, repository.searches.last().query.filters)
    }

    // ---- late calls of a sheet that is closed ----

    /**
     * The sheet is shut and stays so: nothing was counted, no new search began since [searches] requests, and the filters of
     * the search are still [filters]. Time runs on, so that a count that was started by mistake would have been asked.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.assertTheSheetStaysShut(
        viewModel: CollaboratorSearchViewModel,
        filters: CollaboratorFilters,
        searches: Int,
    ) {
        passTime(1_000)
        advanceUntilIdle()
        assertNull(viewModel.state.filtersSheet)
        assertEquals(emptyList(), repository.counts)
        assertEquals(filters, viewModel.state.filters)
        assertEquals(searches, repository.searches.size)
    }

    @Test
    fun aLateDraftChangeAfterTheSheetWasDismissedDoesNotOpenItAgain() = runTest {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onFiltersDismiss()

        viewModel.onFiltersDraftChange(draft)

        assertTheSheetStaysShut(viewModel, CollaboratorFilters(), searches = 1)
    }

    @Test
    fun aLateResetAfterTheSheetWasDismissedDoesNotOpenItAgain() = runTest {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onFiltersDismiss()

        viewModel.onFiltersReset()

        assertTheSheetStaysShut(viewModel, CollaboratorFilters(), searches = 1)
    }

    @Test
    fun aLateDraftChangeAfterApplyingDoesNotOpenTheSheetAgainNorTouchTheFilters() = runTest {
        val viewModel = viewModel()
        val applied = CollaboratorFilters(verifiedProfile = true, university = "МГУ")
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(applied)
        viewModel.onFiltersApply()
        val searches = repository.searches.size

        viewModel.onFiltersDraftChange(CollaboratorFilters(hasPublications = true))

        assertTheSheetStaysShut(viewModel, applied, searches)
    }

    @Test
    fun aLateResetAfterApplyingDoesNotOpenTheSheetAgainNorClearTheFilters() = runTest {
        val viewModel = viewModel()
        val applied = CollaboratorFilters(verifiedProfile = true, university = "МГУ")
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(applied)
        viewModel.onFiltersApply()
        val searches = repository.searches.size

        viewModel.onFiltersReset()

        assertTheSheetStaysShut(viewModel, applied, searches)
    }

    @Test
    fun aDraftChangeAndAResetWithoutAnyOpenSheetDoNothing() = runTest {
        val viewModel = viewModel()

        viewModel.onFiltersDraftChange(draft)
        viewModel.onFiltersReset()

        assertTheSheetStaysShut(viewModel, CollaboratorFilters(), searches = 1)
    }

    @Test
    fun theSheetOpensAndWorksAgainAfterLateCallsWereIgnored() = runTest {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onFiltersDismiss()
        viewModel.onFiltersDraftChange(draft)
        viewModel.onFiltersReset()
        passTime(1_000)
        assertNull(viewModel.state.filtersSheet)
        assertEquals(emptyList(), repository.counts)

        // The ignored calls leave nothing behind: the next opening is the usual one, and it counts as usual.
        viewModel.onFiltersClick()
        assertEquals(FiltersSheetState(CollaboratorFilters(), previewCount = 7), viewModel.state.filtersSheet)
        viewModel.onFiltersDraftChange(draft)
        passTime(300)

        assertEquals(listOf(CollaboratorQuery("", draft)), repository.counts)
        assertEquals(FiltersSheetState(draft, previewCount = 7), viewModel.state.filtersSheet)
    }
}
