package org.matchlab.academic_net.ui.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rule of [navigateOnce], [navigateSingleTop] and [popBackStackOnce]. The functions on
 * `NavController` only read the id of the entry on top of the back stack and hand it to these two;
 * they are checked on a device, where a real back stack runs (a `NavController` cannot be built in
 * a host test without an Android context).
 */
class NavigationGuardTest {
    private val profile = "entry-profile"
    private val settings = "entry-settings"

    @Test
    fun theScreenThatAsksIsTheCurrentOne() {
        assertTrue(isCurrentEntry(fromId = profile, currentId = profile))
    }

    @Test
    fun aScreenThatIsNoLongerOnTopIsNotCurrent() {
        // The profile has opened the settings: the settings are on top, so what the profile still
        // asks for (the next tap of a burst, a late event) is dropped.
        assertFalse(isCurrentEntry(fromId = profile, currentId = settings))
        assertTrue(isCurrentEntry(fromId = settings, currentId = settings))
    }

    @Test
    fun anEmptyBackStackHasNoCurrentScreen() {
        assertFalse(isCurrentEntry(fromId = profile, currentId = null))
    }

    @Test
    fun entryIdsAreComparedWhole() {
        assertFalse(isCurrentEntry(fromId = "entry-1", currentId = "entry-10"))
        assertFalse(isCurrentEntry(fromId = "entry-10", currentId = "entry-1"))
        assertFalse(isCurrentEntry(fromId = "entry-a", currentId = "ENTRY-A"))
    }

    @Test
    fun theCurrentScreenWithAScreenBelowItMayBePopped() {
        assertTrue(mayPopEntry(fromId = settings, currentId = settings, hasEntryBelow = true))
    }

    @Test
    fun theRootIsNeverPopped() {
        // The only entry has nothing to go back to: popping it would leave an empty window.
        assertFalse(mayPopEntry(fromId = profile, currentId = profile, hasEntryBelow = false))
    }

    @Test
    fun aScreenThatIsNoLongerOnTopMayNotBePopped() {
        // Back was tapped twice: the first tap popped the settings, and the second one still comes
        // from the settings, whose screen is on the display while it fades out.
        assertFalse(mayPopEntry(fromId = settings, currentId = profile, hasEntryBelow = true))
    }

    @Test
    fun nothingIsPoppedFromAnEmptyBackStack() {
        assertFalse(mayPopEntry(fromId = settings, currentId = null, hasEntryBelow = false))
        assertFalse(mayPopEntry(fromId = settings, currentId = null, hasEntryBelow = true))
    }
}
