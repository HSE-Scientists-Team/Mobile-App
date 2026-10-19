package org.matchlab.academic_net.feature.projects.ui.list

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectMember
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.projects.domain.ParticipationSource
import org.matchlab.academic_net.feature.projects.domain.usecase.GetParticipatingProjectsUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveMyProjectsUseCase
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
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 06b "Я участник" and the badge of 06e: when the tab reads, what it keeps, and the badge number. */
class ParticipatingProjectsViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val repository = FakeProjectsRepository(caps(canListMine = true))
    private var joinedIds: List<ProjectId> = emptyList()
    private var joinedFailure: Throwable? = null
    private var sourceCalls = 0

    private fun TestScope.viewModel(): MyProjectsViewModel {
        val source = ParticipationSource {
            sourceCalls++
            joinedFailure?.let { throw it }
            joinedIds
        }
        val viewModel = MyProjectsViewModel(
            ObserveMyProjectsUseCase(repository),
            repository.capabilities,
            GetParticipatingProjectsUseCase(source, repository),
        )
        keepCollecting(viewModel.uiState)
        return viewModel
    }

    private fun joined(id: String, title: String = "Проект $id", initiator: Boolean = false): Project =
        project(id, title = title, viewerIsInitiator = initiator, roles = listOf(role("r1"))).copy(
            members = listOf(ProjectMember(UserId("me"), RoleId("r1"), "Роль", isViewer = true)),
        )

    private fun serve(vararg projects: Project) {
        for (project in projects) repository.server[project.id] = project
        joinedIds = projects.map { it.id }
    }

    @Test
    fun nothingIsReadUntilTheTabIsChosen() = runTest {
        serve(joined("a"))

        val viewModel = viewModel()

        assertEquals(ParticipatingState.Idle, viewModel.uiState.value.participating)
        assertEquals(0, sourceCalls)
    }

    @Test
    fun choosingTheTabReadsAndListsTheProjectsWithTheAuthor() = runTest {
        serve(joined("a", "Альфа"), joined("b", "Бета"))
        val viewModel = viewModel()

        viewModel.onParticipantTabShown()

        val loaded = assertIs<ParticipatingState.Loaded>(viewModel.uiState.value.participating)
        assertEquals(listOf("Альфа", "Бета"), loaded.projects.map { it.title })
        assertEquals("Автор", loaded.projects.first().initiatorName)
        assertEquals(1, loaded.projects.first().rolesCount)
    }

    @Test
    fun theTabShowsLoadingWhileTheFirstReadRuns() = runTest {
        serve(joined("a"))
        repository.loadGate = Gate()
        val viewModel = viewModel()

        viewModel.onParticipantTabShown()

        assertEquals(ParticipatingState.Loading, viewModel.uiState.value.participating)
        repository.loadGate!!.open()
        assertIs<ParticipatingState.Loaded>(viewModel.uiState.value.participating)
    }

    @Test
    fun noRecordsIsAnEmptyLoadedTab() = runTest {
        val viewModel = viewModel()

        viewModel.onParticipantTabShown()

        assertEquals(ParticipatingState.Loaded(emptyList()), viewModel.uiState.value.participating)
    }

    @Test
    fun aProjectTheViewerOwnsOrHasLeftIsNotListed() = runTest {
        serve(joined("mine", initiator = true), joined("ok"))
        repository.server[ProjectId("left")] = project("left", viewerIsInitiator = false)
        joinedIds = listOf(ProjectId("mine"), ProjectId("left"), ProjectId("ok"))
        val viewModel = viewModel()

        viewModel.onParticipantTabShown()

        val loaded = assertIs<ParticipatingState.Loaded>(viewModel.uiState.value.participating)
        assertEquals(listOf("ok"), loaded.projects.map { it.id.value })
    }

    @Test
    fun eachChoiceOfTheTabReadsAgainAndTheOldListStaysWhileItDoes() = runTest {
        serve(joined("a", "Старый"))
        val viewModel = viewModel()
        viewModel.onParticipantTabShown()
        repository.server[ProjectId("a")] = joined("a", "Новый")
        repository.loadGate = Gate()

        viewModel.onParticipantTabShown()

        val during = assertIs<ParticipatingState.Loaded>(viewModel.uiState.value.participating)
        assertEquals("Старый", during.projects.single().title, "no flash of the progress indicator")
        repository.loadGate!!.open()
        assertEquals("Новый", assertIs<ParticipatingState.Loaded>(viewModel.uiState.value.participating).projects.single().title)
        assertEquals(2, sourceCalls)
    }

    @Test
    fun aReadThatIsRunningIsNotDoubled() = runTest {
        serve(joined("a"))
        repository.loadGate = Gate()
        val viewModel = viewModel()

        viewModel.onParticipantTabShown()
        viewModel.onParticipantTabShown()
        viewModel.onParticipantTabShown()
        repository.loadGate!!.open()

        assertEquals(1, sourceCalls)
        assertEquals(1, repository.loads.size)
    }

    @Test
    fun aFailureIsTheErrorAndRetryReadsAgain() = runTest {
        serve(joined("a"))
        val cause = IllegalStateException("offline")
        joinedFailure = cause
        val viewModel = viewModel()

        viewModel.onParticipantTabShown()

        assertSame(cause, assertIs<ParticipatingState.Failed>(viewModel.uiState.value.participating).cause)
        joinedFailure = null
        viewModel.onParticipantRetry()
        assertIs<ParticipatingState.Loaded>(viewModel.uiState.value.participating)
        assertEquals(2, sourceCalls)
    }

    @Test
    fun retryWithoutAnErrorDoesNothing() = runTest {
        serve(joined("a"))
        val viewModel = viewModel()

        viewModel.onParticipantRetry()
        assertEquals(ParticipatingState.Idle, viewModel.uiState.value.participating)
        viewModel.onParticipantTabShown()
        viewModel.onParticipantRetry()

        assertEquals(1, sourceCalls)
    }

    @Test
    fun aFailureOfOneProjectFailsTheWholeTab() = runTest {
        serve(joined("a"), joined("b"))
        repository.loadFailure = IllegalStateException("boom")
        val viewModel = viewModel()

        viewModel.onParticipantTabShown()

        assertIs<ParticipatingState.Failed>(viewModel.uiState.value.participating)
    }

    @Test
    fun theTabWorksWhenTheOtherTabCannotListProjects() = runTest {
        repository.capabilities = caps(canListMine = false)
        serve(joined("a"))
        val viewModel = viewModel()

        viewModel.onParticipantTabShown()

        assertTrue(viewModel.uiState.value.isListUnavailable)
        assertIs<ParticipatingState.Loaded>(viewModel.uiState.value.participating)
    }

    @Test
    fun aFailedRereadLeavesTheListThatIsOnTheScreen() = runTest {
        serve(joined("a", "Альфа"))
        val viewModel = viewModel()
        viewModel.onParticipantTabShown()
        joinedFailure = IllegalStateException("offline")

        viewModel.onParticipantTabShown()

        val state = assertIs<ParticipatingState.Loaded>(viewModel.uiState.value.participating)
        assertEquals(listOf("Альфа"), state.projects.map { it.title })
        assertEquals(2, sourceCalls)
    }

    // ---- the badge of 06e ----

    @Test
    fun theBadgeNumberIsThePendingJoinCountOfTheProject() = runTest {
        repository.myProjects = MutableStateFlow(
            listOf(
                project("a").copy(pendingJoinCount = 3),
                project("b"),
                project("c", status = ProjectStatus.Started).copy(pendingJoinCount = 120),
            ),
        )

        val items = viewModel().uiState.value.projects

        assertEquals(listOf(3, 0, 120), items.map { it.pendingJoinCount })
    }
}
