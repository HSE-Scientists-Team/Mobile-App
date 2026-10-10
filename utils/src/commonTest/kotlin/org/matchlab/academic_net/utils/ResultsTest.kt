package org.matchlab.academic_net.utils

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class ResultsTest {
    @Test
    fun returnsValueAsSuccess() = runTest {
        val result = suspendRunCatching { 42 }

        assertEquals(42, result.getOrNull())
    }

    @Test
    fun wrapsExceptionAsFailure() = runTest {
        val result = suspendRunCatching<Int> { throw IllegalStateException("boom") }

        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun rethrowsCancellation() = runTest {
        assertFailsWith<CancellationException> {
            suspendRunCatching<Int> { throw CancellationException("cancelled") }
        }
    }
}
