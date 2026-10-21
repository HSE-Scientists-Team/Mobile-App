package org.matchlab.academic_net.feature.reviews.data

import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraft
import org.matchlab.academic_net.feature.reviews.domain.ReviewRefusal
import org.matchlab.academic_net.feature.reviews.domain.ReviewableProject
import org.matchlab.academic_net.gateway.ProjectCatalogApi
import org.matchlab.academic_net.gateway.ProjectSearchParams
import org.matchlab.academic_net.gateway.ReviewsApi
import org.matchlab.academic_net.gateway.contract.ProjectMineFilter
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.UserReviewInputDto
import org.matchlab.academic_net.gateway.mapping.pageOf

/**
 * [ReviewsDataSource] over the Gateway. The server has no "projects I can review this person in", so the client puts
 * the list together; what the server decides (that both were on the team, that the project is completed, that there is
 * one review per project and pair) is decided again when the review is sent.
 *
 * | Member | What it does |
 * |---|---|
 * | `getReviewableProjects` | three reads run at once: `GET /projects?mine=owner&status=completed` and `GET /projects?mine=participant&status=completed` (pages of 100, at most 10 pages each, the server's order) and `GET /users/{target}/reviews` (pages of 100). A project is shared when the target is its `owner_user_id` or one of its `members` that has not `left` or been `removed`; a project whose `members` are empty and whose owner is not the target is not offered (see below). A project the viewer already reviewed the target in (a review of the target whose `author_user_id` is the viewer) is dropped. The rest: no blank `id`, once each, the most recently completed first (`completed_at_unix`, no date last), equal dates in the server's order. A failure of any read fails the call; nothing is cached |
 * | `sendReview` | `POST /users/me/reviews` with the trimmed draft; 409 is [ReviewRefusal.AlreadyReviewed]; 403, 404 and 400 are [ReviewRefusal.NotAllowed]; any other failure is thrown as the `RemoteFailure` it is. No retry: a repeat of a lost answer is refused by the server with a 409 |
 *
 * **Members.** The list of projects gives the team (`members`) of the projects the viewer owns or is an active member of,
 * which is every project asked for here, and only its active members (backend `sanitize`, PR #16). A list that comes with
 * no team for a project the viewer does not own cannot tell whether the target is in it, and that project is not offered
 * (API-GAP, the check by `GET /projects/{id}` is not made). The reviews of the target are read from their first page on
 * and not more than the server lets the offset go (`offset` ≤ 10 000), the API has no filter by the author
 * (API-GAP): a person with more reviews than that may be offered a project they have already reviewed, and the server
 * refuses it with a 409.
 *
 * @param viewerId the user of the session: the author of the reviews.
 */
class GatewayReviewsDataSource(
    private val catalog: ProjectCatalogApi,
    private val reviews: ReviewsApi,
    private val viewerId: UserId,
) : ReviewsDataSource {
    override suspend fun getReviewableProjects(targetId: UserId): List<ReviewableProject> {
        // A person cannot review themself (the server answers 400), so there is nothing to ask for.
        if (targetId == viewerId) return emptyList()
        val (own, joined, reviewed) = coroutineScope {
            val own = async { completedProjects(ProjectMineFilter.Owner) }
            val joined = async { completedProjects(ProjectMineFilter.Participant) }
            val reviewed = async { projectsReviewedBy(viewerId, about = targetId) }
            Triple(own.await(), joined.await(), reviewed.await())
        }
        return (own + joined)
            .filter { it.id.isNotBlank() && it.id !in reviewed && it.isSharedWith(targetId) }
            .distinctBy { it.id }
            .map { ReviewableProject(ProjectId(it.id), it.details.title, it.completedAtUnix.toInstantOrNull()) }
            // sortedByDescending is stable: equal dates stay in the server's order.
            .sortedByDescending { it.completedAt ?: Instant.DISTANT_PAST }
    }

    override suspend fun sendReview(targetId: UserId, draft: ReviewDraft) {
        val normalized = draft.normalized()
        val projectId = requireNotNull(normalized.projectId) { "The draft has no project" }
        require(normalized.validate().isEmpty()) { "The draft is not valid: ${normalized.validate()}" }
        val input = UserReviewInputDto(
            projectId = projectId.value,
            targetUserId = targetId.value,
            title = normalized.title,
            text = normalized.text,
            rating = normalized.rating,
        )
        try {
            reviews.create(input)
        } catch (failure: RemoteFailure.Http) {
            throw when (failure.status) {
                CONFLICT -> ReviewRefusal.AlreadyReviewed(failure)
                in NOT_ALLOWED -> ReviewRefusal.NotAllowed(failure)
                else -> failure
            }
        }
    }

    /** The completed projects of the viewer that [mine] picks, all pages up to [MaxProjectPages]. */
    private suspend fun completedProjects(mine: String): List<ProjectDto> {
        val params = ProjectSearchParams(mine = mine, status = ProjectStatus.Finished.code)
        val projects = ArrayList<ProjectDto>()
        var request: PageRequest? = PageRequest(limit = PageRequest.MaxLimit)
        var pages = 0
        while (request != null && pages < MaxProjectPages) {
            val answer = catalog.list(params, request)
            projects += answer.items
            // pageOf is the one place that knows when a list is over (an empty page, the total, the largest offset).
            request = pageOf(answer.items, answer.items.size, answer.total, request).next()
            pages++
        }
        return projects
    }

    /** The ids of the projects in which [author] has reviewed [about]: every page of the reviews of [about] up to the largest offset. */
    private suspend fun projectsReviewedBy(author: UserId, about: UserId): Set<String> {
        val found = HashSet<String>()
        var offset = 0
        while (offset <= ReviewsApi.MaxOffset) {
            val items = reviews.userReviews(about.value, ReviewsApi.MaxLimit, offset).items
            items.filter { it.authorUserId == author.value && it.projectId.isNotBlank() }.mapTo(found) { it.projectId }
            // A page that is not full is the last one.
            if (items.size < ReviewsApi.MaxLimit) break
            offset += items.size
        }
        return found
    }

    private fun ProjectDto.isSharedWith(userId: UserId): Boolean =
        ownerUserId == userId.value ||
            members.any { it.userId == userId.value && it.status !in InactiveMemberStatuses }

    private fun Long.toInstantOrNull(): Instant? = if (this > 0) Instant.fromEpochSeconds(this) else null

    private companion object {
        const val CONFLICT = 409

        /** 400 (the server's own check of the project and the people), 403 (not a member, not completed) and 404 (not there for the viewer). */
        val NOT_ALLOWED = setOf(400, 403, 404)

        /** The pages of 100 that one list of the viewer's projects may take: 1000 projects, like the list "Я инициатор". */
        const val MaxProjectPages = 10

        /** `ProjectMember.status` of a person who is no longer on the team (`docs/openapi.yaml`, v2.6.0). */
        val InactiveMemberStatuses = setOf("left", "removed")
    }
}
