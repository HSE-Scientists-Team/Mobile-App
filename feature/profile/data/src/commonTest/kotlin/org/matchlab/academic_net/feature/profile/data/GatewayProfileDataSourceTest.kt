package org.matchlab.academic_net.feature.profile.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentId
import org.matchlab.academic_net.feature.profile.domain.DocumentKind
import org.matchlab.academic_net.feature.profile.domain.EditOutcome
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileDraft
import org.matchlab.academic_net.feature.profile.domain.ProfileRequiredException
import org.matchlab.academic_net.feature.profile.domain.ProofAttachment
import org.matchlab.academic_net.feature.profile.domain.UploadFile
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.feature.profile.domain.WorkId
import org.matchlab.academic_net.gateway.UserDirectory
import org.matchlab.academic_net.gateway.dto.UserAchievementInputDto
import org.matchlab.academic_net.gateway.dto.UserAchievementListDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserReviewListDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [GatewayProfileDataSource] over fakes of the Gateway's calls and a real [UserDirectory]: what each status of the table in
 * its documentation becomes, and what is sent.
 */
class GatewayProfileDataSourceTest {
    private class Rig(scope: CoroutineScope, var zone: TimeZone = TimeZone.UTC) {
        val profiles = FakeProfilesApi()
        val achievements = FakeAchievementsApi()
        val reviews = FakeReviewsApi()
        val media = FakeMediaApi()
        val people = mutableMapOf<String, UserProfileDto>()
        val publicReads = mutableListOf<String>()

        /** The failure the public profile call answers with, if any. */
        var publicFailure: RemoteFailure? = null

        val directory = UserDirectory(
            fetch = { id ->
                publicReads += id
                publicFailure?.let { throw it }
                people[id] ?: throw RemoteFailure.Http(404)
            },
            scope = scope,
        )
        val source = GatewayProfileDataSource(profiles, achievements, reviews, directory, media) { zone }
    }

    private fun kotlinx.coroutines.test.TestScope.rig() = Rig(backgroundScope)

    private val other = UserId(OtherRaw)

    // ---- what it can do ----

    @Test
    fun theProjectTypesAndTheStatesOfRecordsAreOn() = runTest {
        val capabilities = rig().source.capabilities

        assertTrue(capabilities.verificationStatuses, "the backend lists the states of a record")
        assertTrue(capabilities.interestTypes)
    }

    @Test
    fun savingTheChosenTypesWritesTheirTextsNextToTheUnknownEntriesOfTheServer() = runTest {
        val rig = rig()
        rig.profiles.stored = profileDto(userId = ViewerRaw, name = "Старое", projectTypes = listOf("unknown-value", "scientific"))

        val record = rig.source.updateMyProfile(ProfileDraft(name = "Новое", interestTypes = setOf(ProjectType.Practical)))

        assertEquals(listOf("unknown-value", "practical"), rig.profiles.updates.single().projectTypes)
        assertEquals(setOf(ProjectType.Practical), record.interests.projectTypes)
    }

    @Test
    fun myProfileShowsTheTypesWhoseTextsTheServerHolds() = runTest {
        val rig = rig()
        rig.profiles.onMyProfile = { profileDto(userId = ViewerRaw, name = "Я", projectTypes = listOf("practical", "unknown-value")) }

        assertEquals(setOf(ProjectType.Practical), rig.source.getMyProfile()?.interests?.projectTypes)
    }

    // ---- other people's profiles ----

    @Test
    fun aProfileComesFromTheDirectoryAndIsKept() = runTest {
        val rig = rig()
        rig.people[OtherRaw] = profileDto(name = "Елена Смирнова")

        val first = rig.source.getProfile(other)
        val second = rig.source.getProfile(other)

        assertEquals("Елена Смирнова", first?.person?.user?.name)
        assertEquals(first, second)
        assertEquals(listOf(OtherRaw), rig.publicReads, "the second read comes from the directory")
    }

    @Test
    fun aPersonTheServerDoesNotShowIsNull() = runTest {
        for (status in listOf(400, 403, 404)) {
            val rig = rig()
            rig.publicFailure = RemoteFailure.Http(status)

            assertNull(rig.source.getProfile(other), "status $status")
        }
    }

    @Test
    fun aServerErrorOnAProfileIsThrown() = runTest {
        val rig = rig()
        rig.publicFailure = RemoteFailure.Http(500)

        assertEquals(500, assertFailsWith<RemoteFailure.Http> { rig.source.getProfile(other) }.status)
    }

