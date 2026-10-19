package org.matchlab.academic_net.feature.profile.data

import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentId
import org.matchlab.academic_net.feature.profile.domain.DocumentKind
import org.matchlab.academic_net.feature.profile.domain.ProfileDraft
import org.matchlab.academic_net.feature.profile.domain.ProofAttachment
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.feature.profile.domain.WorkId
import org.matchlab.academic_net.gateway.dto.UserAchievementInputDto
import org.matchlab.academic_net.gateway.dto.UserAchievementListDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Gateway's shapes as the models of the profile feature, and back. Only what the API defines is read, so what it does
 * not define (the state of a record, the role of a reviewer, the project types of a person) is `null` or empty here.
 */
class GatewayProfileMappingTest {
    // ---- the profile ----

    @Test
    fun aProfileRecordTakesTheTextsTrimmedAndTheNumbersOfTheServer() {
        val record = profileDto(
            name = "  Арджун Мехта ",
            about = "  Люблю данные \n",
            interests = listOf("ML", "NLP"),
            skills = listOf("Kotlin"),
            verified = true,
            rating = 4.5,
            reviewCount = 4,
            positiveReviewCount = 3,
        ).toRecord()

        assertEquals("Арджун Мехта", record.person.user.name)
        assertTrue(record.person.user.isVerified)
        assertEquals("Люблю данные", record.about)
        assertEquals(listOf("ML", "NLP"), record.interests.topics)
        assertEquals(listOf("Kotlin"), record.skills)
        assertEquals(4.5, record.reviews.average)
        assertEquals(4, record.reviews.count)
        assertEquals(3, record.reviews.positiveCount)
    }

    @Test
    fun theProjectTypesOfAProfileAreReadByTheirTexts() {
        assertEquals(setOf(ProjectType.Scientific), profileDto(projectTypes = listOf("scientific")).toRecord().interests.projectTypes)
        assertEquals(setOf(ProjectType.Practical), profileDto(projectTypes = listOf("practical")).toRecord().interests.projectTypes)
        assertEquals(
            setOf(ProjectType.Scientific, ProjectType.Practical),
            profileDto(projectTypes = listOf("practical", "scientific")).toRecord().interests.projectTypes,
        )
    }

    @Test
    fun theTextsOfProjectTypesAreReadAfterTheOuterSpacesAreCut() {
        assertEquals(setOf(ProjectType.Practical), profileDto(projectTypes = listOf(" practical ")).toRecord().interests.projectTypes)
    }

    @Test
    fun theProjectTypesTheAppDoesNotKnowAreNotInTheModel() {
        val record = profileDto(projectTypes = listOf("1", "3", "01", "1.0", "", "  ", "whatever", "Scientific")).toRecord()

        assertEquals(emptySet(), record.interests.projectTypes)
    }

    @Test
    fun aKnownTypeStaysWhenUnknownOnesComeWithIt() {
        val record = profileDto(projectTypes = listOf("whatever", "practical", "9")).toRecord()

        assertEquals(setOf(ProjectType.Practical), record.interests.projectTypes)
    }

    @Test
    fun aRepeatedTextIsOneType() {
        assertEquals(setOf(ProjectType.Scientific), profileDto(projectTypes = listOf("scientific", "scientific", " scientific")).toRecord().interests.projectTypes)
    }

    @Test
    fun aProfileWithoutProjectTypesHasNone() {
        assertEquals(emptySet(), profileDto(projectTypes = emptyList()).toRecord().interests.projectTypes)
    }

    @Test
    fun aProfileWithoutReviewsHasNoRating() {
        val record = profileDto(rating = 4.9, reviewCount = 0).toRecord()

        assertNull(record.reviews.average)
    }

    @Test
    fun aProfileWithoutOptionalTextsHasEmptyOnes() {
        val record = profileDto(university = null, studyLevel = null, studyYear = null, specialization = null, about = null).toRecord()

        assertEquals("", record.person.university)
        assertEquals(Study(), record.person.study)
        assertEquals("", record.person.field)
        assertEquals("", record.about)
    }

    // ---- the form of the profile laid over the server's details ----

