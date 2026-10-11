package org.matchlab.academic_net.mockserver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.UserId

private fun person(key: String) = MockServerPerson(
    id = UserId("user-$key"),
    name = "Person $key",
    university = "University",
    studyInfo = "study",
    field = "field",
)

private fun document(
    owner: String,
    state: MockCheckState,
    education: Boolean = true,
) = MockServerDocument("doc-$owner-$state-$education", UserId("user-$owner"), education, "Title", "Issuer", 2025, state)

private fun review(subject: String, rating: Int, finished: YearMonth, id: String = "r-$subject-$rating-$finished") =
    MockServerReview(
        id = id,
        authorId = UserId("user-author"),
        subjectId = UserId("user-$subject"),
        authorIsInitiator = true,
        projectTitle = "Project",
        projectFinished = finished,
        title = "Title",
        rating = rating,
        text = "Text",
    )

class MockPeopleTest {
    @Test
    fun personIsFoundByIdAndUnknownIdIsNull() {
        val people = MockPeople(listOf(person("a"), person("b")))

        assertEquals("Person b", people.person(UserId("user-b"))?.name)
        assertNull(people.person(UserId("user-c")))
        assertNull(people.summary(UserId("user-c")))
    }

    @Test
    fun personIsVerifiedByAConfirmedEducationDocument() {
        val people = MockPeople(
            persons = listOf(person("verified"), person("pending"), person("rejected"), person("certificate"), person("none")),
            documents = listOf(
                document("verified", MockCheckState.Verified),
                document("pending", MockCheckState.Pending),
                document("rejected", MockCheckState.Rejected),
                document("certificate", MockCheckState.Verified, education = false),
            ),
        )

        val verified = people.persons.filter { people.isVerified(it.id) }.map { it.id.value }

        assertEquals(listOf("user-verified"), verified)
        assertEquals(true, people.summary(UserId("user-verified"))?.isVerified)
        assertEquals(false, people.summary(UserId("user-pending"))?.isVerified)
    }

    @Test
    fun summaryCarriesTheIdAndTheName() {
        val summary = MockPeople(listOf(person("a"))).summary(UserId("user-a"))

        assertEquals(UserId("user-a"), summary?.id)
        assertEquals("Person a", summary?.name)
    }

    @Test
    fun ratingIsTheMeanOfTheReviewsAndNullWithoutThem() {
        val people = MockPeople(
            persons = listOf(person("a"), person("b")),
            reviews = listOf(
                review("a", 5, YearMonth(2026, 1)),
                review("a", 4, YearMonth(2026, 2)),
            ),
        )

        assertEquals(4.5, people.rating(UserId("user-a")))
        assertNull(people.rating(UserId("user-b")))
    }

    @Test
    fun reviewsAreNewestProjectFirstAndKeepTheTableOrderWithinAMonth() {
        val first = review("a", 5, YearMonth(2026, 3), id = "first")
        val second = review("a", 5, YearMonth(2026, 3), id = "second")
        val older = review("a", 5, YearMonth(2025, 12), id = "older")
        val newer = review("a", 5, YearMonth(2026, 4), id = "newer")
        val people = MockPeople(listOf(person("a")), reviews = listOf(older, first, second, newer))

        assertEquals(listOf("newer", "first", "second", "older"), people.reviewsAbout(UserId("user-a")).map { it.id })
    }

    @Test
    fun worksAndDocumentsAreFilteredByOwnerKeepingTheOrder() {
        val works = listOf("a", "b", "a").mapIndexed { index, owner ->
            MockServerWork("w$index", UserId("user-$owner"), ProjectType.Practical, "T", "D", state = MockCheckState.Unconfirmed)
        }
        val people = MockPeople(listOf(person("a"), person("b")), works = works)

        assertEquals(listOf("w0", "w2"), people.worksOf(UserId("user-a")).map { it.id })
        assertTrue(people.documentsOf(UserId("user-a")).isEmpty())
    }
}

/** The seeded tables. */
class MockPeopleDefaultTest {
    private val people = MockPeople.default

    /**
     * The people of the other mock data sources (the people search, the feed, the chats, the
     * projects and the project page's members) with the names and the verification status they
     * have there: all of them must exist here with the same id, name and status.
     */
    private val peopleOfOtherMocks = mapOf(
        "user-arjun" to ("Арджун Мехта" to true),
        "user-maria" to ("Мария Соколова" to true),
        "user-daniil" to ("Даниил Крюков" to true),
        "user-lina" to ("Лина Ковач" to true),
        "user-ilyas" to ("Ильяс Каримов" to false),
        "user-anna" to ("Анна Петрова" to true),
        "user-sergey" to ("Сергей Волков" to false),
        "user-olga" to ("Ольга Никитина" to true),
        "user-timur" to ("Тимур Алиев" to true),
        "user-eva" to ("Ева Миронова" to false),
        "user-elena" to ("Проф. Елена Васкес" to true),
    )

    @Test
    fun hasEveryPersonOfTheOtherMocksUnderTheSameIdNameAndStatus() {
        for ((id, expected) in peopleOfOtherMocks) {
            val summary = people.summary(UserId(id))

            assertNotNull(summary, "no person $id")
            assertEquals(expected.first, summary.name, id)
            assertEquals(expected.second, summary.isVerified, id)
        }
    }

