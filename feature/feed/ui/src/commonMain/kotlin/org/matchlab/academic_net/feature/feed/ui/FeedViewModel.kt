package org.matchlab.academic_net.feature.feed.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.feed.domain.usecase.GetRecommendedPeopleUseCase
import org.matchlab.academic_net.feature.feed.domain.usecase.GetViewerUseCase

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
 * @property viewerAvatarKey key of the viewer's avatar illustration, `null` until the viewer is loaded
 * @property isPeopleLoading the recommended people have not arrived yet: they are on their way, or
 * still not asked for because the "Люди" scope has not been shown
 * @property peopleFailed the last request for the recommended people failed
 */
@Immutable
data class FeedUiState(
    val scope: FeedScope = FeedScope.Projects,
    val viewerAvatarKey: String? = null,
    val isPeopleLoading: Boolean = true,
    val peopleFailed: Boolean = false,
    val people: List<FeedPerson> = emptyList(),
)

/**
 * "01 Лента": the header with the viewer's avatar, the scope of the search, and the recommended
 * people of "01c Лента — люди". Recommended projects are not built yet.
 *
 * The people are asked for when the "Люди" scope is shown for the first time, not at the start: the
 * feed opens on "Проекты", and a request nobody sees is a wasted one. Whatever comes back (the people,
 * or the failure) stays in the state for as long as the screen lives, so a tap on the other scope and
 * back does not ask again; only "Повторить" asks after a failure.
 */
class FeedViewModel(
    private val getViewer: GetViewerUseCase,
    private val getRecommendedPeople: GetRecommendedPeopleUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = state.asStateFlow()

    private var peopleJob: Job? = null

    /** The people were asked for at least once: they are loaded, have failed or are on their way. */
    private var peopleRequested = false

    init {
        loadViewer()
    }

    /** Switches the scope; the first time [FeedScope.People] is shown it asks for the recommended people. */
    fun onScopeSelect(scope: FeedScope) {
        state.update { it.copy(scope = scope) }
        if (scope == FeedScope.People && !peopleRequested) loadPeople()
    }

    /** "Повторить" after the people failed to load; ignored while a request is still on its way. */
    fun onPeopleRetry() = loadPeople()

    private fun loadViewer() {
        viewModelScope.launch {
            // The avatar is decoration: if it cannot be loaded the header keeps the default one.
            getViewer().onSuccess { viewer ->
                state.update { it.copy(viewerAvatarKey = viewer.id.value) }
            }
        }
    }

    /** The first show of the people and "Повторить"; a load that is still on its way is not doubled. */
    private fun loadPeople() {
        if (peopleJob?.isActive == true) return
        peopleRequested = true
        state.update { it.copy(isPeopleLoading = true, peopleFailed = false) }
        peopleJob = viewModelScope.launch {
            getRecommendedPeople().fold(
                onSuccess = { people ->
                    state.update {
                        it.copy(isPeopleLoading = false, people = people.map(Collaborator::toFeedPerson))
                    }
                },
                onFailure = { state.update { it.copy(isPeopleLoading = false, peopleFailed = true) } },
            )
        }
    }
}

private fun Collaborator.toFeedPerson() = FeedPerson(this)