    private val serverDetails = UserProfileInputDto(
        displayName = "Старое имя",
        avatarFileId = "file-1",
        university = "Старый вуз",
        studyLevel = "магистратура",
        studyYear = 1,
        specialization = "Biology",
        about = "Старый текст",
        openToCollaboration = true,
        projectTypes = listOf("something-the-client-does-not-know"),
        interests = listOf("old"),
        skills = listOf("old"),
        contactEmail = "a@b.c",
        telegram = "@a",
    )

    @Test
    fun theFormReplacesWhatItHasAndLeavesTheRest() {
        val saved = serverDetails.withDraft(
            ProfileDraft(
                name = "Новое имя",
                university = "Новый вуз",
                studyLevel = "бакалавриат",
                studyYear = "3",
                field = "Computer Science",
                about = "Новый текст",
                interests = listOf("ML"),
                skills = listOf("Kotlin", "SQL"),
            ),
        )

        assertEquals("Новое имя", saved.displayName)
        assertEquals("Новый вуз", saved.university)
        assertEquals("бакалавриат", saved.studyLevel)
        assertEquals(3, saved.studyYear)
        assertEquals("Computer Science", saved.specialization)
        assertEquals("Новый текст", saved.about)
        assertEquals(listOf("ML"), saved.interests)
        assertEquals(listOf("Kotlin", "SQL"), saved.skills)
        // Not the form's: they stay as the server sent them (API-GAP G18, and the switch of the settings).
        assertEquals("file-1", saved.avatarFileId)
        assertEquals(true, saved.openToCollaboration)
        assertEquals("a@b.c", saved.contactEmail)
        assertEquals("@a", saved.telegram)
    }

    @Test
    fun aBlankFieldOfTheFormClearsTheField() {
        val saved = serverDetails.withDraft(ProfileDraft(name = "Имя", university = "  ", studyLevel = "", studyYear = "", field = "", about = ""))

        assertNull(saved.university)
        assertNull(saved.studyLevel)
        assertNull(saved.studyYear)
        assertNull(saved.specialization)
        assertNull(saved.about)
    }

    @Test
    fun theProjectTypesOfTheFormAreWrittenAsTextsAndTheUnknownEntriesOfTheServerStay() {
        val saved = serverDetails.withDraft(ProfileDraft(name = "Имя", interestTypes = setOf(ProjectType.Scientific)))

        assertEquals(listOf("something-the-client-does-not-know", "scientific"), saved.projectTypes)
    }

    @Test
    fun noChosenTypeKeepsOnlyTheUnknownEntries() {
        val saved = serverDetails.copy(projectTypes = listOf("scientific", "practical", "other")).withDraft(ProfileDraft(name = "Имя"))

        assertEquals(listOf("other"), saved.projectTypes)
    }

    @Test
    fun bothTypesAreWrittenInTheOrderOfTheTypes() {
        val saved = serverDetails.copy(projectTypes = emptyList())
            .withDraft(ProfileDraft(name = "Имя", interestTypes = setOf(ProjectType.Practical, ProjectType.Scientific)))

        assertEquals(listOf("scientific", "practical"), saved.projectTypes)
    }

    @Test
    fun aTypeTheServerHadAndTheFormDroppedIsGone() {
        val saved = serverDetails.copy(projectTypes = listOf("scientific", "practical"))
            .withDraft(ProfileDraft(name = "Имя", interestTypes = setOf(ProjectType.Practical)))

        assertEquals(listOf("practical"), saved.projectTypes)
    }

    @Test
    fun aChosenTypeTheServerAlreadyHadIsWrittenOnce() {
        val saved = serverDetails.copy(projectTypes = listOf("scientific", " scientific ", "scientific"))
            .withDraft(ProfileDraft(name = "Имя", interestTypes = setOf(ProjectType.Scientific)))

        assertEquals(listOf("scientific"), saved.projectTypes)
    }

    @Test
    fun anUnknownEntryIsReturnedAsItCameAndRepeatsAreOne() {
        val saved = serverDetails.copy(projectTypes = listOf(" weird ", "weird", "", " weird "))
            .withDraft(ProfileDraft(name = "Имя", interestTypes = setOf(ProjectType.Practical)))

        assertEquals(listOf(" weird ", "weird", "", "practical"), saved.projectTypes)
    }

