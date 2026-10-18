package org.matchlab.academic_net.common.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.YearMonth

class ProjectTest {
    private val user = UserSummary(UserId("u1"), "Арджун Мехта")

    private fun role(id: String, slots: Int = 1, occupied: Int = 0, members: List<RoleMember> = emptyList()) =
        Role(RoleId(id), "Role $id", slots = slots, members = members, occupied = occupied)

    private fun project(roles: List<Role>) = Project(
        id = ProjectId("p1"),
        title = "AI-навигация по кампусу",
        type = ProjectType.Scientific,
        sphere = Sphere("cs", "Computer Science"),
        problem = "Problem",
        goal = "Goal",
        tags = emptyList(),
        initiator = user,
        roles = roles,
        status = ProjectStatus.Recruiting,
        period = ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)),
        viewerIsInitiator = true,
    )

    // ---- Role ----

    @Test
    fun aRoleWithNoOneOnItIsVacant() {
        val role = role("r1")

        assertTrue(role.isVacant)
        assertEquals(1, role.vacantPlaces)
    }

    @Test
    fun aFullRoleIsNotVacant() {
        val role = role("r1", slots = 1, occupied = 1)

        assertFalse(role.isVacant)
        assertEquals(0, role.vacantPlaces)
    }

    @Test
    fun aRoleOfSeveralPlacesStaysVacantUntilTheLastOneIsTaken() {
        assertTrue(role("r1", slots = 3, occupied = 2).isVacant)
        assertEquals(1, role("r1", slots = 3, occupied = 2).vacantPlaces)
        assertEquals(3, role("r1", slots = 3, occupied = 0).vacantPlaces)
        assertFalse(role("r1", slots = 3, occupied = 3).isVacant)
        assertEquals(0, role("r1", slots = 3, occupied = 3).vacantPlaces)
    }

    @Test
    fun moreTakenPlacesThanThereAreIsNotVacantAndNeverNegative() {
        // The server may count more people than the role has places: no free place, and not a negative number of them.
        val role = role("r1", slots = 2, occupied = 5)

        assertFalse(role.isVacant)
        assertEquals(0, role.vacantPlaces)
    }

    @Test
    fun takenPlacesDefaultToTheKnownMembers() {
        val member = RoleMember(user)

        assertEquals(1, Role(RoleId("r1"), "Role", slots = 2, members = listOf(member)).occupied)
        assertEquals(0, Role(RoleId("r1"), "Role").occupied)
    }

    @Test
    fun takenPlacesMayBeMoreThanTheMembersTheAppKnows() {
        // API-GAP G52: the members of the server are not read, yet the places they take are counted.
        val role = Role(RoleId("r1"), "Role", slots = 3, members = emptyList(), occupied = 2)

        assertEquals(2, role.occupied)
        assertEquals(1, role.vacantPlaces)
    }

    @Test
    fun aRoleHasAtLeastOnePlace() {
        assertFailsWith<IllegalArgumentException> { role("r1", slots = 0) }
        assertFailsWith<IllegalArgumentException> { role("r1", slots = -1) }
        assertEquals(1, role("r1", slots = 1).slots)
    }

    @Test
    fun takenPlacesAreNeverNegative() {
        assertFailsWith<IllegalArgumentException> { role("r1", occupied = -1) }
        assertEquals(0, role("r1", occupied = 0).occupied)
    }

    // ---- Project ----

    @Test
    fun projectSplitsRolesIntoVacantAndFull() {
        val project = project(
            listOf(role("r1"), role("r2", occupied = 1), role("r3"), role("r4", slots = 2, occupied = 1)),
        )

        assertEquals(listOf(RoleId("r1"), RoleId("r3"), RoleId("r4")), project.vacantRoles.map { it.id })
    }

    @Test
    fun aProjectWithoutRolesHasNoPlaces() {
        val project = project(emptyList())

        assertEquals(emptyList(), project.vacantRoles)
        assertEquals(0, project.occupiedPlaces)
        assertEquals(0, project.totalPlaces)
    }

    @Test
    fun placesAreCountedOverAllRolesBySlots() {
        val project = project(
            listOf(
                role("r1", slots = 1, occupied = 1),
                role("r2", slots = 3, occupied = 1),
                role("r3", slots = 2, occupied = 0),
            ),
        )

        assertEquals(2, project.occupiedPlaces)
        assertEquals(6, project.totalPlaces)
    }

    @Test
    fun aRoleIsCountedUpToItsSlotsWhenTheServerCountsMore() {
        // "2 of 4 taken" must never read "7 of 4": the count of a role is cut at its places.
        val project = project(listOf(role("r1", slots = 1, occupied = 4), role("r2", slots = 3, occupied = 1)))

        assertEquals(2, project.occupiedPlaces)
        assertEquals(4, project.totalPlaces)
    }
}
