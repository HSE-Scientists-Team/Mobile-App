package org.matchlab.academic_net.feature.feed.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/** A recommended person with the line under their name. */
@Immutable
data class FeedPerson(
    val user: UserSummary,
    val subtitle: String,
)

@Immutable
data class FeedUiState(
    val scope: FeedScope = FeedScope.Projects,
    /** Key of the viewer's avatar illustration, `null` until the viewer is loaded. */
    val viewerAvatarKey: String? = null,
    val isPeopleLoading: Boolean = true,
    val peopleFailed: Boolean = false,
    val people: List<FeedPerson> = emptyList(),
)

/**
 * "01 Лента": the header with the viewer's avatar, the scope of the search, and the recommended
 * people of "01c Лента — люди". Recommended projects are not built yet.
 */
class FeedViewModel(
    private val getViewer: GetViewerUseCase,
    private val getRecommendedPeople: GetRecommendedPeopleUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = state.asStateFlow()

    init {
        loadViewer()
        loadPeople()
    }

    fun onScopeSelect(scope: FeedScope) = state.update { it.copy(scope = scope) }

    fun onPeopleRetry() = loadPeople()

    private fun loadViewer() {
        viewModelScope.launch {
            // The avatar is decoration: if it cannot be loaded the header keeps the default one.
            getViewer().onSuccess { viewer ->
                state.update { it.copy(viewerAvatarKey = viewer.id.value) }
            }
        }
    }

    private fun loadPeople() {
        state.update { it.copy(isPeopleLoading = true, peopleFailed = false) }
        viewModelScope.launch {
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

private fun Collaborator.toFeedPerson() = FeedPerson(user, subtitle)
