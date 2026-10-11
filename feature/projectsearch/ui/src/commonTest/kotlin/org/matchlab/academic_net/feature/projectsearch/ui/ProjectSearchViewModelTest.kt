package org.matchlab.academic_net.feature.projectsearch.ui

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.feature.projectsearch.data.MockProjectSearchDataSource
import org.matchlab.academic_net.feature.projectsearch.data.ProjectSearchRepositoryImpl
import org.matchlab.academic_net.feature.projectsearch.domain.FoundProject
import org.matchlab.academic_net.feature.projectsearch.domain.GetSearchSpheresUseCase
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectDuration
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectFilters
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectSearchRepository
import org.matchlab.academic_net.feature.projectsearch.domain.SearchProjectsUseCase
import org.matchlab.academic_net.feature.projectsearch.domain.SphereProvider

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectSearchViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** The mock server behind a spy: records the queries, can fail and can hold one query. */
    private class SpyRepository : ProjectSearchRepository {
        private val server = ProjectSearchRepositoryImpl(MockProjectSearchDataSource(latencyMillis = 0))
        val queries = mutableListOf<ProjectQuery>()
        val cancelledTexts = mutableListOf<String>()
        var failing = false

        /** A query with this text waits until [release] is completed. */
        var holdText: String? = null
        val release = CompletableDeferred<Unit>()

        override suspend fun search(query: ProjectQuery): List<FoundProject> {
            queries += query
            if (query.text == holdText) {
                try {
                    release.await()
                } catch (cancellation: kotlinx.coroutines.CancellationException) {
                    cancelledTexts += query.text
                    throw cancellation
                }
            }
            if (failing) error("no network")
            return server.search(query)
        }
    }

    private class FakeSpheres(var failing: Boolean = false, var spheres: List<Sphere> = Spheres) : SphereProvider {
        var calls = 0

        override suspend fun getSpheres(): List<Sphere> {
            calls++
            if (failing) error("no network")
            return spheres
        }
    }

    private val repository = SpyRepository()
    private val sphereProvider = FakeSpheres()

    private fun viewModel() = ProjectSearchViewModel(
        ProjectSearchUiDependencies(
            searchProjects = SearchProjectsUseCase(repository),
            getSpheres = GetSearchSpheresUseCase(sphereProvider),
        ),
    )

    private fun ProjectSearchUiState.titles() = results.map { it.title }

    @Test
    fun searchesEveryPublishedProjectOnStart() = runTest {
        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.hasError)
        assertEquals(listOf(ProjectQuery()), repository.queries)
        assertEquals(12, state.results.size)
        // The newest first, as the server answers.
        assertEquals("AI-навигация по кампусу", state.results.first().title)
    }

    @Test
    fun itemCarriesWhatTheCardShows() = runTest {
        val item = viewModel().uiState.value.results.first()

        assertEquals("Проф. Елена Васкес", item.initiatorName)
        assertEquals("Stanford", item.initiatorUniversity)
        assertEquals("user-elena-vasquez", item.initiatorAvatarKey)
        assertTrue(item.isInitiatorVerified)
        assertEquals(ProjectType.Scientific, item.type)
        assertEquals("Computer Science", item.sphere)
        assertEquals(4, item.rolesCount)
    }

    @Test
    fun startsLoadingBeforeTheFirstAnswer() = runTest {
        repository.holdText = ""

        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.results.isEmpty())
        repository.release.complete(Unit)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(12, viewModel.uiState.value.results.size)
    }

    @Test
    fun typingSearchesAfterAPause() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("машинное обучение")
        assertEquals("машинное обучение", viewModel.uiState.value.query)
        advanceTimeBy(299)
        runCurrent()
        assertEquals(1, repository.queries.size)
        assertEquals(12, viewModel.uiState.value.results.size)

        advanceTimeBy(2)
        runCurrent()

        assertEquals(ProjectQuery(text = "машинное обучение"), repository.queries.last())
        assertEquals(
            listOf("Обнаружение аномалий в МРТ", "Прогноз спроса в столовых кампуса"),
            viewModel.uiState.value.titles(),
        )
    }

    @Test
    fun everyKeyRestartsThePause() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("м")
        advanceTimeBy(200)
        viewModel.onQueryChange("ма")
        advanceTimeBy(200)
        runCurrent()
        assertEquals(1, repository.queries.size)

        advanceTimeBy(101)
        runCurrent()

        assertEquals(listOf(ProjectQuery(), ProjectQuery(text = "ма")), repository.queries)
    }

    @Test
    fun searchKeySearchesAtOnceAndDropsThePendingOne() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("Telegram")
        viewModel.onSearchSubmit()
        assertEquals(listOf("Telegram-бот для расписания"), viewModel.uiState.value.titles())
        advanceTimeBy(1_000)
        runCurrent()

        // The typed pause was cancelled by the submit: one request for the text, not two.
        assertEquals(listOf(ProjectQuery(), ProjectQuery(text = "Telegram")), repository.queries)
    }

    @Test
    fun newSearchCancelsTheOutdatedOne() = runTest {
        val viewModel = viewModel()
        repository.holdText = "медленный"

        viewModel.onQueryChange("медленный")
        viewModel.onSearchSubmit()
        viewModel.onQueryChange("Telegram")
        viewModel.onSearchSubmit()
        // A late answer of the first request must not replace the newer one.
        repository.release.complete(Unit)
        runCurrent()

        assertEquals(listOf("медленный"), repository.cancelledTexts)
        assertEquals(listOf("Telegram-бот для расписания"), viewModel.uiState.value.titles())
    }

    @Test
    fun repeatedSearchKeepsTheOldListInsteadOfLoading() = runTest {
        val viewModel = viewModel()
        repository.holdText = "медленный"

        viewModel.onQueryChange("медленный")
        viewModel.onSearchSubmit()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(12, state.results.size)
    }

    @Test
    fun noMatchGivesAnEmptyListWithoutAnError() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("такого нет нигде")
        viewModel.onSearchSubmit()

        val state = viewModel.uiState.value
        assertTrue(state.results.isEmpty())
        assertFalse(state.isLoading)
        assertFalse(state.hasError)
    }

    @Test
    fun sheetStartsFromTheAppliedFilters() = runTest {
        val viewModel = viewModel()
        val applied = ProjectFilters(types = setOf(ProjectType.Scientific), keywords = listOf("Python"))
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(applied)
        viewModel.onFiltersApply()

        viewModel.onFiltersClick()

        val sheet = assertNotNull(viewModel.uiState.value.filtersSheet)
        assertEquals(applied, sheet.draft)
        assertFalse(sheet.isSpherePickerOpen)
    }

    @Test
    fun editingTheDraftDoesNotSearch() = runTest {
        val viewModel = viewModel()
        viewModel.onFiltersClick()

        viewModel.onFiltersDraftChange(ProjectFilters(types = setOf(ProjectType.Practical)))

        assertEquals(1, repository.queries.size)
        assertTrue(viewModel.uiState.value.filters.isEmpty)
        assertEquals(setOf(ProjectType.Practical), viewModel.uiState.value.filtersSheet?.draft?.types)
    }

    @Test
    fun applySearchesWithTheDraftAndClosesTheSheet() = runTest {
        val viewModel = viewModel()
        val draft = ProjectFilters(types = setOf(ProjectType.Scientific))
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(draft)

        viewModel.onFiltersApply()

        val state = viewModel.uiState.value
        assertNull(state.filtersSheet)
        assertEquals(draft, state.filters)
        assertEquals(ProjectQuery(filters = draft), repository.queries.last())
        assertEquals(7, state.results.size)
        assertTrue(state.results.all { it.type == ProjectType.Scientific })
    }

    @Test
    fun applySendsTheCurrentTextWithTheFilters() = runTest {
        val viewModel = viewModel()
        viewModel.onQueryChange("Python")
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(ProjectFilters(durations = setOf(ProjectDuration.UpTo3Months)))

        viewModel.onFiltersApply()

        assertEquals(
            ProjectQuery("Python", ProjectFilters(durations = setOf(ProjectDuration.UpTo3Months))),
            repository.queries.last(),
        )
    }

    @Test
    fun cancelKeepsTheAppliedFiltersAndDropsTheDraft() = runTest {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(ProjectFilters(types = setOf(ProjectType.Practical)))

        viewModel.onFiltersCancel()

        val state = viewModel.uiState.value
        assertNull(state.filtersSheet)
        assertTrue(state.filters.isEmpty)
        assertEquals(1, repository.queries.size)
        // The next opening starts from the applied filters, not from the abandoned draft.
        viewModel.onFiltersClick()
        assertTrue(viewModel.uiState.value.filtersSheet?.draft?.isEmpty == true)
    }

    @Test
    fun resetEmptiesTheDraftOnly() = runTest {
        val viewModel = viewModel()
        val applied = ProjectFilters(types = setOf(ProjectType.Scientific))
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(applied)
        viewModel.onFiltersApply()
        viewModel.onFiltersClick()
        val searches = repository.queries.size

        viewModel.onFiltersReset()

        assertTrue(viewModel.uiState.value.filtersSheet?.draft?.isEmpty == true)
        assertEquals(applied, viewModel.uiState.value.filters)
        assertEquals(searches, repository.queries.size)
    }

    @Test
    fun dotFollowsTheAppliedFilters() = runTest {
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.filters.isEmpty)

        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(ProjectFilters(types = setOf(ProjectType.Practical)))
        // An unapplied draft does not light the dot.
        assertTrue(viewModel.uiState.value.filters.isEmpty)
        viewModel.onFiltersApply()
        assertFalse(viewModel.uiState.value.filters.isEmpty)

        viewModel.onFiltersClear()

        assertTrue(viewModel.uiState.value.filters.isEmpty)
        assertEquals(12, viewModel.uiState.value.results.size)
        assertEquals(ProjectQuery(), repository.queries.last())
    }

    @Test
    fun clearKeepsTheTypedText() = runTest {
        val viewModel = viewModel()
        viewModel.onQueryChange("Python")
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(ProjectFilters(types = setOf(ProjectType.Practical)))
        viewModel.onFiltersApply()

        viewModel.onFiltersClear()

        assertEquals(ProjectQuery(text = "Python"), repository.queries.last())
        assertEquals("Python", viewModel.uiState.value.query)
    }

    @Test
    fun failureShowsAnErrorAndRetryRecovers() = runTest {
        repository.failing = true
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.hasError)
        assertFalse(viewModel.uiState.value.isLoading)

        repository.failing = false
        viewModel.onRetry()

        val state = viewModel.uiState.value
        assertFalse(state.hasError)
        assertFalse(state.isLoading)
        assertEquals(12, state.results.size)
    }

    @Test
    fun retryShowsTheProgressIndicatorWhileWaiting() = runTest {
        repository.failing = true
        val viewModel = viewModel()
        repository.failing = false
        repository.holdText = ""

        viewModel.onRetry()

        assertTrue(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.hasError)
    }

    @Test
    fun failureOfARepeatedSearchIsAnError() = runTest {
        val viewModel = viewModel()
        repository.failing = true

        viewModel.onQueryChange("Python")
        viewModel.onSearchSubmit()

        assertTrue(viewModel.uiState.value.hasError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun spheresAreLoadedOnceOnFirstOpening() = runTest {
        val viewModel = viewModel()
        assertEquals(0, sphereProvider.calls)
        viewModel.onFiltersClick()

        viewModel.onSphereFieldClick()
        assertEquals(1, sphereProvider.calls)
        assertEquals(Spheres, viewModel.uiState.value.spheres)
        assertEquals(true, viewModel.uiState.value.filtersSheet?.isSpherePickerOpen)
        viewModel.onSpherePickerDismiss()
        viewModel.onSphereFieldClick()

        assertEquals(1, sphereProvider.calls)
    }

    @Test
    fun spheresStayLoadedAfterTheSheetIsClosed() = runTest {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onSphereFieldClick()
        viewModel.onFiltersCancel()

        viewModel.onFiltersClick()
        viewModel.onSphereFieldClick()

        assertEquals(1, sphereProvider.calls)
    }

    @Test
    fun spheresFailureCanBeRetried() = runTest {
        sphereProvider.failing = true
        val viewModel = viewModel()
        viewModel.onFiltersClick()

        viewModel.onSphereFieldClick()

        assertTrue(viewModel.uiState.value.spheresLoadFailed)
        assertTrue(viewModel.uiState.value.spheres.isEmpty())

        sphereProvider.failing = false
        viewModel.onSpheresRetry()

        assertFalse(viewModel.uiState.value.spheresLoadFailed)
        assertEquals(Spheres, viewModel.uiState.value.spheres)
        assertEquals(2, sphereProvider.calls)
    }

    @Test
    fun emptySphereListIsAFailureSoThePickerDoesNotSpinForever() = runTest {
        sphereProvider.spheres = emptyList()
        val viewModel = viewModel()
        viewModel.onFiltersClick()

        viewModel.onSphereFieldClick()

        assertTrue(viewModel.uiState.value.spheresLoadFailed)
    }

    @Test
    fun pickedSphereGoesIntoTheDraftAndClosesThePicker() = runTest {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onSphereFieldClick()

        viewModel.onSphereSelect(Medicine)

        val sheet = assertNotNull(viewModel.uiState.value.filtersSheet)
        assertEquals(Medicine, sheet.draft.sphere)
        assertFalse(sheet.isSpherePickerOpen)
        // Only the draft changed.
        assertNull(viewModel.uiState.value.filters.sphere)
    }

    @Test
    fun anySphereClearsTheChoice() = runTest {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(ProjectFilters(sphere = Medicine))
        viewModel.onSphereFieldClick()

        viewModel.onSphereSelect(null)

        assertNull(viewModel.uiState.value.filtersSheet?.draft?.sphere)
    }

    @Test
    fun appliedSphereFiltersById() = runTest {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onSphereFieldClick()
        viewModel.onSphereSelect(Medicine)

        viewModel.onFiltersApply()

        assertEquals(ProjectFilters(sphere = Medicine), repository.queries.last().filters)
        assertEquals(listOf("Обнаружение аномалий в МРТ"), viewModel.uiState.value.titles())
    }

    @Test
    fun durationChipsNarrowTheResult() = runTest {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(ProjectFilters(durations = setOf(ProjectDuration.UpTo3Months)))

        viewModel.onFiltersApply()

        assertEquals(
            setOf("Мобильное приложение библиотеки", "Telegram-бот для расписания", "Бюджет студента: финансовая грамотность"),
            viewModel.uiState.value.titles().toSet(),
        )
    }

    @Test
    fun closingTheSheetClosesThePicker() = runTest {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onSphereFieldClick()

        viewModel.onFiltersCancel()
        viewModel.onFiltersClick()

        assertEquals(false, viewModel.uiState.value.filtersSheet?.isSpherePickerOpen)
    }

    private companion object {
        val Medicine = Sphere("medicine", "Медицина")
        val Spheres = listOf(Sphere("computer-science", "Computer Science"), Medicine, Sphere("physics", "Физика"))
    }
}