    // ---- other people's portfolios ----

    @Test
    fun thePortfolioOfAPersonIsTheLargestPageSplitByKind() = runTest {
        val rig = rig()
        rig.achievements.onUserAchievements = { _, _, _ ->
            UserAchievementListDto(
                listOf(
                    achievementDto(id = "w1", kind = "scientific", createdAt = 10),
                    achievementDto(id = "d1", kind = "education"),
                    achievementDto(id = "w2", kind = "project", createdAt = 20),
                ),
            )
        }

        val portfolio = rig.source.getPortfolio(other)!!

        assertEquals(listOf("userAchievements($OtherRaw,100,0)"), rig.achievements.calls)
        assertEquals(listOf("w2", "w1"), portfolio.works.map { it.id.value })
        assertEquals(listOf("d1"), portfolio.documents.map { it.id.value })
    }

    @Test
    fun aPortfolioOfAPersonTheServerDoesNotShowIsNull() = runTest {
        for (status in listOf(400, 403, 404)) {
            val rig = rig()
            rig.achievements.onUserAchievements = { _, _, _ -> throw RemoteFailure.Http(status) }

            assertNull(rig.source.getPortfolio(other), "status $status")
        }
    }

    @Test
    fun aServerErrorOnAPortfolioIsThrown() = runTest {
        val rig = rig()
        rig.achievements.onUserAchievements = { _, _, _ -> throw RemoteFailure.Http(503) }

        assertEquals(503, assertFailsWith<RemoteFailure.Http> { rig.source.getPortfolio(other) }.status)
    }

    // ---- reviews ----

    @Test
    fun theReviewsAreAskedByThePageAndMapped() = runTest {
        val rig = rig()
        rig.reviews.onUserReviews = { _, _, _ -> UserReviewListDto(listOf(reviewDto(id = "r1"), reviewDto(id = "r2", rating = 4))) }

        val reviews = rig.source.getReviews(other, offset = 40, limit = 20)!!

        assertEquals(listOf(Triple(OtherRaw, 20, 40)), rig.reviews.calls)
        assertEquals(listOf("r1", "r2"), reviews.map { it.id.value })
        assertEquals(listOf(5, 4), reviews.map { it.rating })
    }

    @Test
    fun theMonthOfAReviewFollowsTheZoneAskedForAtEachCall() = runTest {
        val rig = rig()
        rig.reviews.onUserReviews = { _, _, _ -> UserReviewListDto(listOf(reviewDto(completedAt = 1_751_313_600))) }

        val inLondon = rig.source.getReviews(other, 0, 20)!!.single().projectFinished
        rig.zone = TimeZone.of("Pacific/Auckland")
        val inAuckland = rig.source.getReviews(other, 0, 20)!!.single().projectFinished

        assertEquals(YearMonth(2025, Month.JUNE), inLondon)
        assertEquals(YearMonth(2025, Month.JULY), inAuckland)
    }

    @Test
    fun reviewsOfAPersonTheServerDoesNotShowAreNullAndOtherErrorsAreThrown() = runTest {
        for (status in listOf(400, 403, 404)) {
            val rig = rig()
            rig.reviews.onUserReviews = { _, _, _ -> throw RemoteFailure.Http(status) }
            assertNull(rig.source.getReviews(other, 0, 20), "status $status")
        }

        val rig = rig()
        rig.reviews.onUserReviews = { _, _, _ -> throw RemoteFailure.NoConnection() }
        assertFailsWith<RemoteFailure.NoConnection> { rig.source.getReviews(other, 0, 20) }
    }

    // ---- the viewer's profile ----

    @Test
    fun theViewersProfileWithANameIsARecord() = runTest {
        val rig = rig()
        rig.profiles.onMyProfile = { profileDto(userId = ViewerRaw, name = "Арджун") }

        val record = rig.source.getMyProfile()

        assertEquals("Арджун", record?.person?.user?.name)
    }

    @Test
    fun anAccountWithoutAProfileIsNullWhetherTheServerSays404OrSendsAnEmptyOne() = runTest {
        val notFound = rig()
        notFound.profiles.onMyProfile = { throw RemoteFailure.Http(404) }
        assertNull(notFound.source.getMyProfile())

        // API-GAP G19: the API does not say which of the two a new account gets.
        val empty = rig()
        empty.profiles.onMyProfile = { UserProfileDto() }
        assertNull(empty.source.getMyProfile())

        val blankName = rig()
        blankName.profiles.onMyProfile = { profileDto(name = "   ") }
        assertNull(blankName.source.getMyProfile())
    }

