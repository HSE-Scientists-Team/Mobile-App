package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.gateway.dto.PeopleListDto
import org.matchlab.academic_net.gateway.dto.PersonDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import org.matchlab.academic_net.gateway.mapping.toCollaborator
import org.matchlab.academic_net.gateway.mapping.toCollaboratorPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A `Person` of the search and of the recommendations as a row of the app, and a `PeopleList` as a [org.matchlab.academic_net.common.paging.Page]. */
class PeopleMappingTest {
    private fun person(
        id: String = "u-1",
        name: String = "Анна Иванова",
        university: String = "МГУ",
        level: String = "магистратура",
        year: Int = 2,
        field: String = "Physics",
        verified: Boolean = false,
    ) = PersonDto(
        userId = id,
        displayName = name,
        university = university,
        studyLevel = level,
        studyYear = year,
        specialization = field,
        verified = verified,
    )

    // ---- one person ----

    @Test
    fun aPersonWithoutAnIdIsNotARow() {
        assertNull(person(id = "").toCollaborator())
        assertNull(person(id = "   ").toCollaborator())
        assertNull(person(id = "\t\n").toCollaborator())
    }

    @Test
    fun aRowHasTheIdTheNameAndWhetherThePersonIsVerified() {
        val row = person(id = "u-7", name = "  Анна Иванова ", verified = true).toCollaborator()

        assertEquals(UserSummary(UserId("u-7"), "Анна Иванова", avatarUrl = null, isVerified = true), row?.user)
    }

    @Test
    fun aPersonNobodyVerifiedIsNotMarked() {
        assertEquals(false, person(verified = false).toCollaborator()?.user?.isVerified)
    }

    @Test
    fun anEmptyOrBlankNameStaysEmptyForTheScreenToReplace() {
        assertEquals("", person(name = "").toCollaborator()?.user?.name)
        assertEquals("", person(name = "   ").toCollaborator()?.user?.name)
    }

    @Test
    fun theUniversityAndTheFieldOfStudyAreCutOfOuterSpaces() {
        val row = person(university = "  МГУ им. Ломоносова ", field = " Computer Science\t").toCollaborator()

        assertEquals("МГУ им. Ломоносова", row?.university)
        assertEquals("Computer Science", row?.field)
    }

    @Test
    fun theStudyLevelIsTheServersTextAsItIsWithItsOuterSpacesCut() {
        // API-GAP G10: a free text; no translation, no change of case.
        assertEquals(Study("магистратура", 2), person(level = " магистратура ", year = 2).toCollaborator()?.study)
        assertEquals(Study("PhD candidate", 4), person(level = "PhD candidate", year = 4).toCollaborator()?.study)
    }

    @Test
    fun aBlankLevelAndAYearOfZeroOrLessAreNotGiven() {
        assertEquals(Study(null, null), person(level = "", year = 0).toCollaborator()?.study)
        assertEquals(Study(null, null), person(level = "   ", year = -2).toCollaborator()?.study)
        assertEquals(Study("бакалавриат", null), person(level = "бакалавриат", year = 0).toCollaborator()?.study)
        assertEquals(Study(null, 1), person(level = "", year = 1).toCollaborator()?.study)
    }

    @Test
    fun aPersonAndAProfileReadTheStudyTheSameWay() {
        // One helper serves the row of the search and the top of a profile page (`studyOf`).
        val cases = listOf<Pair<String, Int>>(
            "магистратура" to 2, "  магистратура " to 2, "" to 0, "   " to -3, "PhD" to 0, "" to 5, "Бакалавриат" to 1,
        )
        for ((level, year) in cases) {
            val fromPerson = person(level = level, year = year).toCollaborator()?.study
            val fromProfile = UserProfileDto(
                userId = "u-1",
                details = UserProfileInputDto(displayName = "Анна", studyLevel = level, studyYear = year),
            ).toCollaborator().study

            assertEquals(fromProfile, fromPerson, "level=\"$level\" year=$year")
        }
        // A profile that left both out (null, not blank) reads like a person that left them out.
        assertEquals(
            PersonDto(userId = "u-1").toCollaborator()?.study,
            UserProfileDto(userId = "u-1", details = UserProfileInputDto(displayName = "Анна")).toCollaborator().study,
        )
    }

