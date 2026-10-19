package org.matchlab.academic_net.feature.projectdetails.data

import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.projectdetails.domain.InitiatorInfo
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetails
import org.matchlab.academic_net.gateway.ProjectCatalogApi
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.mapping.ProjectAssembler
import org.matchlab.academic_net.gateway.mapping.ratingOrNull

/**
 * [ProjectDetailsDataSource] over `GET /api/v1/projects/{id}` and the public profile of the project's owner.
 *
 * | Answer | Result |
 * |---|---|
 * | 200 | the project and what the page adds about its initiator ([toInitiatorInfo]) |
 * | 400, 403, 404 | `null`: the viewer may not see such a project |
 * | 200 without an `id` or an `owner_user_id` | `null`: the app cannot show it |
 * | any other status, a failure of the network, a failure of the owner's request (other than "no such person") | thrown as the `RemoteFailure` it is |
 *
 * An owner without a profile the viewer may see is not a reason to fail: the page shows the initiator without a
 * name, a university and numbers. API-GAP G47: the project names its owner by id only, so the owner is looked up
 * separately. The type, the sphere, the status and the months of the project are read from the codes the app defines
 * (`docs/api-values.md`); a value the app has no code for is `null` and does not hide the project.
 *
 * @param assembler turns the server's project into the app's, with its owner.
 */
class GatewayProjectDetailsDataSource(
    private val api: ProjectCatalogApi,
    private val assembler: ProjectAssembler,
) : ProjectDetailsDataSource {
    override suspend fun getProjectDetails(id: ProjectId): ProjectDetails? {
        val dto = try {
            api.get(id.value)
        } catch (failure: RemoteFailure.Http) {
            if (failure.status in NOT_VISIBLE) return null else throw failure
        }
        val assembled = assembler.assemble(listOf(dto)).firstOrNull() ?: return null
        return ProjectDetails(assembled.project, assembled.owner.toInitiatorInfo())
    }

    private companion object {
        /** 400 (not a project id), 403 (not allowed to see it) and 404 (unknown): the project is not there for the viewer. */
        val NOT_VISIBLE = setOf(400, 403, 404)
    }
}

/**
 * The facts about an initiator that the project page adds to the project: the university (blank when unknown), the
 * rating of the reviews and the number of finished projects. API-GAP G20: a person without reviews has no rating
 * (`null`), whatever number the server sent.
 */
internal fun UserProfileDto?.toInitiatorInfo(): InitiatorInfo = InitiatorInfo(
    university = this?.details?.university.orEmpty().trim(),
    rating = this?.ratingOrNull(), // API-GAP G20
    completedProjects = this?.completedProjectCount?.coerceAtLeast(0) ?: 0,
)
