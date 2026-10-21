package org.matchlab.academic_net.feature.responses.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the tab "Отклики" (15) is on the screen right now.
 *
 * The tabs of the app live inside one navigation destination, so a tab has no lifecycle of its own and its view model
 * cannot tell by itself that the user has left it. `ResponsesRoute` reports the visibility, and two readers use it: the
 * view model marks the invitations as seen only while the tab is shown, and [responsesTabHasBadge] keeps the dot off the
 * tab that is being looked at. One instance lives in [ResponsesUiDependencies], so the route and the tab bar share it.
 */
internal class ResponsesVisibility {
    private val visible = MutableStateFlow(false)

    /** `true` between the moment the tab appears and the moment the user leaves it. */
    val isVisible: StateFlow<Boolean> = visible

    /** The tab appeared on the screen (idempotent). */
    fun onShown() {
        visible.value = true
    }

    /** The user left the tab: another tab, another screen or the background (idempotent). */
    fun onHidden() {
        visible.value = false
    }
}