    @Test
    fun otherFailuresOfTheViewersProfileAreThrown() = runTest {
        for (status in listOf(401, 403, 500)) {
            val rig = rig()
            rig.profiles.onMyProfile = { throw RemoteFailure.Http(status) }

            assertEquals(status, assertFailsWith<RemoteFailure.Http> { rig.source.getMyProfile() }.status)
        }
    }

    @Test
    fun savingTheProfileWritesTheFormOverWhatTheServerHasAndReturnsTheRecord() = runTest {
        val rig = rig()
        rig.profiles.stored = profileDto(
            userId = ViewerRaw,
            name = "Старое",
            avatarFileId = "file-1",
            contactEmail = "a@b.c",
            projectTypes = listOf("unknown-value"),
            openToCollaboration = true,
        )

        val record = rig.source.updateMyProfile(ProfileDraft(name = "Новое", skills = listOf("Kotlin"), studyLevel = "магистратура", studyYear = "2"))

        val written = rig.profiles.updates.single()
        assertEquals("Новое", written.displayName)
        assertEquals("file-1", written.avatarFileId)
        assertEquals("a@b.c", written.contactEmail)
        assertEquals(listOf("unknown-value"), written.projectTypes)
        assertEquals(true, written.openToCollaboration)
        assertEquals("Новое", record.person.user.name)
        assertEquals(Study(level = "магистратура", year = 2), record.person.study)
        assertEquals(listOf("Kotlin"), record.skills)
    }

    @Test
    fun aFailedSaveOfTheProfileIsThrown() = runTest {
        val rig = rig()
        rig.profiles.failUpdate = RemoteFailure.Http(409)

        assertEquals(409, assertFailsWith<RemoteFailure.Http> { rig.source.updateMyProfile(ProfileDraft(name = "Имя")) }.status)
    }

    // ---- the viewer's portfolio ----

    @Test
    fun theViewersPortfolioIsTheOwnersListOfTheLargestPage() = runTest {
        val rig = rig()
        rig.achievements.onMyAchievements = { _, _ ->
            UserAchievementListDto(listOf(achievementDto(id = "w1"), achievementDto(id = "d1", kind = "certificate")))
        }

        val portfolio = rig.source.getMyPortfolio()

        assertEquals(listOf("myAchievements(100,0)"), rig.achievements.calls)
        assertEquals(listOf("w1"), portfolio.works.map { it.id.value })
        assertEquals(listOf("d1"), portfolio.documents.map { it.id.value })
    }

    @Test
    fun anAccountWithoutAProfileHasAnEmptyPortfolioOn404() = runTest {
        val rig = rig()
        rig.achievements.onMyAchievements = { _, _ -> throw RemoteFailure.Http(404) }

        assertEquals(Portfolio.Empty, rig.source.getMyPortfolio())
    }

    @Test
    fun otherFailuresOfTheViewersPortfolioAreThrown() = runTest {
        val rig = rig()
        rig.achievements.onMyAchievements = { _, _ -> throw RemoteFailure.Http(500) }

        assertEquals(500, assertFailsWith<RemoteFailure.Http> { rig.source.getMyPortfolio() }.status)
    }

    // ---- one record of the viewer ----

    @Test
    fun aWorkOfTheViewerIsReadByItsId() = runTest {
        val rig = rig()
        rig.achievements.onMyAchievement = { achievementDto(id = it, kind = "project", title = "Проект") }

        val work = rig.source.getMyWork(WorkId("w1"))

        assertEquals("Проект", work?.title)
        assertEquals(ProjectType.Practical, work?.type)
    }

    @Test
    fun aRecordOfAnotherKindIsNotTheWorkOrTheDocumentThatWasAskedFor() = runTest {
        val rig = rig()
        rig.achievements.onMyAchievement = { achievementDto(id = it, kind = "education") }
        assertNull(rig.source.getMyWork(WorkId("d1")))
        assertEquals(DocumentKind.Education, rig.source.getMyDocument(DocumentId("d1"))?.kind)

        rig.achievements.onMyAchievement = { achievementDto(id = it, kind = "scientific") }
        assertNull(rig.source.getMyDocument(DocumentId("w1")))
    }

    @Test
    fun aRecordThatIsNotTheViewersIsNull() = runTest {
        for (status in listOf(400, 404)) {
            val rig = rig()
            rig.achievements.onMyAchievement = { throw RemoteFailure.Http(status) }

            assertNull(rig.source.getMyWork(WorkId("x")), "status $status")
            assertNull(rig.source.getMyDocument(DocumentId("x")), "status $status")
        }
    }

