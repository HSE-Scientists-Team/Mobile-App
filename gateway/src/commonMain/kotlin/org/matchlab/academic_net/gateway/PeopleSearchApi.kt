package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.gateway.dto.PeopleListDto

/**
 * The people search of the Gateway ("08 Поиск коллабораторов"). Every call throws `RemoteFailure` when it fails and lets
 * a `CancellationException` through, like [AccountApi].
 */
interface PeopleSearchApi {
    /**
     * `GET /api/v1/search/people`: one [page] of the people the server finds for [params]. `limit` and `offset` are always
     * sent (their bounds are those of [PageRequest]); a filter of [params] only when it restricts something.
     */
    suspend fun search(params: PeopleSearchParams, page: PageRequest): PeopleListDto
}

/**
 * The filters of `GET /api/v1/search/people` that the spec defines. Nothing that means "no restriction" is sent: a blank
 * string, a blank item of a list, an empty list, `false`. A list goes as the parameter repeated (`skill=a&skill=b`, the
 * OpenAPI default for an array in the query).
 *
 * API-GAP G31: there is no parameter for a role, and what [q] is matched against is not described.
 *
 * @property q `q`: the text of the search bar
 * @property university `university`
 * @property projectType `project_type`: the code of a project type, the `ProjectType.code` (`docs/api-values.md`); the
 *  parameter holds one value
 * @property skills `skill`, repeated
 * @property interests `interest`, repeated
 * @property myUniversity `my_university=true`
 * @property openOnly `open_only=true`
 * @property verifiedOnly `verified_only=true`
 * @property verifiedProjects `verified_projects=true`
 * @property publications `publications=true`
 */
data class PeopleSearchParams(
    val q: String? = null,
    val university: String? = null,
    val projectType: String? = null,
    val skills: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val myUniversity: Boolean = false,
    val openOnly: Boolean = false,
    val verifiedOnly: Boolean = false,
    val verifiedProjects: Boolean = false,
    val publications: Boolean = false,
)
