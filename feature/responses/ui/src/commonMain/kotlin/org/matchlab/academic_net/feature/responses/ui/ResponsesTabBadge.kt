package org.matchlab.academic_net.feature.responses.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether the tab "Отклики" shows the dot: the viewer has a waiting invitation that came after the last time they looked
 * at 15 (`ObserveHasNewInvitationsUseCase`), and 15 is not on the screen. The dot goes out when 15 marks the
 * invitations as seen, and it stays off while 15 is shown (what arrives on the open screen is not news).
 *
 * The list of the viewer's records is read once per [dependencies], when the tab bar first appears (the app has no
 * push, API-GAP G61); a failure of that read is silent: no dot. Call it where the bottom navigation is built; it
 * observes the flag while it is in the composition.
 */
@Composable
fun responsesTabHasBadge(dependencies: ResponsesUiDependencies): Boolean {
    val hasBadge = remember(dependencies) { dependencies.observeTabBadge() }
    val value by hasBadge.collectAsStateWithLifecycle(initialValue = false)
    LaunchedEffect(dependencies) {
        if (!dependencies.tabListRequested) {
            dependencies.tabListRequested = true
            // A failure is silent: the tab simply has no dot. A cancellation passes through the use case.
            dependencies.refreshMyResponses()
        }
    }
    return value
}

/** The dot: there are new invitations and the tab is not on the screen. */
internal fun ResponsesUiDependencies.observeTabBadge(): Flow<Boolean> =
    combine(observeHasNewInvitations(), visibility.isVisible) { hasNew, visible -> hasNew && !visible }
        .catch { emit(false) }
        .distinctUntilChanged()
