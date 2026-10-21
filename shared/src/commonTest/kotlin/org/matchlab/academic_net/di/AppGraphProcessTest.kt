package org.matchlab.academic_net.di

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.matchlab.academic_net.Backend
import org.matchlab.academic_net.RecordingAnalytics
import org.matchlab.academic_net.analytics.NoOpAnalytics
import org.matchlab.academic_net.analytics.UserSource
import org.matchlab.academic_net.securestore.InMemorySecureStore

/**
 * [AppGraph], the holder of the graphs of the process: made on the first call from the arguments of that call, the same
 * graphs for every call after it. It is how the platform's analytics reaches the controller and the clicks of the app.
 *
 * The holder is a singleton of the process, so this is the only test that goes through it (the other tests build their own
 * [AppGraphs] over an [AppLaunch]): a second one would meet the graphs of the first. The graphs it makes are never closed,
 * as in the app; they start nothing that touches the network (there is no saved session to ask the server about).
 */
class AppGraphProcessTest {
    @Test
    fun theGraphsAreMadeOnceFromTheArgumentsOfTheFirstCall() {
        val analytics = RecordingAnalytics()
        var stores = 0

        val first = AppGraph.get({ stores++; InMemorySecureStore() }, Backend.Gateway(), analytics)
        val second = AppGraph.get({ stores++; InMemorySecureStore() }, Backend.Gateway(), NoOpAnalytics)

        assertSame(first, second)
        assertEquals(1, stores, "the secure store of the platform is asked for once")
        assertSame(analytics, first.app.analytics, "the analytics of the first call stays for the process")
        // The clicks of the app are counted with that analytics.
        first.clicks.userClicked("user-1", UserSource.FeedPeople)
        assertEquals(listOf("user_click"), analytics.events.map { it.name })
    }
}
