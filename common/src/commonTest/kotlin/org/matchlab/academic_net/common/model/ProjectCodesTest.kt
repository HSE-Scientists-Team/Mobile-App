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
        assertEquals("3", ProjectStatus.Started.code)
        assertEquals("4", ProjectStatus.Finished.code)
        assertEquals("5", ProjectStatus.Cancelled.code)
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
        assertEquals(ProjectStatus.Finished, ProjectStatus.fromCode(" 4 "))
    }

    @Test
    fun lifecycleStatusesAreReadFromTheirCodes() {
        assertEquals(ProjectStatus.Started, ProjectStatus.fromCode("3"))
        assertEquals(ProjectStatus.Finished, ProjectStatus.fromCode("4"))
        assertEquals(ProjectStatus.Cancelled, ProjectStatus.fromCode("5"))
    }

    @Test
    fun anythingButAnExactProjectStatusCodeIsUnknown() {
        for (raw in listOf(null, "", " ", "0", "6", "01", "1.0", "published", "draft", "Recruiting")) {
            assertNull(ProjectStatus.fromCode(raw), "status of '$raw'")
        }
    }

    // ---- ProjectJoinType / ProjectJoinStatus ----

    @Test
    fun joinTypeCodesAreTheOnesOfTheContractAndReadBack() {
        assertEquals("1", ProjectJoinType.Application.code)
        assertEquals("2", ProjectJoinType.Invitation.code)
        for (type in ProjectJoinType.entries) assertEquals(type, ProjectJoinType.fromCode(type.code))
        assertEquals(ProjectJoinType.Invitation, ProjectJoinType.fromCode(" 2 "))
    }

    @Test
    fun anythingButAnExactJoinTypeCodeIsUnknown() {
        for (raw in listOf(null, "", " ", "0", "3", "01", "application", "Application")) {
            assertNull(ProjectJoinType.fromCode(raw), "join type of '$raw'")
        }
    }

    @Test
    fun joinStatusCodesAreTheOnesOfTheContractAndReadBack() {
        assertEquals(
            listOf("1", "2", "3", "4"),
            listOf(
                ProjectJoinStatus.Pending,
                ProjectJoinStatus.Accepted,
                ProjectJoinStatus.Declined,
                ProjectJoinStatus.Withdrawn,
            ).map { it.code },
        )
        assertEquals(4, ProjectJoinStatus.entries.size)
        for (status in ProjectJoinStatus.entries) assertEquals(status, ProjectJoinStatus.fromCode(status.code))
        assertEquals(ProjectJoinStatus.Declined, ProjectJoinStatus.fromCode("\t3\n"))
    }

    @Test
    fun anythingButAnExactJoinStatusCodeIsUnknown() {
        for (raw in listOf(null, "", " ", "0", "5", "02", "pending", "Pending")) {
            assertNull(ProjectJoinStatus.fromCode(raw), "join status of '$raw'")
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
