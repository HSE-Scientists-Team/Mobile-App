package org.matchlab.academic_net.feature.profile.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentId
import org.matchlab.academic_net.feature.profile.domain.EditOutcome
import org.matchlab.academic_net.feature.profile.domain.MyProfile
import org.matchlab.academic_net.feature.profile.domain.MyProfileRepository
import org.matchlab.academic_net.feature.profile.domain.PortfolioChange
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileCapabilities
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.ProfileDraft
import org.matchlab.academic_net.feature.profile.domain.ProfileRepository
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.ReviewsPage
import org.matchlab.academic_net.feature.profile.domain.UploadFile
import org.matchlab.academic_net.feature.profile.domain.UserProfile
import org.matchlab.academic_net.feature.profile.domain.UserReviews
import org.matchlab.academic_net.feature.profile.domain.UserWorks
import org.matchlab.academic_net.feature.profile.domain.VerificationResult
import org.matchlab.academic_net.feature.profile.domain.VerificationResultId
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.feature.profile.domain.WorkId
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * The one repository class of the feature: it implements both interfaces (the screens of other people's profiles
 * need only [ProfileRepository], the viewer's own screens need [MyProfileRepository]) over a [ProfileDataSource],
 * which only knows the resources of the server. What the repository adds is the client's work:
 *
 * - **A profile page is three requests** (the profile, the portfolio and the newest reviews), asked at once; a person
 *   the server does not know cancels the other two. A missing portfolio or reviews is an empty list. The person on the
 *   page is the one that was asked for, whatever `user_id` the answer has (API-GAP G55: the schema does not require it).
 * - **Reviews are paged** by [reviewsPageSize] (the API gives no total, so a full page means there may be more) and
 *   every review that was seen is kept, so that the screen of one review finds it (API-GAP G14).
 * - **The viewer's profile** is held as state: loaded when the first collector comes, loaded again by
 *   [refreshMyProfile] and after every change made through here. A change that went through but whose reload
 *   failed is still a success; the next refresh brings the state up to date.
 * - **A load never overwrites a change that is newer than it.** Every change that went through (a save, an added,
 *   edited, sent or deleted record) counts as a version of the state; a load that began at an earlier version asked the
 *   server before the change and its answer is dropped (a slow refresh from the screen must not bring the old name
 *   back after a save). The exception is a state that is still empty: the first answer is better than none.
 * - **Loads are shared.** A refresh that is asked for while another one is on its way, at the same version, waits for
 *   that one and has its result (or its failure) instead of asking the server three more requests; after a change the
 *   next refresh is a new load, as the one on its way is already older than the change.
 * - **Adding is two calls** of the API, create and send; the second is skipped when there is nothing to verify, and
 *   its failure leaves the record added but not sent.
 * - **Verification results** are empty (API-GAP G12): the server gives no statuses and no notifications.
 *
 * @param viewerId the signed-in user: their own id is served from the owner's resources (every record, the moderator's
 * comment), and the reviews they wrote are marked [Review.isByViewer].
 * @param reviewsPageSize how many reviews one page of the list has, 1..100.
 */