    @Test
    fun otherFailuresOnOneRecordAreThrown() = runTest {
        val rig = rig()
        rig.achievements.onMyAchievement = { throw RemoteFailure.Http(500) }

        assertEquals(500, assertFailsWith<RemoteFailure.Http> { rig.source.getMyWork(WorkId("x")) }.status)
        assertFailsWith<RemoteFailure.Http> { rig.source.getMyDocument(DocumentId("x")) }
    }

    // ---- adding ----

    @Test
    fun aWorkIsAddedWithTheInputOfTheDraftAndReadBackFromTheAnswer() = runTest {
        val rig = rig()
        rig.achievements.onCreate = { achievementDto(id = "new-1", kind = it.kind, title = it.title, url = it.url) }

        val work = rig.source.createWork(WorkDraft(type = ProjectType.Practical, title = "Проект", description = "Описание", link = "https://github.com/a/b"))

        val sent = rig.achievements.created.single()
        assertEquals("project", sent.kind)
        assertEquals("Проект", sent.title)
        assertEquals("Описание", sent.description)
        assertEquals("https://github.com/a/b", sent.url)
        assertEquals(WorkId("new-1"), work.id)
        assertEquals("GitHub", work.source)
    }

    @Test
    fun aWorkWhoseAnswerHasNoDetailsIsWhatWasSent() = runTest {
        val rig = rig()
        // Protobuf JSON leaves an empty object out, and the record of the answer then has no kind at all.
        rig.achievements.onCreate = { achievementDto(id = "new-2").copy(details = UserAchievementInputDto()) }

        val work = rig.source.createWork(WorkDraft(title = "Статья", description = "Описание"))

        assertEquals(WorkId("new-2"), work.id)
        assertEquals("Статья", work.title)
        assertEquals(ProjectType.Scientific, work.type)
    }

    @Test
    fun aWorkAnsweredAsAnotherKindIsABrokenAnswer() = runTest {
        val rig = rig()
        rig.achievements.onCreate = { achievementDto(kind = "education") }

        assertFailsWith<RemoteFailure.Malformed> { rig.source.createWork(WorkDraft(title = "T", description = "D")) }
    }

    @Test
    fun aDocumentIsAddedWithItsKindIssuerYearAndProof() = runTest {
        val rig = rig()
        rig.achievements.onCreate = { input ->
            achievementDto(id = "doc-1", kind = input.kind, title = input.title, institution = input.institution, graduationYear = input.graduationYear, url = input.url)
        }

        val document = rig.source.createDocument(
            DocumentDraft(
                kind = DocumentKind.Certificate,
                title = "Сертификат",
                issuer = "Coursera",
                year = "2025",
                proof = ProofAttachment("https://x.org/c.pdf"),
            ),
        )

        val sent = rig.achievements.created.single()
        assertEquals("certificate", sent.kind)
        assertEquals("Coursera", sent.institution)
        assertEquals(2025, sent.graduationYear)
        assertEquals("https://x.org/c.pdf", sent.url)
        assertEquals(DocumentId("doc-1"), document.id)
        assertEquals(ProofAttachment("https://x.org/c.pdf"), document.proof)
    }

    @Test
    fun aDocumentAnsweredAsAWorkIsABrokenAnswer() = runTest {
        val rig = rig()
        rig.achievements.onCreate = { achievementDto(kind = "scientific") }

        assertFailsWith<RemoteFailure.Malformed> {
            rig.source.createDocument(DocumentDraft(title = "T", issuer = "I", proof = ProofAttachment("https://x.org/a")))
        }
    }

    @Test
    fun aRejectedNewRecordIsThrown() = runTest {
        val rig = rig()
        rig.achievements.onCreate = { throw RemoteFailure.Http(400) }

        assertEquals(400, assertFailsWith<RemoteFailure.Http> { rig.source.createWork(WorkDraft(title = "T", description = "D")) }.status)
    }

    // ---- changing ----

    @Test
    fun aWorkIsChangedOnTheRevisionItWasBasedOnAndKeepsItsProofFile() = runTest {
        val rig = rig()
        rig.achievements.stored = achievementDto(id = "w1", evidenceFileId = "file-9", revision = 4)

        val outcome = rig.source.updateWork(WorkId("w1"), WorkDraft(title = "Новое", description = "Новое описание"), expectedRevision = 4)

        assertEquals("Новое", assertIs<EditOutcome.Done<PortfolioWork>>(outcome).value.title)
        assertEquals(listOf(4), rig.achievements.updatedRevisions)
        val (id, written) = rig.achievements.updated.single()
        assertEquals("w1", id)
        assertEquals("file-9", written.evidenceFileId)
        assertEquals("Новое описание", written.description)
        assertNull(written.url)
    }

