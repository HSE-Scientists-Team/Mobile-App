package org.matchlab.academic_net.mockserver

import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary

/** What the mock server's moderation says about a work or a document; the owner sees all four. */
enum class MockCheckState {
    /** Added without a link or a proof: nobody checks it. */
    Unconfirmed,

    /** Waits for a moderator's decision. */
    Pending,

    /** A moderator confirmed it. */
    Verified,

    /** A moderator did not confirm it. */
    Rejected,
}

/**
 * A row of the "people" table: the facts about one person. Whether the person is verified is not
 * stored: it follows from the person's documents ([MockPeople.isVerified]).
 */
data class MockServerPerson(
    val id: UserId,
    val name: String,
    /** The university as the server writes it ("Stanford", "ИТМО"). */
    val university: String,
    /** Year and level, or status: "3 курс бакалавриата", "аспирантка". */
    val studyInfo: String,
    /** Field of study: "Computer Science". */
    val field: String,
    /** The "О себе" text; blank until the person writes it. */
    val about: String = "",
    val interestTypes: Set<ProjectType> = emptySet(),
    val interests: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    /** How many projects the person has finished. */
    val completedProjects: Int = 0,
)

/** A row of the "works" table: a scientific or a practical work of [ownerId]. */
data class MockServerWork(
    val id: String,
    val ownerId: UserId,
    val type: ProjectType,
    val title: String,
    val description: String,
    /** Where the work lives (a publication, a repository); `null` if the author gave no link. */
    val link: String? = null,
    /** The author attached a proof of the work. */
    val hasProof: Boolean = false,
    val state: MockCheckState,
)

/** A row of the "documents" table: an education document or a certificate of [ownerId]. */
data class MockServerDocument(
    val id: String,
    val ownerId: UserId,
    /** An education document (a diploma, a student card); otherwise it is a certificate. */
    val isEducation: Boolean,
    val title: String,
    val issuer: String,
    val year: Int?,
    val state: MockCheckState,
)

/** A row of the "reviews" table: [authorId] reviewed [subjectId] after a finished project. */
data class MockServerReview(
    val id: String,
    val authorId: UserId,
    val subjectId: UserId,
    /** The author was the initiator of the project; otherwise a participant. */
    val authorIsInitiator: Boolean,
    val projectTitle: String,
    val projectFinished: YearMonth,
    val title: String,
    /** 1 to 5 stars. */
    val rating: Int,
    val text: String,
)

/**
 * The mock server's tables of people: who they are, their works, documents and reviews. The mock
 * data sources of several features answer from the same [default] instance, so a person has one
 * id, one name and one verification status everywhere: the catalog of projects takes the
 * initiators' university, rating and finished projects from here, the profile feature builds the
 * profiles from here.
 *
 * Every table lists the newest rows first. The numbers are derived, not stored: a person is
 * verified when an education document is confirmed, the rating is the mean of the reviews.
 */
class MockPeople(
    val persons: List<MockServerPerson>,
    val works: List<MockServerWork> = emptyList(),
    val documents: List<MockServerDocument> = emptyList(),
    val reviews: List<MockServerReview> = emptyList(),
) {
    private val personsById = persons.associateBy { it.id }

    /** The person with [id], or `null` when the server does not know them. */
    fun person(id: UserId): MockServerPerson? = personsById[id]

    /** Works of [id], newest first. */
    fun worksOf(id: UserId): List<MockServerWork> = works.filter { it.ownerId == id }

    /** Documents of [id] in the order of the table. */
    fun documentsOf(id: UserId): List<MockServerDocument> = documents.filter { it.ownerId == id }

    /** Reviews about [id], newest project first (rows of the same month keep the table's order). */
    fun reviewsAbout(id: UserId): List<MockServerReview> =
        reviews.filter { it.subjectId == id }.sortedByDescending { it.projectFinished }

    /** Whether the person has a confirmed education document. */
    fun isVerified(id: UserId): Boolean =
        documentsOf(id).any { it.isEducation && it.state == MockCheckState.Verified }

    /** The person as an author or a member, with the verification status; `null` for an unknown id. */
    fun summary(id: UserId): UserSummary? =
        person(id)?.let { UserSummary(it.id, it.name, isVerified = isVerified(it.id)) }

    /** Mean rating of the reviews about [id], `null` while there are none. */
    fun rating(id: UserId): Double? =
        reviewsAbout(id).takeIf { it.isNotEmpty() }?.let { found -> found.sumOf { it.rating }.toDouble() / found.size }

    companion object {
        /** The seeded tables: the people of the Figma screens and of the other mock data sources. */
        val default: MockPeople = MockPeopleSeed.create()
    }
}