class ProfileRepositoryImpl(
    private val source: ProfileDataSource,
    private val viewerId: UserId,
    private val reviewsPageSize: Int = DefaultReviewsPageSize,
) : ProfileRepository, MyProfileRepository {
    init {
        require(reviewsPageSize in 1..MaxReviewsLimit) { "A page of reviews has 1..$MaxReviewsLimit: $reviewsPageSize" }
    }

    private val reviewsCache = ReviewsCache()

    // The viewer's profile as the screens observe it; null until the first load.
    private val mine = MutableStateFlow<MyProfile?>(null)

    // Guards the three below together with `mine`: a load applies its answer only if no change came in between.
    private val guard = Mutex()

    // How many changes went through here; a load that began at another number is older than the server now.
    private var version = 0

    // The load of the viewer's profile that is on its way, if there is one.
    private var loading: Load? = null

    /**
     * A load of the viewer's profile that began when [version] changes had been made.
     *
     * [finished] is the end of it: its result, or `null` when the caller that ran it was cancelled and so left no
     * answer, in which case the callers that waited for it run one of their own.
     */
    private class Load(val version: Int) {
        val finished = CompletableDeferred<Result<Unit>?>()
    }

    override val capabilities: ProfileCapabilities get() = source.capabilities

    // ---- other people's profiles (and the viewer's, as the owner sees it) ----

    override suspend fun getProfile(id: UserId): UserProfile? =
        if (id == viewerId) loadMine().profile else loadOther(id)

    override suspend fun getWorks(id: UserId): UserWorks? {
        val isViewer = id == viewerId
        val portfolio = if (isViewer) source.getMyPortfolio() else source.getPortfolio(id) ?: return null
        return UserWorks(portfolio.works, isViewer)
    }

    override suspend fun getUserReviews(id: UserId): UserReviews? = coroutineScope {
        val record = async { recordOf(id) }
        val page = async { source.getReviews(id, offset = 0, limit = reviewsPageSize) }
        val found = record.await()
        if (found == null) {
            page.cancel()
            return@coroutineScope null
        }
        val items = marked(page.await().orEmpty())
        reviewsCache.addAll(id, items)
        UserReviews(summary = found.reviews, firstPage = pageOf(items, offset = 0))
    }

    override suspend fun getReviewsPage(id: UserId, offset: Int): ReviewsPage? {
        val items = marked(source.getReviews(id, offset, reviewsPageSize) ?: return null)
        reviewsCache.addAll(id, items)
        return pageOf(items, offset)
    }

    override suspend fun getReview(userId: UserId, reviewId: ReviewId): Review? {
        reviewsCache.find(userId, reviewId)?.let { return it }

        // The screen was restored, nothing was loaded: look through the newest reviews, a page at a time.
        var offset = 0
        while (offset < MaxScannedReviews) {
            val items = marked(source.getReviews(userId, offset, ScanPageSize) ?: return null)
            reviewsCache.addAll(userId, items)
            items.firstOrNull { it.id == reviewId }?.let { return it }
            if (items.size < ScanPageSize) return null
            offset += ScanPageSize
        }
        return null
    }

    // ---- the viewer's own profile ----

    override fun observeMyProfile(): Flow<MyProfile> = flow {
        // A failure of the first load goes to the collector, which shows it and may collect again.
        if (mine.value == null) refreshMyProfile()
        emitAll(mine.filterNotNull())
    }

    override suspend fun refreshMyProfile() {
        while (true) {
            val (load, leads) = guard.withLock {
                val running = loading?.takeIf { it.version == version }
                if (running != null) running to false else Load(version).also { loading = it } to true
            }
            if (leads) return lead(load)
            // The load that was on its way answers this call too; if its caller gave up, a new load begins.
            val outcome = load.finished.await() ?: continue
            return outcome.getOrThrow()
        }
    }

    /**
     * Runs [load] in the caller's coroutine and applies its answer, unless a change went through since it began
     * (then the answer is older than the screens' state and is dropped) or unless there is no state yet. The failure
     * of the load is the caller's, and the other callers that wait for it get it too.
     */
    private suspend fun lead(load: Load) {
        var outcome: Result<Unit>? = null
        try {
            outcome = suspendRunCatching {
                val loaded = loadMine()
                guard.withLock {
                    if (version == load.version || mine.value == null) mine.value = loaded
                }
            }
            outcome.getOrThrow()
        } finally {
            // Whatever happened, the callers that wait get an answer, and the next refresh begins a new load.
            withContext(NonCancellable) {
                guard.withLock { if (loading === load) loading = null }
                load.finished.complete(outcome)
            }
        }
    }

    // The pictures are read by id of the profile, and the directory forgets the viewer when the profile is saved; the text
    // fields of the profile did not change, so the cache of this repository stays as it is.
    override suspend fun changeAvatar(photo: UploadFile) = source.changeAvatar(photo)

    override suspend fun updateProfile(draft: ProfileDraft): MyProfile {
        val record = source.updateMyProfile(draft).ofViewer()
        // The answer of the save is the whole profile, and the portfolio and the reviews did not change with it.
        val updated = withContext(NonCancellable) {
            guard.withLock {
                version++
                mine.value?.let { current ->
                    current.copy(
                        profile = current.profile.copy(
                            person = record.person,
                            about = record.about,
                            interests = record.interests,
                            skills = record.skills,
                            reviews = record.reviews,
                        ),
                    ).also { mine.value = it }
                }
            }
        }
        if (updated != null) return updated

        // Nothing was loaded yet, so there is nothing to lay the answer over: the whole profile is loaded.
        refreshMyProfile()
        return checkNotNull(mine.value)
    }

    override suspend fun createWork(draft: WorkDraft): PortfolioChange<PortfolioWork> {
        val created = written { source.createWork(draft) }
        val sent = draft.sendsForVerification && submitQuietly(VerificationSubject.Work(created.id))
        reloadQuietly()
        return PortfolioChange.Saved(created, sent)
    }

    override suspend fun createDocument(draft: DocumentDraft): PortfolioChange<ProfileDocument> {
        val created = written { source.createDocument(draft) }
        val sent = draft.sendsForVerification && submitQuietly(VerificationSubject.Document(created.id, created.kind))
        reloadQuietly()
        return PortfolioChange.Saved(created, sent)
    }

    override suspend fun getMyWork(id: WorkId): PortfolioWork? = source.getMyWork(id)

    override suspend fun getMyDocument(id: DocumentId): ProfileDocument? = source.getMyDocument(id)

    override suspend fun updateWork(id: WorkId, draft: WorkDraft, expectedRevision: Int): EditOutcome<PortfolioWork> =
        written { source.updateWork(id, draft, expectedRevision) }.also { reloadQuietly() }

    override suspend fun updateDocument(
        id: DocumentId,
        draft: DocumentDraft,
        expectedRevision: Int,
    ): EditOutcome<ProfileDocument> =
        written { source.updateDocument(id, draft, expectedRevision) }.also { reloadQuietly() }

    override suspend fun submitForVerification(subject: VerificationSubject): EditOutcome<Unit> =
        written { source.submit(subject) }.also { reloadQuietly() }

    override suspend fun delete(subject: VerificationSubject): EditOutcome<Unit> =
        written { source.delete(subject) }.also { reloadQuietly() }

    // API-GAP G12: the Gateway has no statuses of records and no notifications of a decision, so there is nothing to
    // tell; the screens ask the capability and do not show the results at all.
    override fun observeVerificationResults(): Flow<List<VerificationResult>> = flowOf(emptyList())

    override suspend fun markResultsSeen(ids: Set<VerificationResultId>) = Unit

    // ---- the parts ----

    /** The three parts of the profile page of somebody else, asked at once. */
    private suspend fun loadOther(id: UserId): UserProfile? = coroutineScope {
        val record = async { source.getProfile(id) }
        val portfolio = async { source.getPortfolio(id) }
        val reviews = async { source.getReviews(id, offset = 0, limit = PreviewReviewsCount) }
        val found = record.await()
        if (found == null) {
            portfolio.cancel()
            reviews.cancel()
            return@coroutineScope null
        }
        val latest = marked(reviews.await().orEmpty())
        reviewsCache.addAll(id, latest)
        assembleProfile(found.ofPerson(id), portfolio.await() ?: Portfolio.Empty, latest, isViewer = false)
    }

    /** The viewer's profile as the owner sees it, from the three requests at once. */
    private suspend fun loadMine(): MyProfile = coroutineScope {
        val record = async { source.getMyProfile() }
        val portfolio = async { source.getMyPortfolio() }
        val reviews = async { source.getReviews(viewerId, offset = 0, limit = PreviewReviewsCount) }
        val latest = marked(reviews.await().orEmpty())
        reviewsCache.addAll(viewerId, latest)
        assembleMyProfile(record.await()?.ofViewer() ?: ProfileRecord.empty(viewerId), portfolio.await(), latest)
    }

    /** The profile record of [id], for the numbers over their reviews; the viewer's own may be empty (a new account). */
    private suspend fun recordOf(id: UserId): ProfileRecord? =
        if (id == viewerId) source.getMyProfile() ?: ProfileRecord.empty(viewerId) else source.getProfile(id)

    /**
     * Runs a call that changes the viewer's data on the server. When it returns, the state has a new version: the
     * loads that began before are older than the server now and their answers are not applied (see [lead]). Counted
     * even if the caller is cancelled right now, as the server has the change in any case.
     */
    private suspend fun <T> written(write: suspend () -> T): T {
        val result = write()
        withContext(NonCancellable) { guard.withLock { version++ } }
        return result
    }

    /** A change went through; the viewer's profile state follows it, and a failure to follow is not the change's. */
    private suspend fun reloadQuietly() {
        suspendRunCatching { refreshMyProfile() }
    }

    /** Sends for verification after adding; `true` when the server took it. A failure leaves the record added. */
    private suspend fun submitQuietly(subject: VerificationSubject): Boolean =
        suspendRunCatching { written { source.submit(subject) } }.getOrNull() is EditOutcome.Done

    /** The record of the viewer's own profile, which is theirs by the id of the session, whatever the answer says. */
    private fun ProfileRecord.ofViewer(): ProfileRecord = ofPerson(viewerId)

    /**
     * The record as the profile of [id]: the person is the one that was asked for, whatever `user_id` the answer
     * carries (API-GAP G55: the schema does not require it, and an empty id would send the screens to a profile
     * that does not exist).
     */
    private fun ProfileRecord.ofPerson(id: UserId): ProfileRecord =
        copy(person = person.copy(user = person.user.copy(id = id)))

    private fun marked(reviews: List<Review>): List<Review> =
        reviews.map { it.copy(isByViewer = it.author.id == viewerId) }

    /** A full page may be followed by another, as long as the API can be asked for it. */
    private fun pageOf(items: List<Review>, offset: Int): ReviewsPage =
        ReviewsPage(items, hasMore = items.size == reviewsPageSize && offset + reviewsPageSize <= MaxReviewsOffset)

    private companion object {
        const val DefaultReviewsPageSize = 20

        /** The most reviews one request returns, and the largest offset the API accepts. */
        const val MaxReviewsLimit = 100
        const val MaxReviewsOffset = 10_000

        /** Looking for a review that was not loaded: [ScanPageSize] at a time, up to [MaxScannedReviews] of the newest. */
        const val ScanPageSize = 100
        const val MaxScannedReviews = 500
    }
}