    @Test
    fun withNoTypesAnywhereNothingIsWritten() {
        assertEquals(emptyList(), serverDetails.copy(projectTypes = emptyList()).withDraft(ProfileDraft(name = "Имя")).projectTypes)
    }

    @Test
    fun theCodesOfSelectedTypesWrittenAreReadBackAsTheSameSelection() {
        val selected = setOf(ProjectType.Scientific, ProjectType.Practical)

        val saved = serverDetails.withDraft(ProfileDraft(name = "Имя", interestTypes = selected))

        assertEquals(selected, profileDto(projectTypes = saved.projectTypes).toRecord().interests.projectTypes)
    }

    // ---- works ----

    @Test
    fun theKindsOfWorksAreScientificAndProject() {
        assertEquals(ProjectType.Scientific, achievementDto(kind = "scientific").toWork()?.type)
        assertEquals(ProjectType.Practical, achievementDto(kind = "project").toWork()?.type)
    }

    @Test
    fun otherKindsAreNotWorks() {
        for (kind in listOf("education", "certificate", "", "Scientific", "publication")) {
            assertNull(achievementDto(kind = kind).toWork(), kind)
        }
    }

    @Test
    fun aWorkTakesItsFieldsTrimmedAndWithoutAState() {
        val work = achievementDto(
            id = "w7",
            kind = "project",
            title = "  Проект  ",
            description = " Описание ",
            url = " https://github.com/a/b ",
            evidenceFileId = "file-9",
            revision = 5,
            moderationComment = "  Нужна ссылка ",
            status = "approved-or-whatever",
        ).toWork()!!

        assertEquals(WorkId("w7"), work.id)
        assertEquals("Проект", work.title)
        assertEquals("Описание", work.description)
        assertEquals("https://github.com/a/b", work.link)
        assertEquals("GitHub", work.source)
        assertTrue(work.hasEvidenceFile)
        assertEquals(5, work.revision)
        assertEquals("Нужна ссылка", work.moderationComment)
        // API-GAP G12: the strings of the status are not defined, so none of them is read.
        assertNull(work.status)
    }

    @Test
    fun aWorkWithoutALinkOrAFileHasNeither() {
        val work = achievementDto(url = "   ", evidenceFileId = "", description = null, moderationComment = "  ").toWork()!!

        assertNull(work.link)
        assertNull(work.source)
        assertFalse(work.hasEvidenceFile)
        assertEquals("", work.description)
        assertNull(work.moderationComment)
    }

    // ---- documents ----

    @Test
    fun theKindsOfDocumentsAreEducationAndCertificate() {
        assertEquals(DocumentKind.Education, achievementDto(kind = "education").toDocument()?.kind)
        assertEquals(DocumentKind.Certificate, achievementDto(kind = "certificate").toDocument()?.kind)
        for (kind in listOf("scientific", "project", "", "diploma")) {
            assertNull(achievementDto(kind = kind).toDocument(), kind)
        }
    }

    @Test
    fun aDocumentTakesItsFieldsAndItsLinkIsItsProof() {
        val document = achievementDto(
            id = "d3",
            kind = "certificate",
            title = " Сертификат ",
            institution = " Coursera ",
            graduationYear = 2024,
            url = " https://files.example.com/c.pdf ",
            revision = 2,
        ).toDocument()!!

        assertEquals(DocumentId("d3"), document.id)
        assertEquals("Сертификат", document.title)
        assertEquals("Coursera", document.issuer)
        assertEquals(2024, document.year)
        assertEquals(ProofAttachment("https://files.example.com/c.pdf"), document.proof)
        assertEquals(2, document.revision)
        assertNull(document.status)
    }

    @Test
    fun aDocumentWithoutAYearOrAProofHasNone() {
        for (year in listOf(null, 0, -5)) {
            assertNull(achievementDto(kind = "education", graduationYear = year, url = null).toDocument()!!.year, "year $year")
        }
        assertNull(achievementDto(kind = "education", url = "  ").toDocument()!!.proof)
        assertTrue(achievementDto(kind = "education", url = null, evidenceFileId = "file-1").toDocument()!!.hasEvidenceFile)
    }

