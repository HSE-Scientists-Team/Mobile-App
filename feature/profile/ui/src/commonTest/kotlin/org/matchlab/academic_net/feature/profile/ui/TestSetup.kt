package org.matchlab.academic_net.feature.profile.ui

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.profile.data.ProfileDataSource
import org.matchlab.academic_net.feature.profile.data.ProfileRepositoryImpl
import org.matchlab.academic_net.feature.profile.data.mock.MockProfileDataSource
import org.matchlab.academic_net.feature.profile.domain.AboutDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.MyProfile
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.UserProfile
import org.matchlab.academic_net.feature.profile.domain.UserReviews
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.feature.profile.domain.usecase.AddDocumentUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.AddWorkUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetReviewUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserProfileUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserReviewsUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserWorksUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.MarkResultsSeenUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveHasUnseenResultsUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveMyProfileUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveVerificationResultsUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.UpdateAboutUseCase

/** "Now" of the tests: the moment of the clock of the mock server and of the screens. */
val TestNow: Instant = Instant.parse("2026-10-12T10:00:00Z")
val TestZone: TimeZone = TimeZone.UTC

val Me = UserId("user-me")
val Arjun = UserId("user-arjun")

private val FixedClock = object : Clock {
    override fun now(): Instant = TestNow
}

/**
 * The mock server with a fixed clock; moderation runs in the test's scope. Without latency by
 * default, so the results are there at once; pass [latencyMillis] to see the gaps of a real network.
 */
fun TestScope.mockSource(latencyMillis: Long = 0): MockProfileDataSource =
    MockProfileDataSource(scope = backgroundScope, latencyMillis = latencyMillis, clock = FixedClock)

/** Use cases over a data source, like the app wires them. */
class TestGraph(val source: ProfileDataSource) {
    private val repository = ProfileRepositoryImpl(source)

    val getUserProfile = GetUserProfileUseCase(repository)
    val getUserWorks = GetUserWorksUseCase(repository)
    val getUserReviews = GetUserReviewsUseCase(repository)
    val getReview = GetReviewUseCase(repository)
    val observeMyProfile = ObserveMyProfileUseCase(repository)
    val updateAbout = UpdateAboutUseCase(repository)
    val addWork = AddWorkUseCase(repository)
    val addDocument = AddDocumentUseCase(repository)
    val observeVerificationResults = ObserveVerificationResultsUseCase(repository)
    val observeHasUnseenResults = ObserveHasUnseenResultsUseCase(repository)
    val markResultsSeen = MarkResultsSeenUseCase(repository)

    private val dependencies = ProfileUiDependencies(
        getUserProfile = getUserProfile,
        getUserWorks = getUserWorks,
        getUserReviews = getUserReviews,
        getReview = getReview,
        observeMyProfile = observeMyProfile,
        updateAbout = updateAbout,
        addWork = addWork,
        addDocument = addDocument,
        observeVerificationResults = observeVerificationResults,
        observeHasUnseenResults = observeHasUnseenResults,
        markResultsSeen = markResultsSeen,
        clock = FixedClock,
        timeZone = { TestZone },
    )

    /** One instance, as in the app: the screen and the tab badge share its visibility. */
    fun dependencies(): ProfileUiDependencies = dependencies
}

/** The mock server that can be switched to fail like a dead network. */
class FlakySource(private val delegate: ProfileDataSource) : ProfileDataSource by delegate {
    var failReads = false
    var failWrites = false

    /** How many times the profile of somebody was requested. */
    var profileRequests = 0
        private set

    class NetworkDown : RuntimeException("network is down")

    override suspend fun getProfile(id: UserId): UserProfile? {
        profileRequests++
        return if (failReads) throw NetworkDown() else delegate.getProfile(id)
    }

    override suspend fun getWorks(id: UserId): List<PortfolioWork>? =
        if (failReads) throw NetworkDown() else delegate.getWorks(id)

    override suspend fun getReviews(id: UserId): UserReviews? =
        if (failReads) throw NetworkDown() else delegate.getReviews(id)

    override suspend fun getReview(id: ReviewId): Review? =
        if (failReads) throw NetworkDown() else delegate.getReview(id)

    override fun observeMyProfile(): Flow<MyProfile> =
        if (failReads) flow { throw NetworkDown() } else delegate.observeMyProfile()

    override suspend fun updateAbout(draft: AboutDraft): MyProfile =
        if (failWrites) throw NetworkDown() else delegate.updateAbout(draft)

    override suspend fun addWork(draft: WorkDraft): PortfolioWork =
        if (failWrites) throw NetworkDown() else delegate.addWork(draft)

    override suspend fun addDocument(draft: DocumentDraft): ProfileDocument =
        if (failWrites) throw NetworkDown() else delegate.addDocument(draft)
}

/** Keeps the flow hot, so `stateIn(WhileSubscribed)` states are live; stops when the test ends. */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> TestScope.keepCollecting(flow: Flow<T>) {
    val scope: CoroutineScope = backgroundScope
    scope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun installMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

@OptIn(ExperimentalCoroutinesApi::class)
fun uninstallMainDispatcher() = Dispatchers.resetMain()
