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
        assertEquals("scientific", ProjectType.Scientific.code)
        assertEquals("practical", ProjectType.Practical.code)
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
        assertEquals(ProjectType.Scientific, ProjectType.fromCode(" scientific "))
        assertEquals(ProjectType.Practical, ProjectType.fromCode("\tpractical\n"))
    }

    @Test
    fun anythingButAnExactProjectTypeCodeIsUnknown() {
        for (raw in listOf(null, "", "   ", "0", "1", "2", "01", "1.0", "scientific practical", "Scientific", "PRACTICAL", "Научный")) {
            assertNull(ProjectType.fromCode(raw), "type of '$raw'")
        }
    }

    // ---- ProjectStatus ----

    @Test
    fun projectStatusCodesAreTheOnesOfTheContract() {
        assertEquals("recruiting", ProjectStatus.Recruiting.code)
        assertEquals("unpublished", ProjectStatus.Unpublished.code)
        assertEquals("active", ProjectStatus.Started.code)
        assertEquals("completed", ProjectStatus.Finished.code)
        assertEquals("cancelled", ProjectStatus.Cancelled.code)
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
        assertEquals(ProjectStatus.Recruiting, ProjectStatus.fromCode(" recruiting"))
        assertEquals(ProjectStatus.Unpublished, ProjectStatus.fromCode("unpublished "))
        assertEquals(ProjectStatus.Finished, ProjectStatus.fromCode(" completed "))
    }

    @Test
    fun lifecycleStatusesAreReadFromTheirCodes() {
        assertEquals(ProjectStatus.Started, ProjectStatus.fromCode("active"))
        assertEquals(ProjectStatus.Finished, ProjectStatus.fromCode("completed"))
        assertEquals(ProjectStatus.Cancelled, ProjectStatus.fromCode("cancelled"))
    }

    @Test
    fun anythingButAnExactProjectStatusCodeIsUnknown() {
        for (raw in listOf(null, "", " ", "0", "1", "5", "01", "1.0", "published", "draft", "started", "finished", "Recruiting", "CANCELLED", "canceled")) {
            assertNull(ProjectStatus.fromCode(raw), "status of '$raw'")
        }
    }

    // ---- ProjectJoinType / ProjectJoinStatus ----

    @Test
    fun joinTypeCodesAreTheOnesOfTheContractAndReadBack() {
        assertEquals("application", ProjectJoinType.Application.code)
        assertEquals("invitation", ProjectJoinType.Invitation.code)
        for (type in ProjectJoinType.entries) assertEquals(type, ProjectJoinType.fromCode(type.code))
        assertEquals(ProjectJoinType.Invitation, ProjectJoinType.fromCode(" invitation "))
    }

    @Test
    fun anythingButAnExactJoinTypeCodeIsUnknown() {
        for (raw in listOf(null, "", " ", "0", "1", "2", "01", "Application", "INVITATION", "apply")) {
            assertNull(ProjectJoinType.fromCode(raw), "join type of '$raw'")
        }
    }

    @Test
    fun joinStatusCodesAreTheOnesOfTheContractAndReadBack() {
        assertEquals(
            listOf("pending", "accepted", "rejected", "cancelled"),
            listOf(
                ProjectJoinStatus.Pending,
                ProjectJoinStatus.Accepted,
                ProjectJoinStatus.Rejected,
                ProjectJoinStatus.Cancelled,
            ).map { it.code },
        )
        assertEquals(4, ProjectJoinStatus.entries.size)
        for (status in ProjectJoinStatus.entries) assertEquals(status, ProjectJoinStatus.fromCode(status.code))
        assertEquals(ProjectJoinStatus.Rejected, ProjectJoinStatus.fromCode("\trejected\n"))
    }

    @Test
    fun anythingButAnExactJoinStatusCodeIsUnknown() {
        for (raw in listOf(null, "", " ", "0", "1", "4", "02", "Pending", "ACCEPTED", "declined", "withdrawn")) {
            assertNull(ProjectJoinStatus.fromCode(raw), "join status of '$raw'")
        }
    }

    // ---- Spheres ----

    @Test
    fun thereAreTwelveSpheresWhoseIdIsTheirName() {
        assertEquals(12, Spheres.all.size)
        assertEquals(Spheres.all.map { it.name }, Spheres.all.map { it.id })
        assertEquals(
            listOf(
                "Computer Science", "Data Science", "Инженерия", "Математика", "Физика", "Химия",
                "Биология", "Медицина", "Экономика", "Социология", "Лингвистика", "Дизайн",
            ),
            Spheres.all.map { it.id },
        )
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
        assertEquals(Spheres.all[2], Spheres.fromCode(" Инженерия "))
        assertEquals(Spheres.all[11], Spheres.fromCode("\tДизайн\n"))
    }

    @Test
    fun anythingButAnExactSphereCodeIsUnknown() {
        for (raw in listOf(null, "", "  ", "0", "1", "12", "03", "1.0", "биология", "COMPUTER SCIENCE", "Computer  Science", "Биология и химия")) {
            assertNull(Spheres.fromCode(raw), "sphere of '$raw'")
        }
    }
}
