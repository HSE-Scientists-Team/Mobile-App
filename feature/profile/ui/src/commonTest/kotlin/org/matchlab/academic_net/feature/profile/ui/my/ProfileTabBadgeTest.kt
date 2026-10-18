package org.matchlab.academic_net.feature.profile.ui.my

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.feature.profile.domain.ProfileCapabilities
import org.matchlab.academic_net.feature.profile.domain.VerificationOutcome
import org.matchlab.academic_net.feature.profile.domain.VerificationResult
import org.matchlab.academic_net.feature.profile.domain.VerificationResultId
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.feature.profile.domain.WorkId
import org.matchlab.academic_net.feature.profile.ui.FakeMyProfileRepository
import org.matchlab.academic_net.feature.profile.ui.profileUiDependencies
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The dot on the "Профиль" tab: a decision of a moderator the viewer has not been shown, while the profile is not on the
 * screen. API-GAP G12: a server without the states of records never lights it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileTabBadgeTest {
    private val withStatuses = ProfileCapabilities(verificationStatuses = true, interestTypes = false)
    private val repository = FakeMyProfileRepository()
    private val results = MutableStateFlow(emptyList<VerificationResult>())

    init {
        repository.results = results
    }

    private fun decision(id: String, seen: Boolean) = VerificationResult(
        id = VerificationResultId(id),
        subject = VerificationSubject.Work(WorkId("w")),
        subjectTitle = "Работа",
        outcome = VerificationOutcome.Verified,
        decidedAt = Instant.fromEpochSeconds(1_760_000_000),
        isSeen = seen,
    )

    @Test
    fun aServerWithoutStatesNeverLightsTheDot() = runTest {
        val dependencies = profileUiDependencies(repository, capabilities = ProfileCapabilities(false, false))
        results.value = listOf(decision("1", seen = false))

        assertEquals(false, dependencies.observeTabBadge().first())
    }

    @Test
    fun anUnseenDecisionLightsTheDotWhileTheProfileIsNotOnTheScreen() = runTest {
        val dependencies = profileUiDependencies(repository, capabilities = withStatuses)
        results.value = listOf(decision("1", seen = false))

        assertEquals(true, dependencies.observeTabBadge().first())
    }

    @Test
    fun theDotStaysOffWhileTheProfileIsVisible() = runTest {
        val dependencies = profileUiDependencies(repository, capabilities = withStatuses)
        results.value = listOf(decision("1", seen = false))
        dependencies.myProfileVisibility.onShown()

        assertEquals(false, dependencies.observeTabBadge().first())
    }

    @Test
    fun withoutAnUnseenDecisionThereIsNoDot() = runTest {
        val dependencies = profileUiDependencies(repository, capabilities = withStatuses)
        results.value = listOf(decision("1", seen = true))

        assertEquals(false, dependencies.observeTabBadge().first())
        results.value = emptyList()
        assertEquals(false, dependencies.observeTabBadge().first())
    }

    @Test
    fun theDotFollowsTheDecisionsAndTheVisibilityAndEmitsOnlyChanges() = runTest {
        val dependencies = profileUiDependencies(repository, capabilities = withStatuses)
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dependencies.observeTabBadge().toList(seen)
        }

        results.value = listOf(decision("1", seen = false))          // news arrives off screen: on
        results.value = listOf(decision("1", seen = false), decision("2", seen = false)) // still on: no new value
        dependencies.myProfileVisibility.onShown()                    // the user looks: off
        results.value = listOf(decision("1", seen = true), decision("2", seen = true)) // marked seen: still off
        dependencies.myProfileVisibility.onHidden()                   // leaves: nothing unseen, still off
        results.value = listOf(decision("3", seen = false))          // new news: on

        assertEquals(listOf(false, true, false, true), seen)
    }
}