    // ---- the lists ----

    @Test
    fun theWorksAreNewestFirstByTheTimeTheyWereAdded() {
        val portfolio = UserAchievementListDto(
            listOf(
                achievementDto(id = "old", createdAt = 100),
                achievementDto(id = "new", createdAt = 300),
                achievementDto(id = "middle", kind = "project", createdAt = 200),
            ),
        ).toPortfolio()

        assertEquals(listOf("new", "middle", "old"), portfolio.works.map { it.id.value })
    }

    @Test
    fun worksAddedAtTheSameTimeKeepTheOrderOfTheList() {
        val portfolio = UserAchievementListDto(
            listOf(achievementDto(id = "a", createdAt = 0), achievementDto(id = "b", createdAt = 0), achievementDto(id = "c", createdAt = 0)),
        ).toPortfolio()

        assertEquals(listOf("a", "b", "c"), portfolio.works.map { it.id.value })
    }

    @Test
    fun theListIsSplitByKindAndAnUnknownKindIsLeftOut() {
        val portfolio = UserAchievementListDto(
            listOf(
                achievementDto(id = "w", kind = "scientific"),
                achievementDto(id = "d1", kind = "education"),
                achievementDto(id = "x", kind = "hackathon"),
                achievementDto(id = "d2", kind = "certificate"),
            ),
        ).toPortfolio()

        assertEquals(listOf("w"), portfolio.works.map { it.id.value })
        assertEquals(listOf("d1", "d2"), portfolio.documents.map { it.id.value })
    }

    @Test
    fun anEmptyListIsAnEmptyPortfolio() {
        assertEquals(Portfolio.Empty, UserAchievementListDto().toPortfolio())
    }

    // ---- reviews ----

    @Test
    fun aReviewTakesItsFieldsAndNoRoleAndNoVerificationOfTheAuthor() {
        val review = reviewDto(id = "r9", authorId = "a1", authorName = "  Елена ", rating = 4).toReview(TimeZone.UTC)!!

        assertEquals(ReviewId("r9"), review.id)
        assertEquals(UserId("a1"), review.author.id)
        assertEquals("Елена", review.author.name)
        // API-GAP G15 and G13: whether the author is verified and what their role was are not in the API.
        assertFalse(review.author.isVerified)
        assertNull(review.authorRole)
        assertEquals("Цифровой двойник", review.projectTitle)
        assertEquals("Отлично", review.title)
        assertEquals("Довёл до демо", review.text)
        assertEquals(4, review.rating)
        assertFalse(review.isByViewer)
    }

    @Test
    fun theRatingIsHeldToZeroToFive() {
        assertEquals(5, reviewDto(rating = 9).toReview(TimeZone.UTC)!!.rating)
        assertEquals(0, reviewDto(rating = -3).toReview(TimeZone.UTC)!!.rating)
        assertEquals(0, reviewDto(rating = 0).toReview(TimeZone.UTC)!!.rating)
    }

    @Test
    fun theMonthOfTheEndOfTheProjectIsTheOneOfTheTimeZone() {
        // 2025-06-30 20:00 UTC: June in London, already July in Auckland.
        val review = reviewDto(completedAt = 1_751_313_600)

        assertEquals(YearMonth(2025, Month.JUNE), review.toReview(TimeZone.UTC)!!.projectFinished)
        assertEquals(YearMonth(2025, Month.JULY), review.toReview(TimeZone.of("Pacific/Auckland"))!!.projectFinished)
    }

    @Test
    fun aProjectWithoutAnEndTimeHasNoMonth() {
        assertNull(reviewDto(completedAt = 0).toReview(TimeZone.UTC)!!.projectFinished)
        assertNull(reviewDto(completedAt = -1).toReview(TimeZone.UTC)!!.projectFinished)
    }

    // ---- dates the calendar cannot hold (API-GAP G57) ----

