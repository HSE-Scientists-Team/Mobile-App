package org.matchlab.academic_net.common.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectTest {
    private val user = UserSummary(UserId("u1"), "Арджун Мехта")

    private fun role(id: String, member: RoleMember? = null) =
        Role(RoleId(id), "Role $id", member = member)

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
        viewerIsInitiator = true,
    )

    @Test
    fun roleWithoutMemberIsVacant() {
        assertTrue(role("r1").isVacant)
        assertFalse(role("r2", RoleMember(user)).isVacant)
    }

    @Test
    fun projectSplitsRolesIntoVacantAndOccupied() {
        val project = project(listOf(role("r1"), role("r2", RoleMember(user)), role("r3")))

        assertEquals(listOf(RoleId("r1"), RoleId("r3")), project.vacantRoles.map { it.id })
        assertEquals(1, project.occupiedRolesCount)
    }
}
