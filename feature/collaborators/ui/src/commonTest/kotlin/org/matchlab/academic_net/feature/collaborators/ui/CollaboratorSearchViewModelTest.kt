package org.matchlab.academic_net.feature.collaborators.ui

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
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.collaborators.data.CollaboratorsRepositoryImpl
import org.matchlab.academic_net.feature.collaborators.data.mock.MockCollaboratorsDataSource
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorFilters
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorQuery
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorsRepository
import org.matchlab.academic_net.feature.collaborators.domain.RoleReference
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirement
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirementProvider
import org.matchlab.academic_net.feature.collaborators.domain.ScoredCollaborator
import org.matchlab.academic_net.feature.collaborators.domain.usecase.GetRoleRequirementUseCase
import org.matchlab.academic_net.feature.collaborators.domain.usecase.SearchCollaboratorsUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class CollaboratorSearchViewModelTest {
    private val projectId = ProjectId("p1")
    private val roleId = RoleId("r1")

    private class MutableRequirement(var value: RoleRequirement?) : RoleRequirementProvider {
        override suspend fun getRoleRequirement(projectId: ProjectId, roleId: RoleId) = value
    }

    private val requirement = MutableRequirement(
        RoleRequirement("AI-навигация по кампусу", "ML Engineer", listOf("Computer Vision", "Python")),
    )

    /** A server that does not answer about the role until the test lets it, and counts the questions. */
    private class GatedRequirement(private val value: RoleRequirement?) : RoleRequirementProvider {
        private val gate = CompletableDeferred<Unit>()
        var requests = 0
            private set

        fun answer() {
            gate.complete(Unit)
        }

        override suspend fun getRoleRequirement(projectId: ProjectId, roleId: RoleId): RoleRequirement? {
            requests++
            gate.await()
            return value
        }
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: CollaboratorsRepository? = null,
        role: RoleReference? = RoleReference(projectId, roleId),
    ): CollaboratorSearchViewModel {
        val backend = repository ?: CollaboratorsRepositoryImpl(
            MockCollaboratorsDataSource(requirement, latencyMillis = 0),
        )
        return CollaboratorSearchViewModel(
            role,
            CollaboratorsUiDependencies(
                getRoleRequirement = GetRoleRequirementUseCase(requirement),
                searchCollaborators = SearchCollaboratorsUseCase(backend),
            ),
        )
    }

    private fun CollaboratorSearchUiState.names() = results.map { it.user.name }

    /** A server that records what it was asked and answers with one fixed person. */
    private class RecordingBackend : CollaboratorsRepository {
        val queries = mutableListOf<CollaboratorQuery>()
        private val person = ScoredCollaborator(
            Collaborator(UserSummary(UserId("u1"), "Иван Иванов"), "МГУ", Study("Магистратура"), "физика"),
            matchPercent = 80,
        )

        override suspend fun search(query: CollaboratorQuery): List<ScoredCollaborator> {
            queries += query
            return listOf(person)
        }
    }

    @Test
    fun startsFromTheRoleAndSearchesItsSkills() = runTest {
        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertEquals("ML Engineer", state.roleTitle)
        assertEquals("Computer Vision, Python", state.query)
        assertTrue(state.filters.isEmpty)
    }

    @Test
    fun firstSearchSendsTheRoleSkillsAndTheRole() = runTest {
        val backend = RecordingBackend()

        val state = viewModel(backend).uiState.value

        assertEquals(
            listOf(CollaboratorQuery("Computer Vision, Python", CollaboratorFilters(), RoleReference(projectId, roleId))),
            backend.queries,
        )
        assertEquals(listOf("Иван Иванов"), state.names())
        assertEquals(80, state.results.single().matchPercent)
    }

    @Test
    fun resultsAreTheServersOrderBestFitFirst() = runTest {
        val state = viewModel().uiState.value

        assertEquals(
            // 100%: by name; 50%: verified (Лина, Мария, Тимур) before not verified (Ильяс).
            listOf(
                "Арджун Мехта", "Даниил Крюков", "Ольга Никитина",
                "Лина Ковач", "Мария Соколова", "Тимур Алиев", "Ильяс Каримов",
            ),
            state.names(),
        )
        assertEquals(listOf(100, 100, 100, 50, 50, 50, 50), state.results.map { it.matchPercent })
    }

    @Test
    fun withoutARoleItFindsEveryoneWithoutScores() = runTest {
        val backend = RecordingBackend()

        val state = viewModel(backend, role = null).uiState.value

        assertFalse(state.isLoading)
        assertNull(state.roleTitle)
        assertEquals("", state.query)
        assertEquals(listOf(CollaboratorQuery()), backend.queries)
    }

    @Test
    fun withoutARoleTheMockServerReturnsEveryoneVerifiedFirstThenByName() = runTest {
        val state = viewModel(role = null).uiState.value

        assertEquals(10, state.results.size)
        assertTrue(state.results.all { it.matchPercent == null })
        val verified = state.results.map { it.user.isVerified }
        assertEquals(verified.sortedDescending(), verified)
    }

    @Test
    fun rowCarriesTheUniversityStudyAndFieldOfThePerson() = runTest {
        val first = viewModel().uiState.value.results.first()

        // The line under the name is made by the screen (`subtitleText`), in the language of the app.
        assertEquals("Stanford", first.collaborator.university)
        assertEquals(Study("Бакалавриат", 3), first.collaborator.study)
        assertEquals("Computer Science", first.collaborator.field)
    }

    @Test
    fun searchSendsTheTextAsTypedAndTheRole() = runTest {
        val backend = RecordingBackend()
        val viewModel = viewModel(backend)

        viewModel.onQueryChange("Kotlin, Android")
        viewModel.onSearchSubmit()

        assertEquals(
            CollaboratorQuery(
                text = "Kotlin, Android",
                filters = CollaboratorFilters(),
                role = RoleReference(projectId, roleId),
            ),
            backend.queries.last(),
        )
    }

    @Test
    fun typingSwitchesToSearchAfterAPause() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("Kotlin")
        // Nothing yet: the search waits for the user to stop typing.
        assertEquals("Kotlin", viewModel.uiState.value.query)
        assertTrue(viewModel.uiState.value.names().size > 2)
        advanceTimeBy(350)
        runCurrent()

        val state = viewModel.uiState.value
        // The score is still the role's: Тимур has Python, Сергей has neither of its skills.
        assertEquals(listOf("Тимур Алиев", "Сергей Волков"), state.names())
        assertEquals(listOf(50, 0), state.results.map { it.matchPercent })
    }

    @Test
    fun submitSearchesWithoutWaiting() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("Figma")
        viewModel.onSearchSubmit()

        assertEquals(listOf("Анна Петрова"), viewModel.uiState.value.names())
    }

    @Test
    fun filtersSheetShowsHowManyPeopleItWouldGive() = runTest {
        val viewModel = viewModel()
        viewModel.onFiltersClick()

        viewModel.onFiltersDraftChange(
            CollaboratorFilters(verifiedProfile = true, workTypes = setOf(ProjectType.Scientific)),
        )

        val sheet = assertNotNull(viewModel.uiState.value.filtersSheet)
        // Of those with Computer Vision or Python: verified profile and scientific works.
        assertEquals(4, sheet.previewCount)
    }

    @Test
    fun previewAsksTheServerWithTheDraftFilters() = runTest {
        val backend = RecordingBackend()
        val viewModel = viewModel(backend)
        val draft = CollaboratorFilters(hasPublications = true)

        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(draft)

        assertEquals(
            CollaboratorQuery("Computer Vision, Python", draft, RoleReference(projectId, roleId)),
            backend.queries.last(),
        )
        assertEquals(1, viewModel.uiState.value.filtersSheet?.previewCount)
    }

    @Test
    fun appliedFiltersNarrowTheResult() = runTest {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(CollaboratorFilters(hasPublications = true))

        viewModel.onFiltersApply()

        val state = viewModel.uiState.value
        assertNull(state.filtersSheet)
        assertEquals(CollaboratorFilters(hasPublications = true), state.filters)
        // People with publications among those with Computer Vision or Python, best fit first.
        assertEquals(listOf("Арджун Мехта", "Ольга Никитина", "Лина Ковач", "Мария Соколова"), state.names())
    }

    @Test
    fun dismissedSheetDiscardsTheDraft() = runTest {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(CollaboratorFilters(verifiedProfile = true))

        viewModel.onFiltersDismiss()

        assertNull(viewModel.uiState.value.filtersSheet)
        assertTrue(viewModel.uiState.value.filters.isEmpty)
    }

    @Test
    fun resetEmptiesTheDraft() = runTest {
        val viewModel = viewModel()
        viewModel.onFiltersClick()
        viewModel.onFiltersDraftChange(CollaboratorFilters(verifiedProfile = true, university = "МГУ"))

        viewModel.onFiltersReset()

        assertTrue(viewModel.uiState.value.filtersSheet?.draft?.isEmpty == true)
    }

    @Test
    fun missingRoleIsAnErrorThatCanBeRetried() = runTest {
        requirement.value = null
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.hasError)
        assertFalse(viewModel.uiState.value.isLoading)

        requirement.value = RoleRequirement("P", "Designer", listOf("Figma"))
        viewModel.onRetry()

        val state = viewModel.uiState.value
        assertFalse(state.hasError)
        assertEquals("Designer", state.roleTitle)
        assertEquals(listOf("Анна Петрова"), state.names())
    }

    @Test
    fun retryWhileTheLoadIsOnItsWayAsksOnce() = runTest {
        val gated = GatedRequirement(RoleRequirement("P", "Designer", listOf("Figma")))
        val backend = CollaboratorsRepositoryImpl(MockCollaboratorsDataSource(requirement, latencyMillis = 0))
        val viewModel = CollaboratorSearchViewModel(
            RoleReference(projectId, roleId),
            CollaboratorsUiDependencies(
                getRoleRequirement = GetRoleRequirementUseCase(gated),
                searchCollaborators = SearchCollaboratorsUseCase(backend),
            ),
        )
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.onRetry()
        viewModel.onRetry()
        gated.answer()

        assertEquals(1, gated.requests)
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Designer", state.roleTitle)
    }
}