    @Test
    fun anEndTimeOfTheServerPastTheYear9999IsNoMonthAndTheReviewIsKept() {
        for (seconds in listOf(Long.MAX_VALUE, Long.MAX_VALUE - 1, 253_402_300_800)) {
            val review = reviewDto(id = "r-$seconds", completedAt = seconds).toReview(TimeZone.UTC)

            assertNotNull(review, "$seconds: the review is still a review")
            assertNull(review.projectFinished, "$seconds")
            assertEquals("Отлично", review.title)
        }
    }

    @Test
    fun theLastSecondOfTheYear9999IsAMonthInEveryTimeZoneWithoutFailing() {
        val lastSecond = 253_402_300_799
        val review = reviewDto(completedAt = lastSecond)

        assertEquals(YearMonth(9999, Month.DECEMBER), review.toReview(TimeZone.UTC)!!.projectFinished)
        // East of Greenwich that second is already in the year 10000 on the wall clock; the calendar still holds it.
        assertEquals(YearMonth(10000, Month.JANUARY), review.toReview(TimeZone.of("Pacific/Kiritimati"))!!.projectFinished)
        assertEquals(YearMonth(9999, Month.DECEMBER), review.toReview(TimeZone.of("Pacific/Pago_Pago"))!!.projectFinished)
    }

    @Test
    fun aListOfReviewsWithOneImpossibleDateIsReadWhole() {
        val reviews = listOf(
            reviewDto(id = "r1", completedAt = 1_751_313_600),
            reviewDto(id = "r2", completedAt = Long.MAX_VALUE),
            reviewDto(id = "r3", completedAt = 0),
        ).map { it.toReview(TimeZone.UTC)!! }

        assertEquals(listOf("r1", "r2", "r3"), reviews.map { it.id.value })
        assertEquals(listOf<YearMonth?>(YearMonth(2025, Month.JUNE), null, null), reviews.map { it.projectFinished })
    }

    // ---- records without an id (API-GAP G55) ----

    @Test
    fun aWorkWithoutAnIdIsNoWork() {
        for (id in listOf("", " ", "   ", "\t\n")) {
            assertNull(achievementDto(id = id, kind = "scientific").toWork(), "'$id'")
            assertNull(achievementDto(id = id, kind = "project").toWork(), "'$id'")
        }
    }

    @Test
    fun aDocumentWithoutAnIdIsNoDocument() {
        for (id in listOf("", " ", "   ", "\t\n")) {
            assertNull(achievementDto(id = id, kind = "education").toDocument(), "'$id'")
            assertNull(achievementDto(id = id, kind = "certificate").toDocument(), "'$id'")
        }
    }

    @Test
    fun anIdIsKeptAsItIsWhenItIsThere() {
        // Only an empty or blank id is dropped; no other id is touched (not trimmed, not checked for its form).
        assertEquals(WorkId("w-1"), achievementDto(id = "w-1").toWork()?.id)
        assertEquals(DocumentId("d 1"), achievementDto(id = "d 1", kind = "education").toDocument()?.id)
        assertEquals(ReviewId("r/1"), reviewDto(id = "r/1").toReview(TimeZone.UTC)?.id)
    }

    @Test
    fun aReviewWithoutAnIdIsNoReview() {
        for (id in listOf("", " ", "   ", "\t\n")) {
            assertNull(reviewDto(id = id).toReview(TimeZone.UTC), "'$id'")
        }
    }

    @Test
    fun aReviewWithoutAnAuthorIdIsKeptWithAnAuthorWhoHasNoId() {
        for (authorId in listOf("", " ", "   ", "\t")) {
            val review = reviewDto(id = "r1", authorId = authorId, authorName = " Елена ").toReview(TimeZone.UTC)

            assertNotNull(review, "'$authorId'")
            assertEquals("", review.author.id.value, "'$authorId': the id is the empty one, whatever the blanks were")
            assertEquals("Елена", review.author.name, "the name is shown all the same")
            assertEquals(ReviewId("r1"), review.id)
            assertFalse(review.isByViewer)
        }
    }

