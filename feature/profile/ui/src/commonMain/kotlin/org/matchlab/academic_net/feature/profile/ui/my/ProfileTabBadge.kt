package org.matchlab.academic_net.feature.profile.ui.my

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import org.matchlab.academic_net.feature.profile.ui.ProfileUiDependencies

/**
 * Whether the "Профиль" tab shows the dot: a moderator has decided about a work or a document of
 * the viewer, and the viewer has not been shown the decision yet. It goes out when
 * [MyProfileRoute] shows the decision, and it stays off while that screen is visible (the
 * decisions that arrive on the open screen are shown at once, so they are not news).
 *
 * API-GAP G12: without [org.matchlab.academic_net.feature.profile.domain.ProfileCapabilities.verificationStatuses]
 * (the Gateway has no statuses and no notifications) there are no decisions, so the dot is never shown.
 *
 * Call it where the bottom navigation is built; it observes the decisions while it is in the
 * composition.
 */
@Composable
fun profileTabHasBadge(dependencies: ProfileUiDependencies): Boolean {
    val hasBadge = remember(dependencies) { dependencies.observeTabBadge() }
    val value by hasBadge.collectAsStateWithLifecycle(initialValue = false)
    return value
}

/**
 * The dot on the "Профиль" tab: there are decisions not shown yet and the profile is not on the
 * screen. The second condition is what keeps the dot from blinking while the profile marks a
 * decision that has just arrived as seen (the server answers a moment later).
 *
 * Known edge: a decision that arrives within the latency of the server before the user leaves the
 * tab can light the dot until the marking request completes.
 *
 * API-GAP G12: a source that does not tell the states of the records never lights the dot.
 */
internal fun ProfileUiDependencies.observeTabBadge(): Flow<Boolean> {
    if (!capabilities.verificationStatuses) return flowOf(false)
    return combine(observeHasUnseenResults(), myProfileVisibility.isVisible) { hasUnseen, visible ->
        hasUnseen && !visible
    }.distinctUntilChanged()
}
