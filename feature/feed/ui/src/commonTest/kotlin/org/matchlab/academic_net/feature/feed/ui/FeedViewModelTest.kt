package org.matchlab.academic_net.feature.feed.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.feed.domain.FeedRepository
import org.matchlab.academic_net.feature.feed.domain.RecommendedProject
import org.matchlab.academic_net.feature.feed.domain.ViewerNameSource
import org.matchlab.academic_net.feature.feed.domain.usecase.GetRecommendedPeopleUseCase
import org.matchlab.academic_net.feature.feed.domain.usecase.GetRecommendedProjectsUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import org.matchlab.academic_net.feature.feed.domain.usecase.ObserveViewerNameUseCase

/**
 * The recommendations behind the feed: how many times the people (`calls`) and the projects (`projectCalls`) were asked,
 * what they answer, and a gate for each to hold the answer back.
 */
private class FakeFeedRepository : FeedRepository {
    var calls = 0
    var people: List<Collaborator> = emptyList()
    var failure: Throwable? = null
    var gate: CompletableDeferred<Unit>? = null

    var projectCalls = 0
    var projects: List<RecommendedProject> = emptyList()
    var projectsFailure: Throwable? = null
    var projectsGate: CompletableDeferred<Unit>? = null

    override suspend fun getRecommendedPeople(): List<Collaborator> {
        calls++
        gate?.await()
        failure?.let { throw it }
        return people
    }

    override suspend fun getRecommendedProjects(): List<RecommendedProject> {
        projectCalls++
        projectsGate?.await()
        projectsFailure?.let { throw it }
        return projects
    }
}

/** The name of the viewer as the profile has it: it can change while the feed is shown, or fail to be read. */
private class FakeViewerNames : ViewerNameSource {
    val name = MutableStateFlow("")
    var failure: Throwable? = null

    override fun observeName(): Flow<String> = flow {
        failure?.let { throw it }
        emitAll(name)
    }
}

private fun recommended(id: String, university: String = "МГУ") = RecommendedProject(
    project = Project(
        id = ProjectId(id),
        title = "Проект $id",
        type = null,
        sphere = null,
        problem = "",
        goal = "",
        tags = emptyList(),
        initiator = UserSummary(UserId("owner-$id"), "Имя $id"),
        roles = emptyList(),
        status = null,
        period = null,
        viewerIsInitiator = false,
    ),
    initiatorUniversity = university,
)

private fun person(id: String) =
    Collaborator(UserSummary(UserId(id), "Имя $id"), "МГУ", Study("магистратура", 2), "physics")

/**
 * "01 Лента": the projects are asked for as the feed starts, the people when "Люди" is chosen for the first time; each
 * block then lives as long as the screen, and only its "Повторить" after a failure asks again.
 */
class FeedViewModelTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @OptIn(ExperimentalCoroutinesApi::class)
    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val repository = FakeFeedRepository()
    private val names = FakeViewerNames()
    private val viewer = UserId("viewer-1")
    private val cause = IllegalStateException("no network")

    private fun viewModel() = FeedViewModel(
        viewer,
        ObserveViewerNameUseCase(names),
        GetRecommendedPeopleUseCase(repository),
        GetRecommendedProjectsUseCase(repository),
    )

    private val FeedViewModel.state get() = uiState.value

    // ---- the start ----

    @Test
    fun theProjectsAreAskedForAtTheStartTheyAreTheModeTheFeedOpensInAndNoPersonIsAsked() {
        val viewModel = viewModel()

        assertEquals(1, repository.projectCalls)
        assertEquals(0, repository.calls)
        assertEquals(
            FeedUiState(
                viewerAvatarKey = "viewer-1",
                scope = FeedScope.Projects,
                projects = FeedProjectsState.Loaded(emptyList()),
                people = FeedPeopleState.NotLoaded,
            ),
            viewModel.state,
        )
    }

    @Test
    fun theAvatarGetsTheNameOfTheViewerForItsInitialsAndFollowsItsChanges() {
        names.name.value = "Арджун Мехта"
        val viewModel = viewModel()

        assertEquals("Арджун Мехта", viewModel.state.viewerName)
        assertEquals("viewer-1", viewModel.state.viewerAvatarKey)

        names.name.value = "Арджун"

        assertEquals("Арджун", viewModel.state.viewerName, "a changed name of the profile reaches the header")
    }

    @Test
    fun aNameThatCannotBeReadLeavesTheHeaderWithoutInitialsAndTheFeedWorks() {
        names.failure = cause
        repository.people = listOf(person("a"))
        val viewModel = viewModel()

        assertEquals("", viewModel.state.viewerName)
        viewModel.onScopeSelect(FeedScope.People)

        assertIs<FeedPeopleState.Loaded>(viewModel.state.people)
    }

    @Test
    fun choosingProjectsAsksNothing() {
        val viewModel = viewModel()

        viewModel.onScopeSelect(FeedScope.Projects)

        assertEquals(0, repository.calls)
        assertEquals(1, repository.projectCalls, "only the request of the start")
        assertEquals(FeedPeopleState.NotLoaded, viewModel.state.people)
    }

    // ---- the projects ----

    @Test
    fun theProjectsLoadAsTheFeedStartsInTheOrderOfTheServer() {
        repository.projects = listOf(recommended("c"), recommended("a", university = ""), recommended("b"))
        repository.projectsGate = CompletableDeferred()
        val viewModel = viewModel()

        assertEquals(FeedProjectsState.Loading, viewModel.state.projects)
        repository.projectsGate!!.complete(Unit)

        val loaded = assertIs<FeedProjectsState.Loaded>(viewModel.state.projects)
        assertEquals(listOf("c", "a", "b"), loaded.projects.map { it.id.value })
        assertEquals(repository.projects, loaded.projects.map { it.recommended })
        assertEquals(1, repository.projectCalls)
    }

    @Test
    fun noRecommendedProjectIsAnEmptyLoadedBlockNotAFailure() {
        repository.projects = emptyList()

        assertEquals(FeedProjectsState.Loaded(emptyList()), viewModel().state.projects)
    }

    @Test
    fun aFailureOfTheProjectsIsTheFailedBlockWithItsCauseAndThePeopleStillLoad() {
        repository.projectsFailure = cause
        repository.people = listOf(person("a"))
        val viewModel = viewModel()

        assertSame(cause, assertIs<FeedProjectsState.Failed>(viewModel.state.projects).cause)
        viewModel.onScopeSelect(FeedScope.People)

        assertIs<FeedPeopleState.Loaded>(viewModel.state.people)
        assertEquals(FeedProjectsState.Failed(cause), viewModel.state.projects, "the people do not touch the projects")
    }

    @Test
    fun aFailureOfThePeopleDoesNotTouchTheLoadedProjects() {
        repository.projects = listOf(recommended("a"))
        repository.failure = cause
        val viewModel = viewModel()
        val loaded = viewModel.state.projects

        viewModel.onScopeSelect(FeedScope.People)

        assertEquals(FeedPeopleState.Failed(cause), viewModel.state.people)
        assertSame(loaded, viewModel.state.projects)
    }

    @Test
    fun switchingBetweenTheScopesDoesNotAskTheProjectsAgain() {
        repository.projects = listOf(recommended("a"))
        val viewModel = viewModel()
        val loaded = viewModel.state.projects

        viewModel.onScopeSelect(FeedScope.People)
        viewModel.onScopeSelect(FeedScope.Projects)
        viewModel.onScopeSelect(FeedScope.People)
        viewModel.onScopeSelect(FeedScope.Projects)

        assertEquals(1, repository.projectCalls)
        assertSame(loaded, viewModel.state.projects)
    }

    @Test
    fun aFailureOfTheProjectsStaysUntilRetryAndTheAnswerReplacesIt() {
        repository.projectsFailure = cause
        val viewModel = viewModel()
        viewModel.onScopeSelect(FeedScope.People)
        viewModel.onScopeSelect(FeedScope.Projects)
        assertEquals(1, repository.projectCalls, "switching asks nothing")
        repository.projectsFailure = null
        repository.projects = listOf(recommended("a"), recommended("b"))
        repository.projectsGate = CompletableDeferred()

        viewModel.onProjectsRetry()

        assertEquals(FeedProjectsState.Loading, viewModel.state.projects)
        repository.projectsGate!!.complete(Unit)
        val loaded = assertIs<FeedProjectsState.Loaded>(viewModel.state.projects)
        assertEquals(listOf("a", "b"), loaded.projects.map { it.id.value })
        assertEquals(2, repository.projectCalls)
    }

    @Test
    fun aRetryThatFailsAgainIsTheNewFailureAndADoubleRetryAsksOnce() {
        repository.projectsFailure = cause
        val viewModel = viewModel()
        val second = IllegalStateException("still no network")
        repository.projectsFailure = second
        repository.projectsGate = CompletableDeferred()

        viewModel.onProjectsRetry()
        viewModel.onProjectsRetry()

        assertEquals(FeedProjectsState.Loading, viewModel.state.projects)
        assertEquals(2, repository.projectCalls, "the second tap came while the first retry was on its way")
        repository.projectsGate!!.complete(Unit)
        assertEquals(FeedProjectsState.Failed(second), viewModel.state.projects)
    }

    @Test
    fun retryDoesNothingWhileTheProjectsAreOnTheirWayOrLoaded() {
        repository.projectsGate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onProjectsRetry()

        assertEquals(1, repository.projectCalls, "the start request is still on its way")
        repository.projectsGate!!.complete(Unit)
        viewModel.onProjectsRetry()

        assertEquals(1, repository.projectCalls, "the projects are loaded")
    }

    // ---- the first choice of people ----

    @Test
    fun theFirstChoiceOfPeopleLoadsThemInTheOrderOfTheServer() {
        repository.people = listOf(person("c"), person("a"), person("b"))
        repository.gate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onScopeSelect(FeedScope.People)

        assertEquals(FeedScope.People, viewModel.state.scope)
        assertEquals(FeedPeopleState.Loading, viewModel.state.people)
        repository.gate!!.complete(Unit)
        val loaded = assertIs<FeedPeopleState.Loaded>(viewModel.state.people)
        assertEquals(listOf("c", "a", "b"), loaded.people.map { it.user.id.value })
        assertEquals(repository.people, loaded.people.map { it.collaborator })
        assertEquals(1, repository.calls)
    }

    @Test
    fun noRecommendationsIsAnEmptyLoadedBlockNotAFailure() {
        repository.people = emptyList()
        val viewModel = viewModel()

        viewModel.onScopeSelect(FeedScope.People)

        assertEquals(FeedPeopleState.Loaded(emptyList()), viewModel.state.people)
    }

    @Test
    fun theScopeFollowsTheChoiceEvenWhileThePeopleLoad() {
        repository.gate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onScopeSelect(FeedScope.People)
        viewModel.onScopeSelect(FeedScope.Projects)

        assertEquals(FeedScope.Projects, viewModel.state.scope)
        assertEquals(FeedPeopleState.Loading, viewModel.state.people)
        repository.gate!!.complete(Unit)
        assertIs<FeedPeopleState.Loaded>(viewModel.state.people)
        assertEquals(FeedScope.Projects, viewModel.state.scope)
    }

    @Test
    fun switchingBetweenProjectsAndPeopleDoesNotAskAgain() {
        repository.people = listOf(person("a"))
        val viewModel = viewModel()
        viewModel.onScopeSelect(FeedScope.People)
        val loaded = viewModel.state.people

        viewModel.onScopeSelect(FeedScope.Projects)
        viewModel.onScopeSelect(FeedScope.People)
        viewModel.onScopeSelect(FeedScope.People)

        assertEquals(1, repository.calls)
        assertSame(loaded, viewModel.state.people)
    }

    @Test
    fun choosingPeopleAgainWhileTheyLoadAsksOnce() {
        repository.gate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onScopeSelect(FeedScope.People)
        viewModel.onScopeSelect(FeedScope.Projects)
        viewModel.onScopeSelect(FeedScope.People)

        assertEquals(1, repository.calls)
    }

    // ---- a failure ----

    @Test
    fun aFailureIsTheFailedBlockWithItsCause() {
        repository.failure = cause
        val viewModel = viewModel()

        viewModel.onScopeSelect(FeedScope.People)

        assertEquals(FeedPeopleState.Failed(cause), viewModel.state.people)
        assertSame(cause, assertIs<FeedPeopleState.Failed>(viewModel.state.people).cause)
        assertEquals(FeedScope.People, viewModel.state.scope)
    }

    @Test
    fun aFailureStaysWhenTheScopeIsSwitchedOnlyRetryAsksAgain() {
        repository.failure = cause
        val viewModel = viewModel()
        viewModel.onScopeSelect(FeedScope.People)

        viewModel.onScopeSelect(FeedScope.Projects)
        viewModel.onScopeSelect(FeedScope.People)

        assertEquals(1, repository.calls)
        assertEquals(FeedPeopleState.Failed(cause), viewModel.state.people)
    }

    @Test
    fun retryAsksAgainAndTheAnswerReplacesTheFailure() {
        repository.failure = cause
        val viewModel = viewModel()
        viewModel.onScopeSelect(FeedScope.People)
        repository.failure = null
        repository.people = listOf(person("a"), person("b"))
        repository.gate = CompletableDeferred()

        viewModel.onPeopleRetry()

        assertEquals(FeedPeopleState.Loading, viewModel.state.people)
        repository.gate!!.complete(Unit)
        val loaded = assertIs<FeedPeopleState.Loaded>(viewModel.state.people)
        assertEquals(listOf("a", "b"), loaded.people.map { it.user.id.value })
        assertEquals(2, repository.calls)
    }

    @Test
    fun aRetryThatFailsAgainIsTheNewFailure() {
        repository.failure = cause
        val viewModel = viewModel()
        viewModel.onScopeSelect(FeedScope.People)
        val second = IllegalStateException("still no network")
        repository.failure = second

        viewModel.onPeopleRetry()

        assertEquals(FeedPeopleState.Failed(second), viewModel.state.people)
        assertEquals(2, repository.calls)
    }

    @Test
    fun aDoubleRetryAsksOnce() {
        repository.failure = cause
        val viewModel = viewModel()
        viewModel.onScopeSelect(FeedScope.People)
        repository.failure = null
        repository.gate = CompletableDeferred()

        viewModel.onPeopleRetry()
        viewModel.onPeopleRetry()
        viewModel.onPeopleRetry()

        assertEquals(2, repository.calls)
    }

    // ---- retry where there is nothing to retry ----

    @Test
    fun retryDoesNothingBeforePeopleWereEverChosen() {
        val viewModel = viewModel()

        viewModel.onPeopleRetry()

        assertEquals(0, repository.calls)
        assertEquals(FeedPeopleState.NotLoaded, viewModel.state.people)
    }

    @Test
    fun retryDoesNothingWhileThePeopleLoad() {
        repository.gate = CompletableDeferred()
        val viewModel = viewModel()
        viewModel.onScopeSelect(FeedScope.People)

        viewModel.onPeopleRetry()

        assertEquals(1, repository.calls)
        assertEquals(FeedPeopleState.Loading, viewModel.state.people)
    }

    @Test
    fun retryDoesNothingOnceThePeopleAreLoaded() {
        repository.people = listOf(person("a"))
        val viewModel = viewModel()
        viewModel.onScopeSelect(FeedScope.People)
        val loaded = viewModel.state.people

        viewModel.onPeopleRetry()

        assertEquals(1, repository.calls)
        assertSame(loaded, viewModel.state.people)
    }
}