    @Test
    fun theAvatarAndTheRatingAreNotRead() {
        // API-GAP G18 (no Media yet) and G30 (no review count to tell "no reviews" from a low rating).
        val withThem = PersonDto(userId = "u-1", avatarFileId = "file-1", rating = 4.9, skills = listOf("Kotlin"))
        val without = PersonDto(userId = "u-1")

        assertEquals(without.toCollaborator(), withThem.toCollaborator())
        assertNull(withThem.toCollaborator()?.user?.avatarUrl)
    }

    // ---- a page ----

    private fun list(total: Long, vararg people: PersonDto) = PeopleListDto(people.toList(), total)

    @Test
    fun aPageHasTheReadablePeopleInTheOrderOfTheServer() {
        val page = list(3, person("z"), person("a"), person("m")).toCollaboratorPage(PageRequest(0, 20))

        assertEquals(listOf("z", "a", "m"), page.items.map { it.user.id.value })
        assertEquals(3, page.total)
        assertEquals(3, page.received)
    }

    @Test
    fun aRecordWithoutAnIdIsNotShownButMovesTheNextOffset() {
        val page = list(10, person("a"), person(id = ""), person("c")).toCollaboratorPage(PageRequest(0, 3))

        assertEquals(listOf("a", "c"), page.items.map { it.user.id.value })
        assertEquals(3, page.received)
        assertEquals(3, page.nextOffset)
        assertEquals(PageRequest(3, 3), page.next())
    }

    @Test
    fun aPageOfOnlyUnreadableRecordsStillGoesOn() {
        val page = list(40, person(id = ""), person(id = " ")).toCollaboratorPage(PageRequest(0, 2))

        assertEquals(emptyList(), page.items)
        assertEquals(2, page.received)
        assertTrue(page.hasMore)
        assertEquals(PageRequest(2, 2), page.next())
    }

    @Test
    fun theTotalIsNeverLessThanWhatWasReceivedUpToTheEndOfThePage() {
        val request = PageRequest(offset = 20, limit = 20)
        val three = arrayOf(person("a"), person("b"), person("c"))

        assertEquals(23, list(0, *three).toCollaboratorPage(request).total)
        assertEquals(23, list(2, *three).toCollaboratorPage(request).total)
        assertEquals(23, list(-5, *three).toCollaboratorPage(request).total)
        assertEquals(500, list(500, *three).toCollaboratorPage(request).total)
    }

    @Test
    fun aMissingTotalIsTakenForWhatHasBeenReceivedSoFar() {
        // total 0 (left out by the server) and a full page: nothing says that more exists, so the list ends here.
        val page = list(0, person("a"), person("b")).toCollaboratorPage(PageRequest(0, 2))

        assertEquals(2, page.total)
        assertEquals(false, page.hasMore)
    }

    @Test
    fun aTotalBeyondIntIsCutToIt() {
        assertEquals(Int.MAX_VALUE, list(Long.MAX_VALUE, person("a")).toCollaboratorPage(PageRequest.First).total)
        assertEquals(Int.MAX_VALUE, list(3_000_000_000L, person("a")).toCollaboratorPage(PageRequest.First).total)
        assertEquals(Int.MAX_VALUE, list(Int.MAX_VALUE.toLong(), person("a")).toCollaboratorPage(PageRequest.First).total)
    }

    @Test
    fun anEmptyAnswerIsAnEmptyPageThatEndsTheList() {
        val page = PeopleListDto().toCollaboratorPage(PageRequest.First)

        assertEquals(emptyList(), page.items)
        assertEquals(0, page.received)
        assertEquals(0, page.total)
        assertEquals(false, page.hasMore)
    }

    @Test
    fun thePageKeepsTheRequestItWasAskedWith() {
        val request = PageRequest(offset = 40, limit = 10)

        val page = list(100, person("a")).toCollaboratorPage(request)

        assertEquals(request, page.request)
    }

    @Test
    fun aRowIsAFullCollaborator() {
        val row: Collaborator? = person(id = "u-9", verified = true).toCollaborator()

        assertNotNull(row)
        assertEquals(
            Collaborator(
                user = UserSummary(UserId("u-9"), "Анна Иванова", isVerified = true),
                university = "МГУ",
                study = Study("магистратура", 2),
                field = "Physics",
            ),
            row,
        )
    }
}
