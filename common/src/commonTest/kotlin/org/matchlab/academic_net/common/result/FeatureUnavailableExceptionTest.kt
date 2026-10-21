package org.matchlab.academic_net.common.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FeatureUnavailableExceptionTest {
    @Test
    fun itNamesTheGapOfTheApi() {
        val failure = FeatureUnavailableException("G40")

        assertEquals("G40", failure.gap)
        assertTrue("G40" in failure.message.orEmpty())
    }

    @Test
    fun itIsAnUnsupportedOperationAndNotAFailureOfTheServer() {
        // Nothing was sent, so it is not a RemoteFailure (an Exception of its own sealed family).
        val failure: Throwable = FeatureUnavailableException("G42")

        assertIs<UnsupportedOperationException>(failure)
    }
}
