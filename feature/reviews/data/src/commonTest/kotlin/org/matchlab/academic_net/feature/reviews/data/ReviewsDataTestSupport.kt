package org.matchlab.academic_net.feature.reviews.data

import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraft
import org.matchlab.academic_net.feature.reviews.domain.ReviewableProject
import org.matchlab.academic_net.gateway.ProjectCatalogApi
import org.matchlab.academic_net.gateway.ProjectSearchParams
import org.matchlab.academic_net.gateway.ReviewsApi
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectInputDto
import org.matchlab.academic_net.gateway.dto.ProjectListDto
import org.matchlab.academic_net.gateway.dto.ProjectMemberDto
import org.matchlab.academic_net.gateway.dto.UserReviewDto
import org.matchlab.academic_net.gateway.dto.UserReviewInputDto
import org.matchlab.academic_net.gateway.dto.UserReviewListDto

// What the tests of the reviews data layer share: the two APIs of the Gateway as fakes that page like the server, and the DTOs
// the server would send.

internal const val ViewerId = "viewer-1"
internal const val TargetId = "target-1"

/** The completion time most tests use: 2025-06-15. */
internal const val June2025 = 1_750_000_000L

internal fun member(userId: String, status: String = "active") = ProjectMemberDto(userId = userId, status = status)

/**
 * A completed project of the viewer's list. By default the owner is the viewer and the target is an active member, so it is a
 * shared project; a test changes only what it is about.
 */
internal fun projectDto(
    id: String,
    owner: String = ViewerId,
    title: String = "Project $id",
    members: List<ProjectMemberDto> = listOf(member(TargetId)),
    completedAt: Long = June2025,
) = ProjectDto(
    id = id,
    ownerUserId = owner,
    details = ProjectInputDto(title = title),
    status = "completed",
    revision = 1,
    members = members,
    completedAtUnix = completedAt,
)

/** A review the viewer wrote about the target (or [author] about someone) in [projectId]. */
internal fun reviewDto(projectId: String, author: String = ViewerId, id: String = "rev-$author-$projectId") =
    UserReviewDto(id = id, authorUserId = author, projectId = projectId, targetUserId = TargetId, rating = 5)

/**
 * [ProjectCatalogApi] with the viewer's [owned] and [joined] projects, served the way the server pages them: `limit` and `offset`
 * of the request, `total` the size of the list (or what [reportedTotal] says). Only `list` is used by the reviews feature.
 */
internal class FakeProjectCatalog : ProjectCatalogApi {
    val owned = mutableListOf<ProjectDto>()
    val joined = mutableListOf<ProjectDto>()
    val listCalls = mutableListOf<Pair<ProjectSearchParams, PageRequest>>()

    /** The `total` the server reports for a list of [size] projects; the real size by default. */
    var reportedTotal: (size: Int) -> Long = { it.toLong() }

    /** Answers instead of the lists, when a test wants to script the server by hand. */
    var onList: (suspend (ProjectSearchParams, PageRequest) -> ProjectListDto)? = null

    fun callsOf(mine: String) = listCalls.filter { it.first.mine == mine }.map { it.second }

    override suspend fun list(params: ProjectSearchParams, page: PageRequest): ProjectListDto {
        listCalls += params to page
        onList?.let { return it(params, page) }
        val source = when (params.mine) {
            "owner" -> owned
            "participant" -> joined
            else -> throw AssertionError("The list of the reviews feature always names `mine`: $params")
        }
        return ProjectListDto(source.drop(page.offset).take(page.limit), reportedTotal(source.size))
    }

    override suspend fun search(params: ProjectSearchParams, page: PageRequest): ProjectListDto = throw AssertionError("search")

    override suspend fun get(id: String): ProjectDto = throw AssertionError("get($id)")
}

/**
 * [ReviewsApi] with the reviews [about] each person, paged like the server (`limit`, `offset`); records every read and every
 * review written.
 */
internal class ScriptedReviewsApi : ReviewsApi {
    val about = mutableMapOf<String, MutableList<UserReviewDto>>()
    val reads = mutableListOf<Triple<String, Int, Int>>()
    val created = mutableListOf<UserReviewInputDto>()

    /** Answers instead of [about], when a test wants to script the server by hand. */
    var onUserReviews: (suspend (userId: String, limit: Int, offset: Int) -> UserReviewListDto)? = null
    var onCreate: suspend (UserReviewInputDto) -> UserReviewDto = { UserReviewDto(id = "created", rating = it.rating) }

    override suspend fun userReviews(userId: String, limit: Int, offset: Int): UserReviewListDto {
        reads += Triple(userId, limit, offset)
        onUserReviews?.let { return it(userId, limit, offset) }
        return UserReviewListDto(about[userId].orEmpty().drop(offset).take(limit))
    }

    override suspend fun create(input: UserReviewInputDto): UserReviewDto {
        created += input
        return onCreate(input)
    }
}

/** [ReviewsDataSource] that records what the repository asked and answers as the test says. */
internal class FakeReviewsDataSource : ReviewsDataSource {
    val projectCalls = mutableListOf<UserId>()
    val sent = mutableListOf<Pair<UserId, ReviewDraft>>()
    var onGetReviewableProjects: suspend (UserId) -> List<ReviewableProject> = { emptyList() }
    var onSendReview: suspend (UserId, ReviewDraft) -> Unit = { _, _ -> }

    override suspend fun getReviewableProjects(targetId: UserId): List<ReviewableProject> {
        projectCalls += targetId
        return onGetReviewableProjects(targetId)
    }

    override suspend fun sendReview(targetId: UserId, draft: ReviewDraft) {
        sent += targetId to draft
        onSendReview(targetId, draft)
    }
}
