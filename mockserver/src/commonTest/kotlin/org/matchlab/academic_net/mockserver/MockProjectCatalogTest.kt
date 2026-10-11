package org.matchlab.academic_net.mockserver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.UserId

private fun period(startYear: Int, startMonth: Int, endYear: Int, endMonth: Int) =
    ProjectPeriod(YearMonth(startYear, startMonth), YearMonth(endYear, endMonth))

class MockProjectCatalogTest {
    private val catalog = MockProjectCatalog.all

    private val campusNavigation
        get() = catalog.first { it.project.id == ProjectId("project-found-campus-navigation") }

    @Test
    fun startsWithTheThreeCardsOfTheDesign() {
        val first = catalog.take(3)

        assertEquals(
            listOf("AI-навигация по кампусу", "Обнаружение аномалий в МРТ", "Цифровой двойник лаборатории"),
            first.map { it.project.title },
        )
        assertEquals(
            listOf("Проф. Елена Васкес", "Д-р Анна Петрова", "Иван Орлов"),
            first.map { it.project.initiator.name },
        )
        assertEquals(listOf("Stanford", "Сеченовский", "ИТМО"), first.map { it.initiatorUniversity })
        assertEquals(listOf(4, 3, 5), first.map { it.project.roles.size })
        assertEquals(
            listOf(
                period(2026, 10, 2027, 3),
                period(2026, 11, 2027, 5),
                period(2026, 9, 2027, 3),
            ),
            first.map { it.project.period },
        )
    }

    @Test
    fun hasAboutTwelveProjectsWithUniqueIds() {
        assertEquals(12, catalog.size)
        assertEquals(catalog.size, catalog.map { it.project.id }.toSet().size)
    }

    @Test
    fun everyProjectIsPublishedAndNotTheViewers() {
        assertTrue(catalog.all { it.project.status == ProjectStatus.Recruiting })
        assertTrue(catalog.none { it.project.viewerIsInitiator })
    }

    @Test
    fun coversBothTypes() {
        assertEquals(ProjectType.entries.toSet(), catalog.map { it.project.type }.toSet())
    }

    @Test
    fun hasProjectsAcrossTheNewYear() {
        assertTrue(catalog.any { it.project.period.start.year != it.project.period.end.year })
    }

    @Test
    fun sphereIdsAreTheProjectsFeaturesIds() {
        val known = setOf(
            "computer-science", "data-science", "engineering", "mathematics", "physics", "chemistry",
            "biology", "medicine", "economics", "social-sciences", "linguistics", "design",
        )

        assertTrue(catalog.all { it.project.sphere.id in known })
    }

    @Test
    fun everySphereIdHasOneName() {
        val namesById = catalog.groupBy({ it.project.sphere.id }, { it.project.sphere.name })

        assertTrue(namesById.values.all { it.toSet().size == 1 })
    }

    @Test
    fun hasSeveralSpheres() {
        assertTrue(catalog.map { it.project.sphere.id }.toSet().size >= 8)
    }

    @Test
    fun roleIdsAreUniqueInTheWholeCatalog() {
        val roleIds = catalog.flatMap { found -> found.project.roles.map { it.id } }

        assertEquals(roleIds.size, roleIds.toSet().size)
    }

    @Test
    fun twoOfFourRolesOfTheCampusProjectAreTaken() {
        val roles = campusNavigation.project.roles

        assertEquals(4, roles.size)
        assertEquals(2, campusNavigation.project.occupiedRolesCount)
        assertEquals(listOf("ML Engineer", "Data Analyst"), roles.filter { !it.isVacant }.map { it.title })
        assertEquals(listOf("Mobile Developer", "UX Researcher"), campusNavigation.project.vacantRoles.map { it.title })
    }

    @Test
    fun theCampusInitiatorIsTheChatsElenaWithAReputation() {
        val found = campusNavigation

        // The id is the one of the person in the mock chats, so that messaging her opens that chat.
        assertEquals(UserId("user-elena"), found.project.initiator.id)
        assertEquals(4.9, found.initiatorRating)
        assertEquals(12, found.initiatorCompletedProjects)
    }

    @Test
    fun initiatorsWithoutReviewsHaveNoRating() {
        val unrated = catalog.filter { it.initiatorRating == null }

        assertTrue(unrated.isNotEmpty())
        assertTrue(unrated.all { it.initiatorCompletedProjects == 0 })
        assertTrue(catalog.any { it.initiatorRating != null })
    }

    @Test
    fun noInitiatorIsAlsoAMemberOfHisOwnProject() {
        assertTrue(
            catalog.all { found ->
                found.project.roles.none { it.member?.user?.id == found.project.initiator.id }
            },
        )
    }
}
