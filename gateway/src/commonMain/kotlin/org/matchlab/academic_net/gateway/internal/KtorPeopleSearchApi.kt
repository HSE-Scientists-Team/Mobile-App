package org.matchlab.academic_net.gateway.internal

import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.gateway.PeopleSearchApi
import org.matchlab.academic_net.gateway.PeopleSearchParams
import org.matchlab.academic_net.gateway.dto.PeopleListDto

/** [PeopleSearchApi] over the client of a session. */
internal class KtorPeopleSearchApi(private val requests: ApiRequests) : PeopleSearchApi {
    override suspend fun search(params: PeopleSearchParams, page: PageRequest): PeopleListDto =
        requests.get(
            "search/people",
            query = params.query(page),
            repeatedQuery = params.repeatedQuery(),
        )

    /**
     * The parameters with one value. `limit` and `offset` always go (the bounds are those of [PageRequest]); a text
     * (the project type too) only if it is not blank and a switch only when it is on, since "off" is the same as not
     * being asked for.
     */
    private fun PeopleSearchParams.query(page: PageRequest): Map<String, String?> = buildMap {
        put("limit", page.limit.toString())
        put("offset", page.offset.toString())
        q?.takeIf { it.isNotBlank() }?.let { put("q", it) }
        university?.takeIf { it.isNotBlank() }?.let { put("university", it) }
        projectType?.takeIf { it.isNotBlank() }?.let { put("project_type", it) }
        if (myUniversity) put("my_university", "true")
        if (openOnly) put("open_only", "true")
        if (verifiedOnly) put("verified_only", "true")
        if (verifiedProjects) put("verified_projects", "true")
        if (publications) put("publications", "true")
    }

    /** The arrays of the query: each item is a parameter of its own, a blank one is left out. */
    private fun PeopleSearchParams.repeatedQuery(): Map<String, List<String>> = mapOf(
        "skill" to skills.filter { it.isNotBlank() },
        "interest" to interests.filter { it.isNotBlank() },
    )
}
