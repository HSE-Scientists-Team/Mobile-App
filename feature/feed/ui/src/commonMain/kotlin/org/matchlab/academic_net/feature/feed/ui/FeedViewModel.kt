package org.matchlab.academic_net.feature.feed.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.feed.domain.RecommendedProject
import org.matchlab.academic_net.feature.feed.domain.usecase.GetRecommendedPeopleUseCase
import org.matchlab.academic_net.feature.feed.domain.usecase.GetRecommendedProjectsUseCase
import org.matchlab.academic_net.feature.feed.domain.usecase.ObserveViewerNameUseCase

/** What the search bar and the list of the feed are about (the "search-scope" of the design). */
enum class FeedScope { Projects, People }

/**
 * A recommended person. The line under their name ("Stanford · Бакалавриат, 3 курс · Computer Science") is made by the
 * screen from [collaborator] (`subtitleText` of `:components`), because the study is written in the language of the app.
 */
@Immutable
data class FeedPerson(
    val collaborator: Collaborator,
) {
    val user: UserSummary get() = collaborator.user
}

/**
 * A recommended project. The card is made by the screen from [recommended]: the labels of the type, the status and the
 * months are written in the language of the app.
 */
@Immutable
data class FeedProject(
    val recommended: RecommendedProject,
) {
    val id: ProjectId get() = recommended.project.id
}

/**
 * The projects block of "01 Лента", the mode the feed opens in. The answer of the server lives as long as the feed does,
 * so the switch "Проекты ↔ Люди" never asks again; only "Повторить" after a [Failed] does.
 */
sealed interface FeedProjectsState {
    /** The recommendations are on their way (they are asked for as the feed starts): the list shows the spinner. */
    data object Loading : FeedProjectsState

    /** They could not be loaded because of [cause]: the block says so and offers "Повторить". */
    data class Failed(val cause: Throwable) : FeedProjectsState

    /** The server's answer, in its order; may be empty ("Пока нет рекомендаций"). */
    @Immutable
    data class Loaded(val projects: List<FeedProject>) : FeedProjectsState
}

/**
 * The people block of "01c Лента — люди". The answer of the server lives as long as the feed does, so the switch
 * "Проекты ↔ Люди" never asks again; only "Повторить" after a [Failed] does.
 */
sealed interface FeedPeopleState {
    /** "Люди" has not been shown yet: nothing was asked. */
    data object NotLoaded : FeedPeopleState

    /** The recommendations are on their way: the list shows the spinner. */
    data object Loading : FeedPeopleState

    /** They could not be loaded because of [cause]: the block says so and offers "Повторить". */
    data class Failed(val cause: Throwable) : FeedPeopleState

    /** The server's answer, in its order; may be empty ("Пока нет рекомендаций"). */
    @Immutable
    data class Loaded(val people: List<FeedPerson>) : FeedPeopleState
}

/**
 * @property viewerAvatarKey the key of the viewer's picture: the id of the signed-in user
 * @property viewerName the name of the signed-in user for the initials of their avatar; empty while it is not known
 * @property scope the mode of the search bar and of the list
 * @property projects the recommended projects of the "Проекты" scope
 * @property people the recommended people of the "Люди" scope
 */
@Immutable
data class FeedUiState(
    val viewerAvatarKey: String,
    val viewerName: String = "",
    val scope: FeedScope = FeedScope.Projects,
    val projects: FeedProjectsState = FeedProjectsState.Loading,
    val people: FeedPeopleState = FeedPeopleState.NotLoaded,
)

/**
 * "01 Лента": the header with the viewer's avatar, the scope of the search, the recommended projects of the "Проекты"
 * scope and the recommended people of "01c Лента — люди".
 *
 * The feed opens on "Проекты", so the projects are asked for as the feed starts. The people are asked for when the "Люди"
 * scope is shown for the first time, not at the start: a request nobody sees is a wasted one. Whatever comes back (the
 * projects or the people, or the failure) stays in the state for as long as the screen lives, so a tap on the other scope
 * and back does not ask again; only "Повторить" asks after a failure. A load starts only from the state that has no answer
 * ([FeedPeopleState.NotLoaded] or a `Failed` one) and moves the state to `Loading` at once, so a burst of taps on
 * "Повторить" makes one request.
 *
 * API-GAP G32: the server's description of the recommended people changed from random people open to collaboration to a
 * ranking of a periodic calculation with a fallback list; the app shows them as they come.
 *
 * @param viewerId the signed-in user; the header asks for their picture by the id
 * @param observeViewerName the name of the signed-in user: the header shows their initials until the picture is there. A
 * name that cannot be read leaves the header without initials, the feed itself goes on
 */
class FeedViewModel(
    viewerId: UserId,
    observeViewerName: ObserveViewerNameUseCase,
    private val getRecommendedPeople: GetRecommendedPeopleUseCase,
    private val getRecommendedProjects: GetRecommendedProjectsUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(FeedUiState(viewerAvatarKey = viewerId.value))
    val uiState: StateFlow<FeedUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            observeViewerName()
                .catch { /* The avatar of the header keeps no initials; nothing else depends on the name. */ }
                .collect { name -> state.update { it.copy(viewerName = name) } }
        }
        loadProjects()
    }

    /** Switches the scope; the first time [FeedScope.People] is shown it asks for the recommended people. */
    fun onScopeSelect(scope: FeedScope) {
        state.update { it.copy(scope = scope) }
        if (scope == FeedScope.People && state.value.people is FeedPeopleState.NotLoaded) loadPeople()
    }

    /** "Повторить" after the projects failed to load; it does nothing in any other state. */
    fun onProjectsRetry() {
        if (state.value.projects is FeedProjectsState.Failed) loadProjects()
    }

    /** "Повторить" after the people failed to load; it does nothing in any other state. */
    fun onPeopleRetry() {
        if (state.value.people is FeedPeopleState.Failed) loadPeople()
    }

    private fun loadProjects() {
        state.update { it.copy(projects = FeedProjectsState.Loading) }
        viewModelScope.launch {
            getRecommendedProjects().fold(
                onSuccess = { projects ->
                    state.update { it.copy(projects = FeedProjectsState.Loaded(projects.map(::FeedProject))) }
                },
                onFailure = { cause -> state.update { it.copy(projects = FeedProjectsState.Failed(cause)) } },
            )
        }
    }

    private fun loadPeople() {
        state.update { it.copy(people = FeedPeopleState.Loading) }
        viewModelScope.launch {
            getRecommendedPeople().fold(
                onSuccess = { people ->
                    state.update { it.copy(people = FeedPeopleState.Loaded(people.map(::FeedPerson))) }
                },
                onFailure = { cause -> state.update { it.copy(people = FeedPeopleState.Failed(cause)) } },
            )
        }
    }
}