    @Test
    fun aDocumentIsChangedOnTheSameRules() = runTest {
        val rig = rig()
        rig.achievements.stored = achievementDto(id = "d1", kind = "education", evidenceFileId = "file-3")

        val outcome = rig.source.updateDocument(
            DocumentId("d1"),
            DocumentDraft(title = "Диплом", issuer = "ВШЭ", year = "2024", hasEvidenceFile = true),
            expectedRevision = 1,
        )

        assertIs<EditOutcome.Done<*>>(outcome)
        val written = rig.achievements.updated.single().second
        assertEquals("file-3", written.evidenceFileId)
        assertEquals(2024, written.graduationYear)
        assertNull(written.url)
    }

    @Test
    fun aStaleRevisionOrAGoneRecordOnAChangeIsAnOutcomeNotAnError() = runTest {
        val rig = rig()
        rig.achievements.failUpdate = RemoteFailure.Http(409)
        assertEquals(EditOutcome.Conflict, rig.source.updateWork(WorkId("w1"), WorkDraft(title = "T", description = "D"), 1))
        assertEquals(EditOutcome.Conflict, rig.source.updateDocument(DocumentId("d1"), DocumentDraft(title = "T", issuer = "I"), 1))

        rig.achievements.failUpdate = RemoteFailure.Http(404)
        assertEquals(EditOutcome.NotFound, rig.source.updateWork(WorkId("w1"), WorkDraft(title = "T", description = "D"), 1))
        assertEquals(EditOutcome.NotFound, rig.source.updateDocument(DocumentId("d1"), DocumentDraft(title = "T", issuer = "I"), 1))
    }

    @Test
    fun otherFailuresOnAChangeAreThrown() = runTest {
        val rig = rig()
        rig.achievements.failUpdate = RemoteFailure.Http(500)
        assertEquals(500, assertFailsWith<RemoteFailure.Http> { rig.source.updateWork(WorkId("w1"), WorkDraft(title = "T", description = "D"), 1) }.status)

        rig.achievements.failUpdate = RemoteFailure.Timeout()
        assertFailsWith<RemoteFailure.Timeout> { rig.source.updateWork(WorkId("w1"), WorkDraft(title = "T", description = "D"), 1) }
    }

    @Test
    fun aChangeAnsweredAsAnotherKindIsABrokenAnswer() = runTest {
        val rig = rig()

        rig.achievements.updateAnswer = achievementDto(kind = "education")
        assertFailsWith<RemoteFailure.Malformed> {
            rig.source.updateWork(WorkId("w1"), WorkDraft(title = "T", description = "D"), 1)
        }

        rig.achievements.updateAnswer = achievementDto(kind = "scientific")
        assertFailsWith<RemoteFailure.Malformed> {
            rig.source.updateDocument(DocumentId("d1"), DocumentDraft(title = "T", issuer = "I", hasEvidenceFile = true), 1)
        }
    }

    // ---- sending and deleting ----

    @Test
    fun aWorkOrADocumentIsSentByItsId() = runTest {
        val rig = rig()

        assertEquals(EditOutcome.Done(Unit), rig.source.submit(VerificationSubject.Work(WorkId("w1"))))
        assertEquals(EditOutcome.Done(Unit), rig.source.submit(VerificationSubject.Document(DocumentId("d1"), DocumentKind.Education)))

        assertEquals(listOf("submit(w1)", "submit(d1)"), rig.achievements.calls)
    }

    @Test
    fun aSendThatFindsAConflictOrAGoneRecordIsAnOutcome() = runTest {
        val rig = rig()
        val subject = VerificationSubject.Work(WorkId("w1"))

        rig.achievements.onSubmit = { throw RemoteFailure.Http(409) }
        assertEquals(EditOutcome.Conflict, rig.source.submit(subject))

        rig.achievements.onSubmit = { throw RemoteFailure.Http(404) }
        assertEquals(EditOutcome.NotFound, rig.source.submit(subject))

        rig.achievements.onSubmit = { throw RemoteFailure.Http(500) }
        assertEquals(500, assertFailsWith<RemoteFailure.Http> { rig.source.submit(subject) }.status)
    }

