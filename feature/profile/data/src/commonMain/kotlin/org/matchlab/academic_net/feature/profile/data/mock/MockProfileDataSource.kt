package org.matchlab.academic_net.feature.profile.data.mock

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.profile.data.ProfileDataSource
import org.matchlab.academic_net.feature.profile.domain.AboutDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentKind
import org.matchlab.academic_net.feature.profile.domain.MyProfile
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.UserProfile
import org.matchlab.academic_net.feature.profile.domain.UserReviews
import org.matchlab.academic_net.feature.profile.domain.VerificationOutcome
import org.matchlab.academic_net.feature.profile.domain.VerificationResult
import org.matchlab.academic_net.feature.profile.domain.VerificationResultId
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.mockserver.MockCheckState
import org.matchlab.academic_net.mockserver.MockPeople
import org.matchlab.academic_net.mockserver.MockServerDocument
import org.matchlab.academic_net.mockserver.MockServerWork
import org.matchlab.academic_net.utils.IdGenerator
import org.matchlab.academic_net.utils.UuidIdGenerator

/**
 * In-memory stand-in for the profile server. It answers from the [people] tables of the mock
 * server (the same people the catalog of projects and the other mock data sources have) after
 * [latencyMillis], like a network call, and keeps the changes of the viewer ([viewerId]) in
 * memory.
 *
 * Rules of this mock (the contract for the backend):
 * - an unknown person id is "not found" (`null`), and so is an unknown review id;
 * - other people see only whether a work or a document is confirmed: a pending or a rejected one
 *   looks unconfirmed; the owner sees the real state and the queue of what is being checked;
 * - a person is verified when at least one education document is confirmed (it follows the
 *   moderation, so the viewer becomes verified when their diploma is confirmed);
 * - a profile previews the 3 newest works and the 2 newest reviews; works are newest first,
 *   documents are education first, then the newest year first, reviews are newest project first;
 * - the numbers of the reviews (count, positive ones, average) are counted from the real list;
 * - the server labels the source of a work from its link (`doi.org/X` is "DOI X", `github.com` is
 *   "GitHub", `arxiv.org` is "arXiv", any other site is its domain);
 * - a work with neither a link nor a proof is [VerificationStatus.Unconfirmed] at once, and
 *   nobody checks it; everything else is `Pending`, and after [moderationDelay] the [moderator]
 *   decides: the work becomes `Verified` or `Rejected`, a document is always `Verified`;
 * - every decision makes an unseen [VerificationResult]; the viewer marks them as seen;
 * - the viewer starts as in the design "10 Мой профиль": a pending certificate (it is confirmed
 *   after [moderationDelay], to show the notification) and a rejected work with an unseen result.
 *
 * Moderation runs in [scope] (the app's scope, `backgroundScope` in tests), so the mock does not
 * make its own threads and the tests move its time with the test scheduler.
 */
