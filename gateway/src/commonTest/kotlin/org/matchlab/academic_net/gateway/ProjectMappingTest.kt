package org.matchlab.academic_net.gateway

import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Spheres
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectInputDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import org.matchlab.academic_net.gateway.mapping.toProject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A `Project` of the server as a project of the app: what is read, what is "unknown" and what is cleaned. */
class ProjectMappingTest {
    private val viewer = UserId("viewer-1")

    private fun dto(
        id: String = "p-1",
        owner: String = "owner-1",
        details: ProjectInputDto = ProjectInputDto(title = "AI-навигация", field = "1"),
        status: String = "1",
        roles: List<ProjectRoleDto> = emptyList(),
    ) = ProjectDto(id = id, ownerUserId = owner, details = details, status = status, revision = 3, roles = roles)

    private fun profile(id: String = "owner-1", name: String = "Елена Васкес", verified: Boolean = true) =
        UserProfileDto(userId = id, details = UserProfileInputDto(displayName = name), verified = verified)

    private fun project(dto: ProjectDto = dto(), owner: UserProfileDto? = profile()): Project =
        assertNotNull(dto.toProject(viewer, owner))

    private fun role(
        id: String = "r-1",
        title: String = "ML Engineer",
        skills: List<String> = emptyList(),
        slots: Int = 1,
        occupied: Int = 0,
    ) = ProjectRoleDto(id, title, skills, slots, occupied)

    // ---- what cannot be shown ----

    @Test
    fun aProjectWithoutAnIdIsNotShown() {
        assertNull(dto(id = "").toProject(viewer, profile()))
        assertNull(dto(id = "   ").toProject(viewer, profile()))
    }

    @Test
    fun aProjectWithoutAnOwnerIsNotShown() {
        assertNull(dto(owner = "").toProject(viewer, null))
        assertNull(dto(owner = " \t").toProject(viewer, profile()))
    }

    // ---- the fields that are read ----

    @Test
    fun theTextsAreCutOfOuterSpaces() {
        val project = project(
            dto(
                id = "p-9",
                details = ProjectInputDto(
                    title = "  AI-навигация ",
                    field = " 1\t",
                    problem = "\nGPS не работает ",
                    goal = " Маршруты ",
                ),
            ),
        )

        assertEquals(ProjectId("p-9"), project.id)
        assertEquals("AI-навигация", project.title)
        assertEquals("GPS не работает", project.problem)
        assertEquals("Маршруты", project.goal)
        assertEquals(Spheres.all[0], project.sphere)
    }

    @Test
    fun theSphereIsTheOneWhoseCodeTheFieldHolds() {
        for (sphere in Spheres.all) {
            assertEquals(sphere, project(dto(details = ProjectInputDto(field = sphere.id))).sphere)
        }
    }

    @Test
    fun aFieldThatIsNoCodeOfASphereIsUnknownAndTheProjectStays() {
        for (field in listOf("", "  ", "0", "13", "03", "Биология", "Computer Science")) {
            val project = project(dto(details = ProjectInputDto(title = "T", field = field)))

            assertNull(project.sphere, "field '$field'")
            assertEquals("T", project.title)
        }
    }

    @Test
    fun theTagsAreCleanedOfBlanksAndKeepTheirOrder() {
        val project = project(dto(details = ProjectInputDto(tags = listOf(" ML ", "", "  ", "Robotics", "ML"))))

        assertEquals(listOf("ML", "Robotics", "ML"), project.tags)
    }

