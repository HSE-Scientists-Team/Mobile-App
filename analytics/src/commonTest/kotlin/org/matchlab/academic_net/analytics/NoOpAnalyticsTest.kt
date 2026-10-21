package org.matchlab.academic_net.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

/** [NoOpAnalytics] is what builds without a key, the tests and the previews run with: it does nothing, and nothing in it fails. */
class NoOpAnalyticsTest {
    @Test
    fun noCallDoesAnythingOrThrows() {
        NoOpAnalytics.track("project_click")
        NoOpAnalytics.track("project_click", mapOf("project_id" to "p-1", "source" to "responses"))
        NoOpAnalytics.track("", emptyMap())
        NoOpAnalytics.setUserProfileId("user-1")
        NoOpAnalytics.setUserProfileId(null)
        NoOpAnalytics.setEnabled(true)
        NoOpAnalytics.setEnabled(false)
    }

    @Test
    fun itIsTheSameObjectEverywhere() {
        val analytics: Analytics = NoOpAnalytics

        assertEquals(NoOpAnalytics, analytics)
    }

    @Test
    fun theParametersOfAnEventDefaultToNone() {
        // The default is declared on the interface, so it holds for every implementation.
        val recording = RecordingAnalytics()
        val analytics: Analytics = recording

        analytics.track("project_click")

        assertEquals(listOf(RecordingAnalytics.Event("project_click", emptyMap())), recording.events)
    }
}