    @Test
    fun aRecordIsDeletedByItsId() = runTest {
        val rig = rig()

        assertEquals(EditOutcome.Done(Unit), rig.source.delete(VerificationSubject.Work(WorkId("w1"))))
        assertEquals(EditOutcome.Done(Unit), rig.source.delete(VerificationSubject.Document(DocumentId("d1"), DocumentKind.Certificate)))

        assertEquals(listOf("delete(w1)", "delete(d1)"), rig.achievements.calls)
    }

    @Test
    fun aRecordThatIsGoneIsDeletedAndAConflictIsAnOutcome() = runTest {
        val rig = rig()
        val subject = VerificationSubject.Work(WorkId("w1"))

        rig.achievements.onDelete = { throw RemoteFailure.Http(404) }
        assertEquals(EditOutcome.Done(Unit), rig.source.delete(subject))

        rig.achievements.onDelete = { throw RemoteFailure.Http(409) }
        assertEquals(EditOutcome.Conflict, rig.source.delete(subject))
    }

    @Test
    fun otherFailuresOnADeleteAreThrown() = runTest {
        val rig = rig()
        val subject = VerificationSubject.Work(WorkId("w1"))

        rig.achievements.onDelete = { throw RemoteFailure.Http(500) }
        assertEquals(500, assertFailsWith<RemoteFailure.Http> { rig.source.delete(subject) }.status)

        rig.achievements.onDelete = { throw RemoteFailure.NoConnection() }
        assertTrue(runCatching { rig.source.delete(subject) }.exceptionOrNull() is RemoteFailure.NoConnection)
    }

    // ---- records without an id (API-GAP G55) ----

    private fun portfolioWithRecordsWithoutAnId() = UserAchievementListDto(
        listOf(
            achievementDto(id = "w1", kind = "scientific"),
            achievementDto(id = "", kind = "scientific"),
            achievementDto(id = "d1", kind = "education"),
            achievementDto(id = "   ", kind = "certificate"),
            achievementDto(id = "", kind = "project"),
        ),
    )

    @Test
    fun thePortfolioOfAPersonLeavesOutTheRecordsWithoutAnId() = runTest {
        val rig = rig()
        rig.achievements.onUserAchievements = { _, _, _ -> portfolioWithRecordsWithoutAnId() }

        val portfolio = rig.source.getPortfolio(other)!!

        assertEquals(listOf("w1"), portfolio.works.map { it.id.value })
        assertEquals(listOf("d1"), portfolio.documents.map { it.id.value })
    }

    @Test
    fun theViewersPortfolioLeavesOutTheRecordsWithoutAnId() = runTest {
        val rig = rig()
        rig.achievements.onMyAchievements = { _, _ -> portfolioWithRecordsWithoutAnId() }

        val portfolio = rig.source.getMyPortfolio()

        assertEquals(listOf("w1"), portfolio.works.map { it.id.value })
        assertEquals(listOf("d1"), portfolio.documents.map { it.id.value })
    }

    @Test
    fun aPageOfReviewsWithRecordsWithoutAnIdIsReadWithoutThem() = runTest {
        val rig = rig()
        rig.reviews.onUserReviews = { _, _, _ ->
            UserReviewListDto(listOf(reviewDto(id = "r1"), reviewDto(id = ""), reviewDto(id = "  "), reviewDto(id = "r4")))
        }

        val reviews = rig.source.getReviews(other, offset = 0, limit = 4)!!

        // The page is shorter than the server's page: a known limit, see the KDoc of the source.
        assertEquals(listOf("r1", "r4"), reviews.map { it.id.value })
    }

    @Test
    fun aPageOfReviewsWithNoIdAtAllIsEmptyAndNotAnError() = runTest {
        val rig = rig()
        rig.reviews.onUserReviews = { _, _, _ -> UserReviewListDto(listOf(reviewDto(id = ""), reviewDto(id = ""))) }

        assertEquals(emptyList(), rig.source.getReviews(other, offset = 0, limit = 2))
    }

    @Test
    fun aReviewWithoutAnAuthorIdStaysInThePageWithAnAuthorWhoHasNoId() = runTest {
        val rig = rig()
        rig.reviews.onUserReviews = { _, _, _ ->
            UserReviewListDto(listOf(reviewDto(id = "r1", authorId = "", authorName = "Анонимный автор"), reviewDto(id = "r2", authorId = "a2")))
        }

        val reviews = rig.source.getReviews(other, offset = 0, limit = 2)!!

        assertEquals(listOf("r1", "r2"), reviews.map { it.id.value })
        assertEquals("", reviews[0].author.id.value)
        assertEquals("Анонимный автор", reviews[0].author.name)
        assertEquals("a2", reviews[1].author.id.value)
    }