    @Test
    fun theTypeTheStatusAndThePeriodAreReadByTheirCodes() {
        val project = project(
            dto(
                details = ProjectInputDto(title = "T", type = "1", plannedStart = "2026-10-01", plannedEnd = "2027-03-31"),
                status = "1",
            ),
        )

        assertEquals(ProjectType.Scientific, project.type)
        assertEquals(ProjectStatus.Recruiting, project.status)
        assertEquals(ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)), project.period)
    }

    @Test
    fun theOtherCodesAreReadToo() {
        val project = project(dto(details = ProjectInputDto(type = " 2 "), status = "2 "))

        assertEquals(ProjectType.Practical, project.type)
        assertEquals(ProjectStatus.Unpublished, project.status)
    }

    @Test
    fun valuesTheAppHasNoCodeForAreUnknownAndDoNotHideTheProject() {
        val project = project(
            dto(
                id = "p-7",
                details = ProjectInputDto(
                    title = "T",
                    type = "scientific",
                    field = "99",
                    plannedStart = "soon",
                    plannedEnd = "later",
                ),
                status = "published",
            ),
        )

        assertEquals(ProjectId("p-7"), project.id)
        assertEquals("T", project.title)
        assertNull(project.type)
        assertNull(project.status)
        assertNull(project.sphere)
        assertNull(project.period)
    }

    @Test
    fun nothingSentIsUnknown() {
        val project = project(dto(details = ProjectInputDto(title = "T"), status = ""))

        assertNull(project.type)
        assertNull(project.status)
        assertNull(project.sphere)
        assertNull(project.period)
    }

    @Test
    fun codesWithAZeroOrADecimalPointAreUnknown() {
        val project = project(dto(details = ProjectInputDto(type = "01"), status = "1.0"))

        assertNull(project.type)
        assertNull(project.status)
    }

    @Test
    fun anEndBeforeTheStartLeavesThePeriodUnknownAndTheProjectShown() {
        val project = project(dto(details = ProjectInputDto(title = "T", plannedStart = "2027-03-01", plannedEnd = "2026-10-31")))

        assertNull(project.period)
        assertEquals("T", project.title)
    }

    @Test
    fun oneMissingDateLeavesThePeriodUnknown() {
        assertNull(project(dto(details = ProjectInputDto(plannedStart = "2026-10-01", plannedEnd = ""))).period)
        assertNull(project(dto(details = ProjectInputDto(plannedStart = "", plannedEnd = "2026-10-31"))).period)
    }

    // ---- the initiator ----

    @Test
    fun theInitiatorIsTheOwnersProfileWithTheIdOfTheProject() {
        val project = project(owner = profile(id = "owner-1", name = "  Елена Васкес ", verified = true))

        assertEquals(UserSummary(UserId("owner-1"), "Елена Васкес", avatarUrl = null, isVerified = true), project.initiator)
    }

    @Test
    fun theIdOfTheInitiatorIsTheOneOfTheProjectEvenIfTheProfileNamesAnother() {
        val project = project(dto(owner = "owner-1"), owner = profile(id = "someone-else"))

        assertEquals(UserId("owner-1"), project.initiator.id)
    }

    @Test
    fun anOwnerWithoutAProfileIsNamedByTheirIdWithAnEmptyName() {
        val project = project(dto(owner = "owner-2"), owner = null)

        assertEquals(UserSummary(UserId("owner-2"), name = ""), project.initiator)
        assertFalse(project.initiator.isVerified)
    }

    @Test
    fun anOwnerWhoseNameIsBlankStaysEmptyForTheScreenToReplace() {
        assertEquals("", project(owner = profile(name = "   ")).initiator.name)
    }

    @Test
    fun theViewerIsTheInitiatorOnlyOfTheirOwnProject() {
        assertTrue(project(dto(owner = "viewer-1"), owner = null).viewerIsInitiator)
        assertFalse(project(dto(owner = "owner-1")).viewerIsInitiator)
        // The ids are compared as they are written: a different case is another person.
        assertFalse(project(dto(owner = "VIEWER-1")).viewerIsInitiator)
    }

    // ---- the roles ----

    @Test
    fun theRolesComeFromTheTopLevelListInTheirOrder() {
        val project = project(
            dto(roles = listOf(role("r-2", "Дизайнер", listOf(" Figma ", "")), role("r-1", " ML Engineer ", listOf("Python")))),
        )

        assertEquals(
            listOf(
                Role(RoleId("r-2"), "Дизайнер", skills = listOf("Figma"), slots = 1, members = emptyList(), occupied = 0),
                Role(RoleId("r-1"), "ML Engineer", skills = listOf("Python"), slots = 1, members = emptyList(), occupied = 0),
            ),
            project.roles,
        )
    }

    @Test
    fun aRoleWithoutAnIdIsDropped() {
        val project = project(dto(roles = listOf(role(id = ""), role(id = "r-1"), role(id = "  "))))

        assertEquals(listOf(RoleId("r-1")), project.roles.map { it.id })
    }

    @Test
    fun aRoleHasAtLeastOnePlaceWhateverTheServerSays() {
        // API-GAP G49: the server leaves a zero out, so a missing `slots` arrives as 0.
        val project = project(dto(roles = listOf(role("a", slots = 0), role("b", slots = -3), role("c", slots = 4))))

        assertEquals(listOf(1, 1, 4), project.roles.map { it.slots })
    }

    @Test
    fun takenPlacesAreNeverNegativeAndMayExceedThePlaces() {
        val project = project(dto(roles = listOf(role("a", occupied = -2), role("b", slots = 2, occupied = 5))))

        assertEquals(listOf(0, 5), project.roles.map { it.occupied })
        assertTrue(project.roles[0].isVacant)
        assertFalse(project.roles[1].isVacant)
        assertEquals(0, project.roles[1].vacantPlaces)
    }

    @Test
    fun theMembersAreNotRead() {
        // API-GAP G52: `ProjectMember.status` has no list of values; the places are counted by `occupied`.
        val project = project(dto(roles = listOf(role(slots = 3, occupied = 2))))

        assertEquals(emptyList(), project.roles.single().members)
        assertEquals(2, project.roles.single().occupied)
    }

    @Test
    fun aProjectWithoutRolesHasNoRoles() {
        assertEquals(emptyList(), project().roles)
    }

    // ---- a whole project ----

    @Test
    fun aWholeProjectAsTheServerWritesItIsReadWithEveryRule() {
        val dto = org.matchlab.academic_net.gateway.internal.GatewayJson
            .decodeFromString(ProjectDto.serializer(), ProjectSpec.FullProject)

        val project = assertNotNull(dto.toProject(viewer, profile(id = ProjectSpec.OwnerId)))

        assertEquals("AI-навигация", project.title)
        assertEquals(listOf("ML", "Robotics"), project.tags)
        assertEquals(1, project.roles.size)
        assertEquals(2, project.roles.single().slots)
        assertEquals(1, project.roles.single().occupied)
        assertEquals(listOf("Python", "PyTorch"), project.roles.single().skills)
        assertEquals(2, project.totalPlaces)
        assertEquals(1, project.occupiedPlaces)
        assertFalse(project.viewerIsInitiator)
        assertEquals(ProjectType.Scientific, project.type)
        assertEquals(ProjectStatus.Recruiting, project.status)
        assertEquals(Spheres.all[0], project.sphere)
        assertEquals(ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)), project.period)
    }
}
