package org.matchlab.academic_net.feature.feed.data

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.feed.domain.RecommendedProject
import org.matchlab.academic_net.gateway.RecommendationsApi
import org.matchlab.academic_net.gateway.mapping.ProjectAssembler
import org.matchlab.academic_net.gateway.mapping.toCollaborator

/**
 * [FeedDataSource] over `GET /api/v1/recommendations/people?limit` and `GET /api/v1/recommendations/projects?limit`;
 * failures are thrown as `RemoteFailure`.
 *
 * The people. API-GAP G32: the server answered with random people open to collaboration (its description now speaks of a
 * ranking from a periodic calculation with a fallback list; the app takes the order as it comes). API-GAP G33: the API does
 * not say whether the viewer can be recommended to themselves, or one person twice, nor what the bounds of `limit` are; the
 * records without a `user_id`, the viewer and the repeats are therefore dropped here, and fewer than [limit] people may be
 * shown.
 *
 * The projects. The server checks every project again before it answers (visibility, status, membership, free places), so
 * nothing is filtered by status or owner here. The [assembler] reads them and looks up their initiators (API-GAP G47): a
 * project without an `id` or an `owner_user_id` is not shown, a repeat of a project is shown once, and the university of the
 * initiator is the line under their name. A failure of the initiators' request, other than "no such person", fails the
 * call.
 *
 * @param assembler turns the server's projects into the app's, with their initiators.
 * @param viewerId the signed-in user, who is not offered to themselves.
 * @param limit how many people and projects to ask for; positive.
 */
class GatewayFeedDataSource(
    private val api: RecommendationsApi,
    private val assembler: ProjectAssembler,
    private val viewerId: UserId,
    private val limit: Int = DefaultLimit,
) : FeedDataSource {
    init {
        require(limit > 0) { "The feed must ask for at least one person: $limit" }
    }

    override suspend fun getRecommendedPeople(): List<Collaborator> =
        api.people(limit).items
            .mapNotNull { it.toCollaborator() }
            // API-GAP G33: the API does not say whether the viewer can be recommended to themselves, or twice.
            .filter { it.user.id != viewerId }
            .distinctBy { it.user.id }

    override suspend fun getRecommendedProjects(): List<RecommendedProject> =
        assembler.assemble(api.projects(limit).items)
            .map { RecommendedProject(it.project, it.owner?.details?.university.orEmpty().trim()) }
            .distinctBy { it.project.id }

    companion object {
        /** How many people (and projects) the feed asks for: the block is a short list, not a search. */
        const val DefaultLimit = 10
    }
}