    @Test
    fun aNewRecordThatTheServerAnswersWithoutAnIdIsABrokenAnswer() = runTest {
        val rig = rig()
        rig.achievements.onCreate = { input -> achievementDto(id = "", kind = input.kind, title = input.title) }

        assertFailsWith<RemoteFailure.Malformed> { rig.source.createWork(WorkDraft(title = "Статья", description = "О графах")) }
        assertFailsWith<RemoteFailure.Malformed> {
            rig.source.createDocument(DocumentDraft(title = "Диплом", issuer = "ВШЭ", proof = ProofAttachment("https://x.org/d.pdf")))
        }
    }

    @Test
    fun aChangedRecordThatTheServerAnswersWithoutAnIdIsABrokenAnswer() = runTest {
        val rig = rig()
        rig.achievements.updateAnswer = achievementDto(id = "", kind = "scientific")
        assertFailsWith<RemoteFailure.Malformed> { rig.source.updateWork(WorkId("w1"), WorkDraft(title = "T", description = "D"), 1) }

        rig.achievements.updateAnswer = achievementDto(id = "", kind = "education")
        assertFailsWith<RemoteFailure.Malformed> {
            rig.source.updateDocument(DocumentId("d1"), DocumentDraft(title = "T", issuer = "I", hasEvidenceFile = true), 1)
        }
    }

    @Test
    fun aRecordOfTheViewerThatCameWithoutAnIdIsNotTheOneThatWasAskedFor() = runTest {
        val rig = rig()
        rig.achievements.onMyAchievement = { achievementDto(id = "", kind = "scientific") }
        assertNull(rig.source.getMyWork(WorkId("w1")))

        rig.achievements.onMyAchievement = { achievementDto(id = "", kind = "education") }
        assertNull(rig.source.getMyDocument(DocumentId("d1")))
    }

    // ---- dates the calendar cannot hold (API-GAP G57) ----

    @Test
    fun aPageOfReviewsWithAnEndTimeOfLongMaxValueIsLoadedWithoutAMonth() = runTest {
        val rig = rig()
        rig.reviews.onUserReviews = { _, _, _ ->
            UserReviewListDto(listOf(reviewDto(id = "r1", completedAt = Long.MAX_VALUE), reviewDto(id = "r2", completedAt = 1_751_313_600)))
        }

        val reviews = rig.source.getReviews(other, offset = 0, limit = 2)!!

        assertEquals(listOf("r1", "r2"), reviews.map { it.id.value })
        assertEquals(listOf<YearMonth?>(null, YearMonth(2025, Month.JUNE)), reviews.map { it.projectFinished })
    }

    // ---- the avatar ----

    private val photo = UploadFile(byteArrayOf(1, 2, 3), "image/jpeg", "avatar.jpg")

    @Test
    fun aPhotoIsUploadedPublicAndSavedAsTheAvatarKeepingTheRestOfTheProfile() = runTest {
        val rig = rig()
        rig.profiles.stored = profileDto(userId = ViewerRaw, name = "Арджун")

        rig.source.changeAvatar(photo)

        assertEquals(listOf("create", "put file-1", "complete file-1"), rig.media.calls)
        val request = rig.media.created.single()
        assertEquals("avatar.jpg", request.originalName)
        assertEquals("image/jpeg", request.contentType)
        assertEquals(3L, request.sizeBytes)
        assertTrue(request.isPublic, "an avatar is read by everybody")
        val saved = rig.profiles.updates.single()
        assertEquals("file-1", saved.avatarFileId)
        assertEquals("Арджун", saved.displayName)
    }

    @Test
    fun thePreviousAvatarFileIsRemovedAfterTheSwitch() = runTest {
        val rig = rig()
        rig.profiles.stored = profileDto(userId = ViewerRaw, name = "Арджун").let { it.copy(details = it.details.copy(avatarFileId = "old-file")) }

        rig.source.changeAvatar(photo)

        assertEquals(listOf("old-file"), rig.media.deleted)
        assertEquals("file-1", rig.profiles.updates.single().avatarFileId)
    }

