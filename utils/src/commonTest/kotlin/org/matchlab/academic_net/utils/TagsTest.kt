package org.matchlab.academic_net.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class TagsTest {
    @Test
    fun normalizeTagTrimsAndCollapsesWhitespace() {
        assertEquals("Machine Learning", "  Machine   \t Learning ".normalizeTag())
    }

    @Test
    fun normalizeTagOfBlankIsEmpty() {
        assertEquals("", "   ".normalizeTag())
    }

    @Test
    fun normalizeTagsDropsBlanksAndCaseInsensitiveDuplicatesKeepingFirstSpelling() {
        val result = listOf("Python", " python ", "", "IoT", "Python", "iot").normalizeTags()

        assertEquals(listOf("Python", "IoT"), result)
    }

    @Test
    fun withTagAppendsNewTag() {
        assertEquals(listOf("Python", "IoT"), listOf("Python").withTag(" IoT "))
    }

    @Test
    fun withTagIgnoresDuplicateAndBlank() {
        val tags = listOf("Python")

        assertEquals(tags, tags.withTag("PYTHON"))
        assertEquals(tags, tags.withTag("  "))
    }

    @Test
    fun withoutTagRemovesIgnoringCase() {
        assertEquals(listOf("IoT"), listOf("Python", "IoT").withoutTag("python"))
    }
}
