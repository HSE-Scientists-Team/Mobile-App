package org.matchlab.academic_net.feature.profile.ui.my

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the viewer's own profile ("10 Мой профиль") is on the screen right now.
 *
 * The tabs of the app live inside one navigation destination, so a tab has no lifecycle of its own
 * and its view model cannot tell by itself that the user has left it. [MyProfileRoute] reports the
 * visibility (enter and leave of the composable plus `ON_START`/`ON_STOP` of the host), and two
 * readers use it: [MyProfileViewModel] marks decisions of moderators as seen only while the
 * screen is visible, and [profileTabHasBadge] keeps the dot off the tab that is being looked at.
 *
 * One instance lives in `ProfileUiDependencies`, so the route and the tab bar share it.
 */
internal class MyProfileVisibility {
    private val visible = MutableStateFlow(false)

    /** `true` between the moment the profile appears and the moment the user leaves it. */
    val isVisible: StateFlow<Boolean> = visible

    /** The profile appeared on the screen (idempotent). */
    fun onShown() {
        visible.value = true
    }

    /** The user left the profile: another tab, another screen or the background (idempotent). */
    fun onHidden() {
        visible.value = false
    }
}
