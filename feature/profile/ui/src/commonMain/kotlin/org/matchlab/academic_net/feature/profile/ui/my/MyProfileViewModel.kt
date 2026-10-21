package org.matchlab.academic_net.feature.profile.ui.my

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.matchlab.academic_net.feature.profile.domain.ProfileCapabilities
import org.matchlab.academic_net.feature.profile.domain.VerificationItem
import org.matchlab.academic_net.feature.profile.domain.VerificationResult
import org.matchlab.academic_net.feature.profile.domain.VerificationResultId
import org.matchlab.academic_net.feature.profile.domain.usecase.MarkResultsSeenUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveMyProfileUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveVerificationResultsUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.RefreshMyProfileUseCase
import org.matchlab.academic_net.feature.profile.ui.ProfileUiModel
import org.matchlab.academic_net.feature.profile.ui.VerificationResultItem
import org.matchlab.academic_net.feature.profile.ui.toUiModel
import org.matchlab.academic_net.utils.chatListTime

/** What "10 Мой профиль" shows: the loading, the failure or the profile with its verification. */
sealed interface MyProfileUiState {
    data object Loading : MyProfileUiState

    /** The profile could not be loaded because of [cause]; "Повторить" asks again. */
    data class Error(val cause: Throwable) : MyProfileUiState

    /**
     * @property needsName a new account: it has no name yet (API-GAP G19), so the header says "Имя не указано" and every
     *  section says "Не заполнено" until the form of the profile is saved with a name
     * @property queue the works and documents that are being checked or were rejected; always empty without
     *  [ProfileCapabilities.verificationStatuses] (API-GAP G12)
     * @property results the decisions of the moderators that were shown to the viewer on this
     *  visit of the screen, newest first; always empty without [ProfileCapabilities.verificationStatuses]
     */
    @Immutable
    data class Content(
        val profile: ProfileUiModel,
        val needsName: Boolean,
        val queue: List<VerificationItem>,
        val results: List<VerificationResultItem>,
    ) : MyProfileUiState
}

/**
 * "10 Мой профиль": the viewer's own profile, the verification of its works and documents and the
 * notifications about the decisions of moderators.
 *
 * **The profile is asked for again when the screen is shown** ([onScreenShown]): when the user comes back to the tab
 * and when the app comes back from the background, so what was changed on another device or by a moderator is not
 * stale. The first load is the stream's own and is not doubled; a refresh that is still running is not started again,
 * and a failed one is not told (the profile that is on the screen stays).
 *
 * **Decisions of the moderators** (API-GAP G12: the Gateway has none, [ProfileCapabilities.verificationStatuses] is
 * `false`, and then they are not even observed). A decision is news until it is shown here. While the screen is visible
 * ([onScreenShown] .. [onScreenLeft]) the view model shows every unseen decision and marks it seen, so the dot on the
 * tab goes out. While the screen is not visible nothing is marked: the tabs share one navigation
 * destination, and the state of this view model outlives the tab by [StopTimeoutMillis], so a
 * decision that arrives after the user has left the tab must stay news. The decisions that were
 * shown stay on the screen until the user leaves it.
 *
 * @param visibility shared with the dot on the tab ([profileTabHasBadge]); this view model is the
 *  one that writes it
 * @param capabilities what the server can give: without the states of the records there is no queue and no results
 */
class MyProfileViewModel internal constructor(
    observeMyProfile: ObserveMyProfileUseCase,
    private val refreshMyProfile: RefreshMyProfileUseCase,
    observeVerificationResults: ObserveVerificationResultsUseCase,
    private val markResultsSeen: MarkResultsSeenUseCase,
    private val visibility: MyProfileVisibility,
    private val capabilities: ProfileCapabilities,
    private val clock: Clock,
    private val timeZone: () -> TimeZone,
) : ViewModel() {
    private val attempt = MutableStateFlow(0)

    /** Results that have been shown on this visit: they stay on the screen though they are seen. */
    private val shown = MutableStateFlow<Set<VerificationResultId>>(emptySet())

    private var refreshJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MyProfileUiState> = attempt
        .flatMapLatest {
            // Also reacts to the screen becoming visible: decisions that came while it was not are
            // shown (and marked) at that moment, not at the next change of the data.
            val results: Flow<List<VerificationResult>> = if (capabilities.verificationStatuses) {
                combine(observeVerificationResults(), visibility.isVisible) { all, visible ->
                    if (visible) markNewResultsShown(all)
                    all
                }
            } else {
                flowOf(emptyList())
            }
            combine(observeMyProfile(), results, shown) { my, all, shownIds ->
                val now = clock.now()
                val zone = timeZone()
                val items = all.filter { it.id in shownIds }.map { it.toItem(now, zone) }
                val content: MyProfileUiState = MyProfileUiState.Content(
                    profile = my.profile.toUiModel(),
                    needsName = my.needsName,
                    // API-GAP G12: a source without states has an empty queue; the flag keeps it so whatever the data says.
                    queue = if (capabilities.verificationStatuses) my.verificationQueue else emptyList(),
                    results = items,
                )
                content
            }.catch { emit(MyProfileUiState.Error(it)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), MyProfileUiState.Loading)

    /** "Повторить" after a failed load. */
    fun onRetry() = attempt.update { it + 1 }

    /**
     * The profile is on the screen: from now on the decisions that arrive are shown and seen, and the profile is asked
     * for again if it is already there (the first load is not doubled).
     */
    fun onScreenShown() {
        visibility.onShown()
        if (uiState.value !is MyProfileUiState.Content || refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch { refreshMyProfile() }
    }

    /**
     * The user left the screen (another tab, another screen, the app went to the background):
     * from now on decisions are not marked seen, and those that were shown are history now and
     * leave the list.
     */
    fun onScreenLeft() {
        visibility.onHidden()
        shown.value = emptySet()
    }

    /**
     * Shows the unseen [results] and asks the server to mark them seen. The dot on the tab is
     * kept off by [MyProfileVisibility] while the screen is visible, so the moment between the
     * decision and the answer of the server does not make it blink.
     */
    private fun markNewResultsShown(results: List<VerificationResult>) {
        val fresh = results.filter { !it.isSeen && it.id !in shown.value }.map { it.id }.toSet()
        if (fresh.isEmpty()) return
        shown.update { it + fresh }
        // A failed call only leaves the dot on the tab until the next visit.
        viewModelScope.launch { markResultsSeen(fresh) }
    }

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}

private fun VerificationResult.toItem(now: kotlin.time.Instant, zone: TimeZone) = VerificationResultItem(
    id = id,
    subject = subject,
    title = subjectTitle,
    outcome = outcome,
    time = chatListTime(decidedAt, now, zone),
)
