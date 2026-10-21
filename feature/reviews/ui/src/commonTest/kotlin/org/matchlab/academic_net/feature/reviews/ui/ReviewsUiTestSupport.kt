package org.matchlab.academic_net.feature.reviews.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.reviews.domain.GetReviewableProjectsUseCase
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraft
import org.matchlab.academic_net.feature.reviews.domain.ReviewableProject
import org.matchlab.academic_net.feature.reviews.domain.ReviewsRepository
import org.matchlab.academic_net.feature.reviews.domain.SendReviewUseCase
import kotlin.time.Instant

// What the view-model tests of the review form share: the main dispatcher of a test, a fake repository on the interface of the
// domain (the form is tested against the domain, never against the data layer) and models to build answers from.

@OptIn(ExperimentalCoroutinesApi::class)
fun installMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

@OptIn(ExperimentalCoroutinesApi::class)
fun uninstallMainDispatcher() = Dispatchers.resetMain()

/** The person the form is about. */
val Target = UserId("target")

/** A project the viewer and the target completed together. */
fun project(id: String, title: String = "Проект $id", completedAt: Instant? = Instant.fromEpochSeconds(1_750_000_000)) =
    ReviewableProject(ProjectId(id), title, completedAt)

/** A repository whose answers the test scripts; it records what the form asked and sent. */
class FakeReviewsRepository : ReviewsRepository {
    var onGetProjects: suspend (UserId) -> List<ReviewableProject> = { emptyList() }
    var onSend: suspend (UserId, ReviewDraft) -> Unit = { _, _ -> }

    val projectRequests = mutableListOf<UserId>()
    val sent = mutableListOf<Pair<UserId, ReviewDraft>>()

    override suspend fun getReviewableProjects(targetId: UserId): List<ReviewableProject> {
        projectRequests += targetId
        return onGetProjects(targetId)
    }

    override suspend fun sendReview(targetId: UserId, draft: ReviewDraft) {
        sent += targetId to draft
        onSend(targetId, draft)
    }
}

/** The form of [Target] over [repository], with the real use cases (the checks and the trimming are the domain's). */
fun reviewForm(repository: FakeReviewsRepository, targetId: UserId = Target) =
    ReviewFormViewModel(targetId, GetReviewableProjectsUseCase(repository), SendReviewUseCase(repository))

/** A string of [count] code points outside the Basic Multilingual Plane (two UTF-16 units each). */
fun emojis(count: Int): String = "😀".repeat(count)