    @Test
    fun aFailureToRemoveTheOldFileIsNotAFailureOfTheChange() = runTest {
        val rig = rig()
        rig.profiles.stored = profileDto(userId = ViewerRaw, name = "Арджун").let { it.copy(details = it.details.copy(avatarFileId = "old-file")) }
        rig.media.failDelete = RemoteFailure.Http(500)

        rig.source.changeAvatar(photo)

        assertEquals("file-1", rig.profiles.updates.single().avatarFileId)
    }

    @Test
    fun anAccountWithoutAProfileCannotHaveAnAvatarAndNothingIsSaved() = runTest {
        val rig = rig()
        rig.profiles.stored = null

        assertFailsWith<ProfileRequiredException> { rig.source.changeAvatar(photo) }

        assertTrue(rig.profiles.updates.isEmpty())
    }

    @Test
    fun aFailedUploadSavesNothing() = runTest {
        val rig = rig()
        rig.profiles.stored = profileDto(userId = ViewerRaw, name = "Арджун")
        rig.media.failPut = RemoteFailure.NoConnection()

        assertFailsWith<RemoteFailure.NoConnection> { rig.source.changeAvatar(photo) }

        assertTrue(rig.profiles.updates.isEmpty())
        assertEquals(emptyList(), rig.media.deleted)
    }

    // ---- the proof file ----

    private val proof = UploadFile(byteArrayOf(5, 6), "application/pdf", "diploma.pdf")

    @Test
    fun aNewWorkWithAFileUploadsItPrivateFirstAndSendsItsIdWithTheRecord() = runTest {
        val rig = rig()

        rig.source.createWork(WorkDraft(title = "Статья", description = "О графах", proofFile = proof))

        assertEquals(listOf("create", "put file-1", "complete file-1"), rig.media.calls)
        val request = rig.media.created.single()
        assertEquals("diploma.pdf", request.originalName)
        assertEquals("application/pdf", request.contentType)
        assertFalse(request.isPublic, "the server accepts only a private file as a proof")
        assertEquals("file-1", rig.achievements.created.single().evidenceFileId)
    }

    @Test
    fun aNewDocumentWithOnlyAFileHasNoLinkAndHasTheFile() = runTest {
        val rig = rig()

        rig.source.createDocument(DocumentDraft(title = "Диплом", issuer = "ВШЭ", proofFile = proof))

        val input = rig.achievements.created.single()
        assertEquals("file-1", input.evidenceFileId)
        assertNull(input.url)
    }

    @Test
    fun aFailedUploadCreatesNoRecord() = runTest {
        val rig = rig()
        rig.media.failPut = RemoteFailure.Timeout()

        assertFailsWith<RemoteFailure.Timeout> {
            rig.source.createWork(WorkDraft(title = "Статья", description = "О графах", proofFile = proof))
        }

        assertTrue(rig.achievements.created.isEmpty())
    }

    @Test
    fun editingWithoutTouchingTheFileUploadsNothingAndKeepsTheOldOne() = runTest {
        val rig = rig()
        rig.achievements.stored = achievementDto(id = "w1", evidenceFileId = "old-file")

        rig.source.updateWork(WorkId("w1"), WorkDraft(title = "Новое", description = "Описание", hasEvidenceFile = true), expectedRevision = 1)

        assertTrue(rig.media.calls.isEmpty())
        assertEquals("old-file", rig.achievements.updated.single().second.evidenceFileId)
    }

    @Test
    fun aNewFileReplacesTheOldOneOfARecordAndTheOldOneStaysOnTheStorage() = runTest {
        val rig = rig()
        rig.achievements.stored = achievementDto(id = "w1", evidenceFileId = "old-file")

        rig.source.updateWork(
            WorkId("w1"),
            WorkDraft(title = "Новое", description = "Описание", hasEvidenceFile = true, proofFile = proof),
            expectedRevision = 1,
        )

        assertEquals("file-1", rig.achievements.updated.single().second.evidenceFileId)
        assertTrue(rig.media.deleted.isEmpty(), "the history of the decisions refers to the old file")
    }

    @Test
    fun removingTheFileClearsItOnTheRecord() = runTest {
        val rig = rig()
        rig.achievements.stored = achievementDto(id = "d1", kind = "education", institution = "ВШЭ", evidenceFileId = "old-file")

        rig.source.updateDocument(
            DocumentId("d1"),
            DocumentDraft(title = "Диплом", issuer = "ВШЭ", proof = ProofAttachment("https://x.example/d"), hasEvidenceFile = true, removeEvidenceFile = true),
            expectedRevision = 1,
        )

        assertEquals("", rig.achievements.updated.single().second.evidenceFileId)
        assertTrue(rig.media.calls.isEmpty())
    }
}