    @Test
    fun theListsLeaveOutTheRecordsWithoutAnId() {
        val portfolio = UserAchievementListDto(
            listOf(
                achievementDto(id = "w1", kind = "scientific", createdAt = 30),
                achievementDto(id = "", kind = "scientific", createdAt = 40),
                achievementDto(id = "d1", kind = "education"),
                achievementDto(id = "  ", kind = "certificate"),
                achievementDto(id = "w2", kind = "project", createdAt = 20),
                achievementDto(id = "", kind = "project", createdAt = 50),
            ),
        ).toPortfolio()

        assertEquals(listOf("w1", "w2"), portfolio.works.map { it.id.value })
        assertEquals(listOf("d1"), portfolio.documents.map { it.id.value })
    }

    @Test
    fun aListWithNothingButRecordsWithoutAnIdIsAnEmptyPortfolio() {
        val portfolio = UserAchievementListDto(
            listOf(achievementDto(id = ""), achievementDto(id = "", kind = "education"), achievementDto(id = " ", kind = "certificate")),
        ).toPortfolio()

        assertEquals(Portfolio.Empty, portfolio)
    }

    // ---- what is sent ----

    @Test
    fun aWorkIsSentWithItsKindAndWithoutBlankOptionalFields() {
        val input = WorkDraft(type = ProjectType.Practical, title = "Проект", description = "  ", link = "").toInput()

        assertEquals(UserAchievementInputDto(kind = "project", title = "Проект"), input)
        assertEquals("scientific", WorkDraft(title = "T", description = "D").toInput().kind)
    }

    @Test
    fun aWorkIsSentWithItsLink() {
        val input = WorkDraft(title = "T", description = "D", link = "https://doi.org/10.1/x").toInput()

        assertEquals("https://doi.org/10.1/x", input.url)
        assertEquals("D", input.description)
    }

    @Test
    fun aDocumentIsSentWithItsKindIssuerYearAndProof() {
        val input = DocumentDraft(
            kind = DocumentKind.Certificate,
            title = "Сертификат",
            issuer = "Coursera",
            year = "2025",
            proof = ProofAttachment("https://x.org/c.pdf"),
        ).toInput()

        assertEquals(
            UserAchievementInputDto(
                kind = "certificate",
                title = "Сертификат",
                institution = "Coursera",
                graduationYear = 2025,
                url = "https://x.org/c.pdf",
            ),
            input,
        )
        assertEquals("education", DocumentDraft(title = "T", issuer = "I").toInput().kind)
    }

    @Test
    fun aDocumentWithoutAYearOrAProofIsSentWithoutThem() {
        val input = DocumentDraft(title = "T", issuer = "I", year = "", proof = null, hasEvidenceFile = true).toInput()

        assertNull(input.graduationYear)
        assertNull(input.url)
    }

    @Test
    fun aChangeOfAWorkKeepsTheProofFileAndTheKeysOfTheRecord() {
        val record = UserAchievementInputDto(
            id = "w1",
            kind = "scientific",
            title = "Старое",
            description = "Старое описание",
            url = "https://old.example.com",
            evidenceFileId = "file-9",
            expectedRevision = 3,
        )

        val changed = record.withDraft(WorkDraft(type = ProjectType.Practical, title = "Новое", description = "", link = ""))

        assertEquals("project", changed.kind)
        assertEquals("Новое", changed.title)
        assertNull(changed.description)
        assertNull(changed.url)
        assertEquals("file-9", changed.evidenceFileId)
        assertEquals("w1", changed.id)
    }

    @Test
    fun aChangeOfADocumentKeepsTheProofFileAndTheKeysOfTheRecord() {
        val record = UserAchievementInputDto(
            id = "d1",
            kind = "education",
            title = "Диплом",
            institution = "ВШЭ",
            graduationYear = 2020,
            url = "https://old.example.com/d.pdf",
            evidenceFileId = "file-3",
        )

        val changed = record.withDraft(
            DocumentDraft(kind = DocumentKind.Certificate, title = "Сертификат", issuer = "Coursera", year = "", proof = null, hasEvidenceFile = true),
        )

        assertEquals("certificate", changed.kind)
        assertEquals("Сертификат", changed.title)
        assertEquals("Coursera", changed.institution)
        assertNull(changed.graduationYear)
        assertNull(changed.url)
        assertEquals("file-3", changed.evidenceFileId)
        assertEquals("d1", changed.id)
    }
}
