package org.matchlab.academic_net.feature.projectsearch.ui

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectDuration
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectFilters
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectSearchCapabilities
import org.matchlab.academic_net.common.model.ProjectType

/**
 * "02 Поиск проектов" and its filters "03" against a fake server that lists seven projects `p1`..`p7`, three to a page.
 * Time is virtual: the typing waits 300 ms.
 */
class ProjectSearchViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val repository = FakeProjectSearchRepository(foundProjects(7))
    private val spheres = FakeSphereProvider()
    private val cause = IllegalStateException("no network")

    private fun viewModel(pageSize: Int = 3) = ProjectSearchViewModel(uiDependencies(repository, spheres), pageSize)

    private val ProjectSearchViewModel.state get() = uiState.value
    private val ProjectSearchUiState.ids get() = results.map { it.id.value }

    private fun firstPage(query: ProjectQuery = ProjectQuery()) = SearchCall(query, PageRequest(0, 3))

    // ---- the start ----

    @Test
    fun theFirstPageIsAskedAtOnceAndShown() {
        val viewModel = viewModel()

        assertEquals(listOf(firstPage()), repository.searches)
        with(viewModel.state) {
            assertFalse(isLoading)
            assertNull(error)
            assertEquals("", query)
            assertEquals(ProjectFilters(), filters)
            assertEquals(projectIds(1, 3), ids)
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
            assertFalse(hasMore)
        }
        repository.gates.single().open()
        with(viewModel.state) {
            assertFalse(isLoading)
            assertEquals(projectIds(1, 3), ids)
            assertEquals(1, resultsVersion)
        }
    }

    @Test
    fun withoutAnyProjectThereIsAnEmptyListNotAnError() {
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
        ProjectSearchViewModel(uiDependencies(repository, spheres))

        assertEquals(PageRequest(offset = 0, limit = 20), repository.searches.single().page)
    }

    // ---- the card ----

    @Test
    fun aCardHasEverythingTheProjectGivesAndNothingMadeUp() {
        repository.server = listOf(found("1", university = "НИУ ВШЭ", roles = 3))

        val item = viewModel().state.results.single()

        assertEquals(
            ProjectSearchItem(
                id = ProjectId("p1"),
                title = "Проект 1",
                initiatorName = "Автор 1",
                initiatorUniversity = "НИУ ВШЭ",
                initiatorAvatarKey = "u1",
                isOwnProject = false,
                type = null,
                status = null,
                sphere = "Computer Science",
                rolesCount = 3,
                period = null,
            ),
            item,
        )
    }

    @Test
    fun aProjectOfUnknownValuesIsListedWithoutThem() {
        repository.server = listOf(found("1", sphere = null, type = null, status = null, period = null), found("2"))

        val items = viewModel().state.results

        assertEquals(listOf("p1", "p2"), items.map { it.id.value })
        with(items.first()) {
            assertNull(sphere)
            assertNull(type)
            assertNull(status)
            assertNull(period)
            assertEquals("Проект 1", title)
        }
    }

    @Test
    fun theKnownValuesOfAProjectReachItsCard() {
        val period = ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3))
        repository.server = listOf(
            found("1", sphere = Biology, type = ProjectType.Practical, status = ProjectStatus.Recruiting, period = period),
        )

        with(viewModel().state.results.single()) {
            assertEquals("Биология", sphere)
            assertEquals(ProjectType.Practical, type)
            assertEquals(ProjectStatus.Recruiting, status)
            assertEquals(period, this.period)
        }
    }

    @Test
    fun theViewersOwnProjectIsMarked() {
        repository.server = listOf(found("1", own = true), found("2"))

        val items = viewModel().state.results

        assertEquals(listOf(true, false), items.map { it.isOwnProject })
    }

    // ---- the next pages ----

    @Test
    fun loadMoreAppendsTheNextPageAndKeepsTheVersion() {
        val viewModel = viewModel()

        viewModel.onLoadMore()

        assertEquals(listOf(firstPage(), SearchCall(ProjectQuery(), PageRequest(3, 3))), repository.searches)
        with(viewModel.state) {
            assertEquals(projectIds(1, 6), ids)
            assertEquals(7, total)
            assertTrue(hasMore)
            assertFalse(isLoadingMore)
            assertEquals(1, resultsVersion, "the list is not shown from its start again")
        }
    }

    @Test
    fun theLastPageEndsTheList() {
        val viewModel = viewModel()

        viewModel.onLoadMore()
        viewModel.onLoadMore()

        assertEquals(projectIds(1, 7), viewModel.state.ids)
        assertFalse(viewModel.state.hasMore)
        viewModel.onLoadMore()
        assertEquals(3, repository.searches.size, "nothing is asked for once the list is over")
    }

    @Test
    fun aSecondLoadMoreWhileThePageIsOnItsWayAsksNothing() {
        val viewModel = viewModel()
        repository.gated = true

        viewModel.onLoadMore()
        viewModel.onLoadMore()
        viewModel.onLoadMore()

        assertEquals(2, repository.searches.size)
        assertTrue(viewModel.state.isLoadingMore)
        repository.gates.single().open()
        assertEquals(projectIds(1, 6), viewModel.state.ids)
        assertFalse(viewModel.state.isLoadingMore)
    }

    @Test
    fun aProjectThatTheServersListShiftedOntoTheNextPageIsShownOnce() {
        val viewModel = viewModel()
        // The list shifted: the next page starts with the last project of the first one.
        repository.serve = { _, page -> Page(projectsOf(3, 4, 5, offsetOf = page), total = 7, request = page) }

        viewModel.onLoadMore()

        assertEquals(projectIds(1, 5), viewModel.state.ids)
        assertEquals(7, viewModel.state.total)
    }

    @Test
    fun aPageOfOnlyRepeatsStillMovesTheListOn() {
        val viewModel = viewModel()
        repository.serve = { _, page ->
            if (page.offset == 3) Page(projectsOf(1, 2, 3, offsetOf = page), total = 7, request = page) else sliceFor(page)
        }

        viewModel.onLoadMore()
        assertEquals(projectIds(1, 3), viewModel.state.ids)
        assertTrue(viewModel.state.hasMore)
        viewModel.onLoadMore()

        // The next page starts after what the server sent, repeats and all (API-GAP G53/G34: a record the list shifted
        // away may be missed, but the list never stops on a page of repeats).
        assertEquals(PageRequest(6, 3), repository.searches.last().page)
        assertEquals(listOf("p1", "p2", "p3", "p7"), viewModel.state.ids)
        assertFalse(viewModel.state.hasMore)
    }

    @Test
    fun nothingIsAskedForWhileTheTypingWaitsToBeSearched() = runTest {
        val viewModel = viewModel()
        viewModel.onQueryChange("py")

        viewModel.onLoadMore()

        assertEquals(1, repository.searches.size)
        passTime(300)
        assertEquals(2, repository.searches.size)
    }

    @Test
    fun theListHasNoMoreWhileANewSearchIsOnItsWay() = runTest {
        val viewModel = viewModel()
        viewModel.onQueryChange("py")
        repository.gated = true
        passTime(300)

        with(viewModel.state) {
            assertFalse(hasMore, "the old list has no next page for the new query")
            assertEquals(projectIds(1, 3), ids, "the old results stay until the answer comes")
            assertFalse(isLoading)
        }
        viewModel.onLoadMore()
        assertEquals(2, repository.searches.size)
    }

    // ---- the next page fails ----

    @Test
    fun aFailureOfANextPageIsShownInTheFooterAndKeepsTheList() {
        val viewModel = viewModel()
        repository.failure = cause

        viewModel.onLoadMore()

        with(viewModel.state) {
            assertSame(cause, loadMoreFailure)
            assertFalse(isLoadingMore)
            assertEquals(projectIds(1, 3), ids)
            assertNull(error, "the full-screen error is for the first page")
            assertTrue(hasMore)
        }
    }

    @Test
    fun afterAFailureOnlyTheRetryAsksAgainAndForTheSamePage() {
        val viewModel = viewModel()
        repository.failure = cause
        viewModel.onLoadMore()

        viewModel.onLoadMore()
        assertEquals(2, repository.searches.size, "the scroll does not ask again after a failure")
        repository.failure = null
        viewModel.onRetryLoadMore()

        assertEquals(SearchCall(ProjectQuery(), PageRequest(3, 3)), repository.searches.last())
        assertEquals(3, repository.searches.size)
        with(viewModel.state) {
            assertNull(loadMoreFailure)
            assertEquals(projectIds(1, 6), ids)
        }
    }

    @Test
    fun theRetryOfTheFooterDoesNothingWithoutAFailureAndNotTwice() {
        val viewModel = viewModel()
        viewModel.onRetryLoadMore()
        assertEquals(1, repository.searches.size)

        repository.failure = cause
        viewModel.onLoadMore()
        repository.failure = null
        repository.gated = true
        viewModel.onRetryLoadMore()
        viewModel.onRetryLoadMore()

        assertEquals(3, repository.searches.size, "the first page, the failed page and one retry")
    }

    // ---- a new search while the old one is working ----

    @Test
    fun aNewTextWhileANextPageIsOnItsWayCancelsItAndItsAnswerIsDropped() = runTest {
        val viewModel = viewModel()
        repository.gated = true
        repository.lateAnswers = true
        viewModel.onLoadMore()
        val late = repository.gates.single()
        repository.gated = false

        viewModel.onQueryChange("ros")
        passTime(300)
        late.open()

        with(viewModel.state) {
            assertEquals("ros", query)
            assertEquals(projectIds(1, 3), ids, "the late page of the old query is not appended to the new list")
            assertFalse(isLoadingMore)
            assertEquals(2, resultsVersion)
        }
        assertEquals(SearchCall(ProjectQuery("ros"), PageRequest(0, 3)), repository.searches.last())
    }

    @Test
    fun theAnswerOfAnOlderSearchNeverOverwritesANewerOne() = runTest {
        val viewModel = viewModel()
        repository.gated = true
        repository.lateAnswers = true
        viewModel.onQueryChange("a")
        passTime(300)
        val first = repository.gates.last()
        viewModel.onQueryChange("ab")
        passTime(300)
        val second = repository.gates.last()
        repository.serve = { query, page -> Page(foundProjects(1).map { found("for-${query.text}") }, 1, page) }

        second.open()
        first.open()

        with(viewModel.state) {
            assertEquals(listOf("pfor-ab"), ids)
            assertEquals(2, resultsVersion, "the first page of the initial search and of the newest one")
        }
    }

    @Test
    fun threeEditsWithinTheWaitMakeOneRequestWithTheLastText() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("p")
        passTime(100)
        viewModel.onQueryChange("py")
        passTime(100)
        viewModel.onQueryChange("pyt")
        passTime(299)
        assertEquals(1, repository.searches.size, "nothing is asked for before the end of the typing")
        passTime(1)

        assertEquals(2, repository.searches.size)
        assertEquals(firstPage(ProjectQuery("pyt")), repository.searches.last())
        assertEquals("pyt", viewModel.state.query)
    }

    @Test
    fun theSearchKeyAsksAtOnceAndTheWaitingSearchIsGone() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("python")
        viewModel.onSearchSubmit()
        assertEquals(firstPage(ProjectQuery("python")), repository.searches.last())
        passTime(1_000)

        assertEquals(2, repository.searches.size)
    }

    @Test
    fun theTextIsSentAsTypedAndTheDataLayerCutsTheSpaces() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("  python, ros ")
        passTime(300)

        assertEquals(ProjectQuery("  python, ros "), repository.searches.last().query)
    }

    @Test
    fun theResultsVersionGrowsWithEveryNewFirstPageOnly() = runTest {
        val viewModel = viewModel()
        assertEquals(1, viewModel.state.resultsVersion)

        viewModel.onLoadMore()
        assertEquals(1, viewModel.state.resultsVersion)
        viewModel.onQueryChange("a")
        passTime(300)
        assertEquals(2, viewModel.state.resultsVersion)
        viewModel.onSearchSubmit()
        assertEquals(3, viewModel.state.resultsVersion)
    }

    @Test
    fun aTypedSearchKeepsTheOldListOnTheScreenUntilTheAnswer() = runTest {
        val viewModel = viewModel()
        repository.gated = true

        viewModel.onSearchSubmit()

        with(viewModel.state) {
            assertFalse(isLoading)
            assertEquals(projectIds(1, 3), ids)
            assertFalse(hasMore)
        }
    }

    // ---- the first page fails ----

    @Test
    fun aFailureOfTheFirstPageIsAFullScreenErrorWithItsCause() {
        repository.failure = cause

        val viewModel = viewModel()

        with(viewModel.state) {
            assertSame(cause, error)
            assertTrue(hasError)
            assertFalse(isLoading)
            assertEquals(emptyList(), results)
            assertEquals(0, total)
            assertFalse(hasMore)
        }
    }

    @Test
    fun aFailedTypedSearchTakesTheOldListAway() = runTest {
        val viewModel = viewModel()
        repository.failure = cause

        viewModel.onQueryChange("py")
        passTime(300)

        with(viewModel.state) {
            assertSame(cause, error)
            assertEquals(emptyList(), results)
            assertFalse(isLoading)
        }
    }

    @Test
    fun theRetryShowsTheProgressAndThenTheList() {
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
            assertEquals(projectIds(1, 3), ids)
        }
    }

    @Test
    fun aSecondRetryWhileTheFirstIsOnItsWayAsksNothing() {
        repository.failure = cause
        val viewModel = viewModel()
        repository.failure = null
        repository.gated = true

        viewModel.onRetry()
        repository.failure = cause
        viewModel.onRetry()

        assertEquals(2, repository.searches.size)
    }

    @Test
    fun theRetryWithoutAnErrorDoesNothing() {
        val viewModel = viewModel()

        viewModel.onRetry()

        assertEquals(1, repository.searches.size)
    }

    @Test
    fun aSecondFailureOfTheRetryIsShownAgain() {
        repository.failure = cause
        val viewModel = viewModel()

        viewModel.onRetry()

        assertSame(cause, viewModel.state.error)
        assertEquals(2, repository.searches.size)
    }

    // ---- the filters ----

    @Test
    fun theSheetOpensWithACopyOfTheAppliedFiltersAndTheFlagsOfTheServer() {
        val viewModel = viewModel()

        viewModel.onFiltersClick()

        with(assertNotNullSheet(viewModel)) {
            assertEquals(ProjectFilters(), draft)
            assertFalse(typeFilter, "the source cannot filter by the type")
            assertFalse(durationFilter, "nor by the duration")
            assertFalse(isSpherePickerOpen)
        }
    }

    @Test
    fun theFlagsOfTheSheetFollowTheCapabilities() {
        repository.capabilities = ProjectSearchCapabilities(typeFilter = true, durationFilter = true)
        val viewModel = viewModel()

        viewModel.onFiltersClick()

        with(assertNotNullSheet(viewModel)) {
            assertTrue(typeFilter)
            assertTrue(durationFilter)
        }
    }

    @Test
    fun editingTheDraftChangesNothingUntilApply() {
        val viewModel = viewModel()
        viewModel.onFiltersClick()

        viewModel.onFiltersDraftChange(ProjectFilters(keywords = listOf("ROS")))

        assertEquals(ProjectFilters(keywords = listOf("ROS")), assertNotNullSheet(viewModel).draft)
        assertEquals(ProjectFilters(), viewModel.state.filters)
        assertEquals(1, repository.searches.size)
    }

    @Test
    fun applyMakesTheDraftTheFiltersClosesTheSheetAndSearchesAtOnce() {
        val viewModel = viewModel()
        val filters = ProjectFilters(sphere = Biology, keywords = listOf("ROS"))
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(filters)

        viewModel.onFiltersApply()

        assertEquals(filters, viewModel.state.filters)
        assertNull(viewModel.state.filtersSheet)
        assertEquals(firstPage(ProjectQuery("", filters)), repository.searches.last())
        assertEquals(2, viewModel.state.resultsVersion)
    }

    @Test
    fun applyAfterTheSheetWasClosedDoesNothing() {
        val viewModel = viewModel()

        viewModel.onFiltersApply()

        assertEquals(1, repository.searches.size)
    }

    @Test
    fun cancelKeepsTheAppliedFilters() {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(ProjectFilters(keywords = listOf("ROS")))

        viewModel.onFiltersCancel()

        assertNull(viewModel.state.filtersSheet)
        assertEquals(ProjectFilters(), viewModel.state.filters)
        assertEquals(1, repository.searches.size)
    }

    @Test
    fun resetEmptiesTheDraftAndSearchesNothing() {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(ProjectFilters(sphere = Biology, durations = setOf(ProjectDuration.UpTo3Months)))

        viewModel.onFiltersReset()

        assertEquals(ProjectFilters(), assertNotNullSheet(viewModel).draft)
        assertEquals(1, repository.searches.size)
    }

    @Test
    fun clearingTheFiltersOfAnEmptyResultSearchesAtOnceWithTheProgressIndicator() {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(ProjectFilters(keywords = listOf("nothing")))
        repository.server = emptyList()
        viewModel.onFiltersApply()
        assertEquals(emptyList(), viewModel.state.results)
        repository.server = foundProjects(7)
        repository.gated = true

        viewModel.onFiltersClear()

        assertEquals(ProjectFilters(), viewModel.state.filters)
        assertTrue(viewModel.state.isLoading)
        repository.gates.single().open()
        assertEquals(projectIds(1, 3), viewModel.state.ids)
        assertEquals(ProjectQuery("", ProjectFilters()), repository.searches.last().query)
    }

    @Test
    fun theTypeAndTheDurationOfADraftReachTheRepositoryAsTheyAre() {
        // The data layer turns them into the parameters (one chip: its code, none or several: nothing); the view model does not decide that.
        val filters = ProjectFilters(types = setOf(ProjectType.Scientific), durations = setOf(ProjectDuration.Over6Months))
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(filters)

        viewModel.onFiltersApply()

        assertEquals(filters, repository.searches.last().query.filters)
    }

    // ---- the spheres ----

    @Test
    fun theSpheresAreLoadedWhenThePickerIsFirstOpenedAndOnlyOnce() {
        val viewModel = viewModel()
        viewModel.onFiltersClick()

        viewModel.onSphereFieldClick()

        assertTrue(assertNotNullSheet(viewModel).isSpherePickerOpen)
        assertEquals(listOf(ComputerScience, Biology), viewModel.state.spheres)
        viewModel.onSpherePickerDismiss()
        viewModel.onSphereFieldClick()
        assertEquals(1, spheres.calls)
    }

    @Test
    fun aPickerOpenedWhileTheSpheresComeAsksOnce() {
        spheres.gate = Gate()
        val viewModel = viewModel()
        viewModel.onFiltersClick()

        viewModel.onSphereFieldClick()
        viewModel.onSphereFieldClick()

        assertEquals(1, spheres.calls)
        spheres.gate!!.open()
        assertEquals(listOf(ComputerScience, Biology), viewModel.state.spheres)
    }

    @Test
    fun aFailureOfTheSpheresOffersARetry() {
        spheres.failure = cause
        val viewModel = viewModel()
        viewModel.onFiltersClick()

        viewModel.onSphereFieldClick()
        assertTrue(viewModel.state.spheresLoadFailed)
        viewModel.onSphereFieldClick()
        assertEquals(1, spheres.calls, "the picker does not retry by itself")
        spheres.failure = null
        viewModel.onSpheresRetry()

        assertFalse(viewModel.state.spheresLoadFailed)
        assertEquals(listOf(ComputerScience, Biology), viewModel.state.spheres)
    }

    @Test
    fun anEmptyListOfSpheresIsAFailureToo() {
        spheres.spheres = emptyList()
        val viewModel = viewModel()
        viewModel.onFiltersClick()

        viewModel.onSphereFieldClick()

        assertTrue(viewModel.state.spheresLoadFailed)
    }

    @Test
    fun pickingASphereSetsTheDraftAndClosesThePicker() {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onSphereFieldClick()

        viewModel.onSphereSelect(Biology)

        with(assertNotNullSheet(viewModel)) {
            assertEquals(Biology, draft.sphere)
            assertFalse(isSpherePickerOpen)
        }
        viewModel.onSphereFieldClick()
        viewModel.onSphereSelect(null)
        assertNull(assertNotNullSheet(viewModel).draft.sphere, "any sphere is no restriction")
    }

    @Test
    fun closingThePickerKeepsTheSphereOfTheDraft() {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onSphereFieldClick()
        viewModel.onSphereSelect(ComputerScience)
        viewModel.onSphereFieldClick()

        viewModel.onSpherePickerDismiss()

        with(assertNotNullSheet(viewModel)) {
            assertEquals(ComputerScience, draft.sphere)
            assertFalse(isSpherePickerOpen)
        }
    }

    @Test
    fun aSphereChosenAfterTheSheetWasClosedChangesNothing() {
        val viewModel = viewModel()

        viewModel.onSphereSelect(Biology)

        assertNull(viewModel.state.filtersSheet)
        assertEquals(ProjectFilters(), viewModel.state.filters)
    }

    // ---- helpers ----

    private fun assertNotNullSheet(viewModel: ProjectSearchViewModel): ProjectFiltersSheetState =
        viewModel.state.filtersSheet ?: error("The filters sheet is closed")

    private fun sliceFor(page: PageRequest) = repository.sliceOfServer(page)

    /** The projects `p<n>` for the numbers, as the page [offsetOf] holds them. */
    private fun projectsOf(vararg numbers: Int, offsetOf: PageRequest) =
        numbers.map { found("$it") }.also { check(offsetOf.limit >= it.size) }
}