class MockProfileDataSource(
    private val scope: CoroutineScope,
    people: MockPeople = MockPeople.default,
    private val moderator: MockModerator = MockModerator(),
    private val moderationDelay: Duration = DefaultModerationDelay,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = UuidIdGenerator,
    private val viewerId: UserId = UserId("user-me"),
    private val latencyMillis: Long = DefaultLatencyMillis,
) : ProfileDataSource {
    /** The tables; the viewer's changes replace rows in them. */
    private val database = MutableStateFlow(people)

    private val results = MutableStateFlow(seedResults(people))

    init {
        // What was already sent to moderation before the app started is decided in due time too.
        people.documentsOf(viewerId).filter { it.state == MockCheckState.Pending }.forEach { document ->
            moderate(Submitted.Document(document.id))
        }
        people.worksOf(viewerId).filter { it.state == MockCheckState.Pending }.forEach { work ->
            moderate(Submitted.Work(work.id))
        }
    }

    override suspend fun getProfile(id: UserId): UserProfile? {
        simulateNetwork()
        return database.value.userProfile(id, viewerId)
    }

    override suspend fun getWorks(id: UserId): List<PortfolioWork>? {
        simulateNetwork()
        val people = database.value
        if (people.person(id) == null) return null
        return people.userWorks(id, forOwner = id == viewerId)
    }

    override suspend fun getReviews(id: UserId): UserReviews? {
        simulateNetwork()
        val people = database.value
        if (people.person(id) == null) return null
        return people.userReviews(id)
    }

    override suspend fun getReview(id: ReviewId): Review? {
        simulateNetwork()
        val people = database.value
        return people.reviews.firstOrNull { it.id == id.value }?.toDomain(people)
    }

    override fun observeMyProfile(): Flow<MyProfile> =
        database
            .map { it.myProfile(viewerId) }
            .onStart { simulateNetwork() }

    override suspend fun updateAbout(draft: AboutDraft): MyProfile {
        simulateNetwork()
        return database.updateAndGet { people ->
            MockPeople(
                persons = people.persons.map { person ->
                    if (person.id != viewerId) {
                        person
                    } else {
                        person.copy(
                            about = draft.about,
                            interestTypes = draft.interestTypes,
                            interests = draft.interests,
                            skills = draft.skills,
                        )
                    }
                },
                works = people.works,
                documents = people.documents,
                reviews = people.reviews,
            )
        }.myProfile(viewerId)
    }

    override suspend fun addWork(draft: WorkDraft): PortfolioWork {
        simulateNetwork()
        val work = MockServerWork(
            id = ids.newId(),
            ownerId = viewerId,
            type = draft.type,
            title = draft.title,
            description = draft.description,
            link = draft.link.ifBlank { null },
            hasProof = draft.proof != null,
            state = if (draft.sendsForVerification) MockCheckState.Pending else MockCheckState.Unconfirmed,
        )
        database.update { people ->
            MockPeople(people.persons, listOf(work) + people.works, people.documents, people.reviews)
        }
        if (work.state == MockCheckState.Pending) moderate(Submitted.Work(work.id))
        return work.toDomain(forOwner = true)
    }

    override suspend fun addDocument(draft: DocumentDraft): ProfileDocument {
        simulateNetwork()
        val document = MockServerDocument(
            id = ids.newId(),
            ownerId = viewerId,
            isEducation = draft.kind == DocumentKind.Education,
            title = draft.title,
            issuer = draft.issuer,
            year = draft.yearNumber,
            state = MockCheckState.Pending,
        )
        database.update { people ->
            MockPeople(people.persons, people.works, listOf(document) + people.documents, people.reviews)
        }
        moderate(Submitted.Document(document.id))
        return document.toDomain(forOwner = true)
    }

    override fun observeVerificationResults(): Flow<List<VerificationResult>> = results

    override suspend fun markResultsSeen(ids: Set<VerificationResultId>) {
        simulateNetwork()
        results.update { all -> all.map { if (it.id in ids) it.copy(isSeen = true) else it } }
    }

    /** A work or a document of the viewer that was sent to moderation. */
    private sealed interface Submitted {
        data class Work(val id: String) : Submitted

        data class Document(val id: String) : Submitted
    }

    /** After [moderationDelay] the moderator decides about the submitted thing, if it still waits. */
    private fun moderate(submitted: Submitted) {
        scope.launch {
            delay(moderationDelay)
            decide(submitted)
        }
    }

    private fun decide(submitted: Submitted) {
        var result: VerificationResult? = null
        database.update { people ->
            result = null
            when (submitted) {
                is Submitted.Work -> {
                    val work = people.works.firstOrNull { it.id == submitted.id }
                    if (work == null || work.state != MockCheckState.Pending) return@update people

                    val outcome = moderator.decide(ModerationSubmission.Work(work.link, work.hasProof))
                    result = newResult(work.subject(), work.title, outcome)
                    MockPeople(
                        people.persons,
                        people.works.map { if (it.id == work.id) it.copy(state = outcome.toState()) else it },
                        people.documents,
                        people.reviews,
                    )
                }

                is Submitted.Document -> {
                    val document = people.documents.firstOrNull { it.id == submitted.id }
                    if (document == null || document.state != MockCheckState.Pending) return@update people

                    val outcome = moderator.decide(ModerationSubmission.Document)
                    result = newResult(document.subject(), document.title, outcome)
                    MockPeople(
                        people.persons,
                        people.works,
                        people.documents.map { if (it.id == document.id) it.copy(state = outcome.toState()) else it },
                        people.reviews,
                    )
                }
            }
        }
        result?.let { decision -> results.update { listOf(decision) + it } }
    }

    private fun newResult(subject: VerificationSubject, title: String, outcome: VerificationOutcome) =
        VerificationResult(
            id = VerificationResultId(ids.newId()),
            subject = subject,
            subjectTitle = title,
            outcome = outcome,
            decidedAt = clock.now(),
            isSeen = false,
        )

    /** What the viewer was already told before the app started: their rejected works and documents. */
    private fun seedResults(people: MockPeople): List<VerificationResult> {
        val decidedAt = clock.now() - SeedResultAge
        fun rejected(rowId: String, subject: VerificationSubject, title: String) = VerificationResult(
            id = VerificationResultId("result-$rowId"),
            subject = subject,
            subjectTitle = title,
            outcome = VerificationOutcome.Rejected,
            decidedAt = decidedAt,
            isSeen = false,
        )

        val documents = people.documentsOf(viewerId)
            .filter { it.state == MockCheckState.Rejected }
            .map { rejected(it.id, it.subject(), it.title) }
        val works = people.worksOf(viewerId)
            .filter { it.state == MockCheckState.Rejected }
            .map { rejected(it.id, it.subject(), it.title) }
        return documents + works
    }

    private suspend fun simulateNetwork() {
        if (latencyMillis > 0) delay(latencyMillis)
    }

    private fun VerificationOutcome.toState() = when (this) {
        VerificationOutcome.Verified -> MockCheckState.Verified
        VerificationOutcome.Rejected -> MockCheckState.Rejected
    }

    private companion object {
        const val DefaultLatencyMillis = 300L

        /** How long a moderator takes in the demo: long enough to see "На проверке". */
        val DefaultModerationDelay = 8.seconds

        /** How long ago the seeded rejections happened. */
        val SeedResultAge = 1.hours
    }
}