    @Test
    fun hasTheCollaboratorsUniversityStudyInfoAndField() {
        val arjun = people.person(UserId("user-arjun"))
        val maria = people.person(UserId("user-maria"))

        assertEquals(Triple("Stanford", "3 курс бакалавриата", "Computer Science"), arjun?.let { Triple(it.university, it.studyInfo, it.field) })
        assertEquals(Triple("МГУ", "аспирантка", "биоинформатика"), maria?.let { Triple(it.university, it.studyInfo, it.field) })
    }

    @Test
    fun hasTheViewerAndEveryPersonOfTheCatalogOfProjects() {
        assertNotNull(people.person(UserId("user-me")))
        for (found in MockProjectCatalog.all) {
            assertNotNull(people.person(found.project.initiator.id), "initiator ${found.project.initiator.id}")
            for (role in found.project.roles) {
                role.member?.let { assertNotNull(people.person(it.user.id), "member ${it.user.id}") }
            }
        }
    }

    @Test
    fun catalogTakesTheInitiatorsFactsFromThePeopleTables() {
        for (found in MockProjectCatalog.all) {
            val id = found.project.initiator.id

            assertEquals(people.person(id)?.university, found.initiatorUniversity)
            assertEquals(people.rating(id), found.initiatorRating)
            assertEquals(people.person(id)?.completedProjects, found.initiatorCompletedProjects)
            assertEquals(people.isVerified(id), found.project.initiator.isVerified)
            assertEquals(people.person(id)?.name, found.project.initiator.name)
        }
    }

    @Test
    fun theViewerIsAlexeyWithTheContentOfTheDesign() {
        val me = people.person(UserId("user-me"))

        assertEquals("Алексей Морозов", me?.name)
        assertEquals("Stanford", me?.university)
        assertEquals(true, people.isVerified(UserId("user-me")))
        assertEquals(
            listOf("B.S. Computer Science", "Сертификат Deep Learning"),
            people.documentsOf(UserId("user-me")).map { it.title },
        )
        assertEquals(MockCheckState.Pending, people.documentsOf(UserId("user-me")).last().state)
        assertEquals(
            MockCheckState.Rejected,
            people.worksOf(UserId("user-me")).first { it.title == "Прототип AR-навигации" }.state,
        )
    }

    @Test
    fun viewerHasTwelveReviewsElevenOfThemPositive() {
        val reviews = people.reviewsAbout(UserId("user-me"))

        assertEquals(12, reviews.size)
        assertEquals(11, reviews.count { it.rating >= 4 })
        assertEquals(4.75, people.rating(UserId("user-me")))
        assertEquals(
            listOf("Довёл модели до демо", "Помогал команде с данными", "Быстро включился в работу"),
            reviews.take(3).map { it.title },
        )
    }

    @Test
    fun elenaKeepsTheRatingOfTheProjectPageDesign() {
        assertEquals(4.9, people.rating(UserId("user-elena")))
        assertEquals(10, people.reviewsAbout(UserId("user-elena")).size)
        assertEquals(12, people.person(UserId("user-elena"))?.completedProjects)
    }

    @Test
    fun personHasReviewsExactlyWhenHasFinishedProjects() {
        for (person in people.persons) {
            val hasReviews = people.reviewsAbout(person.id).isNotEmpty()

            assertEquals(person.completedProjects > 0, hasReviews, "${person.id}")
        }
    }

    @Test
    fun idsAreUniqueInEveryTable() {
        assertEquals(people.persons.size, people.persons.map { it.id }.toSet().size)
        assertEquals(people.works.size, people.works.map { it.id }.toSet().size)
        assertEquals(people.documents.size, people.documents.map { it.id }.toSet().size)
        assertEquals(people.reviews.size, people.reviews.map { it.id }.toSet().size)
    }

    @Test
    fun everyRowPointsToAKnownPerson() {
        val known = people.persons.map { it.id }.toSet()

        assertTrue(people.works.all { it.ownerId in known })
        assertTrue(people.documents.all { it.ownerId in known })
        assertTrue(people.reviews.all { it.authorId in known && it.subjectId in known })
    }

    @Test
    fun nobodyReviewsThemself() {
        assertTrue(people.reviews.none { it.authorId == it.subjectId })
    }

    @Test
    fun ratingsAreFromOneToFive() {
        assertTrue(people.reviews.all { it.rating in 1..5 })
    }

    @Test
    fun hasPeopleOfBothVerificationStatuses() {
        val verified = people.persons.count { people.isVerified(it.id) }

        assertTrue(verified > 0)
        assertTrue(verified < people.persons.size)
    }

    @Test
    fun hasWorksInEveryModerationState() {
        val states = people.works.map { it.state }.toSet()

        assertTrue(MockCheckState.Verified in states)
        assertTrue(MockCheckState.Rejected in states)
        assertTrue(MockCheckState.Unconfirmed in states)
    }

    @Test
    fun nobodyHasAnotherPersonsNameOrAUserIdTwice() {
        assertEquals(people.persons.size, people.persons.map { it.name }.toSet().size)
    }
}
