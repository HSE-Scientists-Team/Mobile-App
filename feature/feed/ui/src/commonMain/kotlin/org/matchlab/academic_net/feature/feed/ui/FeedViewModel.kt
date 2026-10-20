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
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.feed.domain.usecase.GetRecommendedPeopleUseCase
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
 * @property people the recommended people of the "Люди" scope
 */
@Immutable
data class FeedUiState(
    val viewerAvatarKey: String,
    val viewerName: String = "",
    val scope: FeedScope = FeedScope.Projects,
    val people: FeedPeopleState = FeedPeopleState.NotLoaded,
)

/**
 * "01 Лента": the header with the viewer's avatar, the scope of the search, and the recommended people of "01c Лента —
 * люди". Recommended projects are not built yet.
 *
 * The people are asked for when the "Люди" scope is shown for the first time, not at the start: the feed opens on
 * "Проекты", and a request nobody sees is a wasted one. Whatever comes back (the people, or the failure) stays in the
 * state for as long as the screen lives, so a tap on the other scope and back does not ask again; only "Повторить" asks
 * after a failure. A load starts only from [FeedPeopleState.NotLoaded] or [FeedPeopleState.Failed] and moves the state to
 * [FeedPeopleState.Loading] at once, so a burst of taps on "Повторить" makes one request.
 *
 * API-GAP G32: the server recommends random people open to collaboration, not people for the viewer's projects.
 *
 * @param viewerId the signed-in user; the header asks for their picture by the id
 * @param observeViewerName the name of the signed-in user: the header shows their initials until the picture is there. A
 * name that cannot be read leaves the header without initials, the feed itself goes on
 */
class FeedViewModel(
    viewerId: UserId,
    observeViewerName: ObserveViewerNameUseCase,
    private val getRecommendedPeople: GetRecommendedPeopleUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(FeedUiState(viewerAvatarKey = viewerId.value))
    val uiState: StateFlow<FeedUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            observeViewerName()
                .catch { /* The avatar of the header keeps no initials; nothing else depends on the name. */ }
                .collect { name -> state.update { it.copy(viewerName = name) } }
        }
    }

    /** Switches the scope; the first time [FeedScope.People] is shown it asks for the recommended people. */
    fun onScopeSelect(scope: FeedScope) {
        state.update { it.copy(scope = scope) }
        if (scope == FeedScope.People && state.value.people is FeedPeopleState.NotLoaded) loadPeople()
    }

    /** "Повторить" after the people failed to load; it does nothing in any other state. */
    fun onPeopleRetry() {
        if (state.value.people is FeedPeopleState.Failed) loadPeople()
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
