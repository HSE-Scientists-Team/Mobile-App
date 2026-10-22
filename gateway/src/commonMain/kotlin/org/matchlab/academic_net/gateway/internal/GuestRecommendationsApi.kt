package org.matchlab.academic_net.gateway.internal

import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.gateway.PeopleSearchApi
import org.matchlab.academic_net.gateway.PeopleSearchParams
import org.matchlab.academic_net.gateway.ProjectCatalogApi
import org.matchlab.academic_net.gateway.ProjectSearchParams
import org.matchlab.academic_net.gateway.RecommendationsApi
import org.matchlab.academic_net.gateway.dto.PeopleListDto
import org.matchlab.academic_net.gateway.dto.ProjectListDto

/**
 * [RecommendationsApi] for a visitor without an account. The recommendations of the server are made for a user, so a
 * guest's feed is read from the calls that are open to everybody: the people open to collaboration, and the projects of
 * the catalog, both in the order of the server.
 */
internal class GuestRecommendationsApi(
    private val people: PeopleSearchApi,
    private val projects: ProjectCatalogApi,
) : RecommendationsApi {
    override suspend fun people(limit: Int): PeopleListDto {
        require(limit > 0) { "limit must be positive: $limit" }
        return people.search(PeopleSearchParams(openOnly = true), PageRequest(limit = limit))
    }

    override suspend fun projects(limit: Int): ProjectListDto {
        require(limit > 0) { "limit must be positive: $limit" }
        return projects.search(ProjectSearchParams(), PageRequest(limit = limit))
    }
}
