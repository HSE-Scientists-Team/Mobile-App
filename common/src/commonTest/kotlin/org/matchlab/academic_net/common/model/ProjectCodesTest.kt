package org.matchlab.academic_net.common.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The tables of codes of `docs/api-values.md`: what is written, and what is "unknown" when read. */
class ProjectCodesTest {
    // ---- ProjectType ----

    @Test
    fun projectTypeCodesAreTheOnesOfTheContract() {
        assertEquals("1", ProjectType.Scientific.code)
        assertEquals("2", ProjectType.Practical.code)
    }

    @Test
    fun projectTypeCodesAreUnique() {
        assertEquals(ProjectType.entries.size, ProjectType.entries.map { it.code }.toSet().size)
    }

    @Test
    fun everyProjectTypeIsReadBackFromItsOwnCode() {
        for (type in ProjectType.entries) assertEquals(type, ProjectType.fromCode(type.code))
    }

    @Test
    fun projectTypeIsReadAfterTheOuterSpacesAreCut() {
        assertEquals(ProjectType.Scientific, ProjectType.fromCode(" 1 "))
        assertEquals(ProjectType.Practical, ProjectType.fromCode("\t2\n"))
    }

    @Test
    fun anythingButAnExactProjectTypeCodeIsUnknown() {
        for (raw in listOf(null, "", "   ", "0", "3", "01", "1.0", "1 2", "-1", "scientific", "Scientific", "Научный")) {
            assertNull(ProjectType.fromCode(raw), "type of '$raw'")
        }
    }

    // ---- ProjectStatus ----

    @Test
    fun projectStatusCodesAreTheOnesOfTheContract() {
        assertEquals("1", ProjectStatus.Recruiting.code)
        assertEquals("2", ProjectStatus.Unpublished.code)
    }

    @Test
    fun projectStatusCodesAreUnique() {
        assertEquals(ProjectStatus.entries.size, ProjectStatus.entries.map { it.code }.toSet().size)
    }

    @Test
    fun everyProjectStatusIsReadBackFromItsOwnCode() {
        for (status in ProjectStatus.entries) assertEquals(status, ProjectStatus.fromCode(status.code))
    }

    @Test
    fun projectStatusIsReadAfterTheOuterSpacesAreCut() {
        assertEquals(ProjectStatus.Recruiting, ProjectStatus.fromCode(" 1"))
        assertEquals(ProjectStatus.Unpublished, ProjectStatus.fromCode("2 "))
    }

    @Test
    fun anythingButAnExactProjectStatusCodeIsUnknown() {
        for (raw in listOf(null, "", " ", "0", "3", "5", "01", "1.0", "published", "draft", "Recruiting")) {
            assertNull(ProjectStatus.fromCode(raw), "status of '$raw'")
        }
    }

    // ---- Spheres ----

    @Test
    fun thereAreTwelveSpheresWithCodesOneToTwelve() {
        assertEquals((1..12).map { it.toString() }, Spheres.all.map { it.id })
    }

    @Test
    fun noSphereIdOrNameRepeatsAndNoneIsBlank() {
        assertEquals(12, Spheres.all.map { it.id }.toSet().size)
        assertEquals(12, Spheres.all.map { it.name }.toSet().size)
        assertTrue(Spheres.all.none { it.name.isBlank() })
    }

    @Test
    fun everySphereIsFoundByItsCode() {
        for (sphere in Spheres.all) assertEquals(sphere, Spheres.fromCode(sphere.id))
    }

    @Test
    fun aSphereIsFoundAfterTheOuterSpacesAreCut() {
        assertEquals(Spheres.all[2], Spheres.fromCode(" 3 "))
        assertEquals(Spheres.all[11], Spheres.fromCode("\t12\n"))
    }

    @Test
    fun anythingButAnExactSphereCodeIsUnknown() {
        for (raw in listOf(null, "", "  ", "0", "13", "-1", "03", "1.0", "1 2", "100", "Биология", "Computer Science")) {
            assertNull(Spheres.fromCode(raw), "sphere of '$raw'")
        }
    }
}
