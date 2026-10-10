package org.matchlab.academic_net.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdGeneratorTest {
    @Test
    fun uuidGeneratorProducesDistinctNonBlankIds() {
        val ids = List(100) { UuidIdGenerator.newId() }

        assertTrue(ids.all { it.isNotBlank() })
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun uuidGeneratorDoesNotRepeatBetweenCalls() {
        assertNotEquals(UuidIdGenerator.newId(), UuidIdGenerator.newId())
    }
}
