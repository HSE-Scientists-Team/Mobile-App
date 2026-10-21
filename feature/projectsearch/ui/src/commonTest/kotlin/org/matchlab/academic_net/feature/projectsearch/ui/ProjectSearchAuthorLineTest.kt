package org.matchlab.academic_net.feature.projectsearch.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectSearchAuthorLineTest {
    @Test
    fun theUniversityFollowsTheNameAfterADot() {
        assertEquals("Проф. Елена Васкес · Stanford", authorLine("Проф. Елена Васкес", "Stanford"))
    }

    @Test
    fun withoutAUniversityThereIsOnlyTheName() {
        assertEquals("Проф. Елена Васкес", authorLine("Проф. Елена Васкес", ""))
        assertEquals("Проф. Елена Васкес", authorLine("Проф. Елена Васкес", "   "))
    }
}
