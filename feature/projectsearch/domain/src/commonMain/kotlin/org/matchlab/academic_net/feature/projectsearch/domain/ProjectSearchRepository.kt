package org.matchlab.academic_net.feature.projectsearch.domain

import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest

/** Search of published projects; the server matches and orders them. The call throws on failure (e.g. no network). */
interface ProjectSearchRepository {
    /** What the server can filter by. */
    val capabilities: ProjectSearchCapabilities

    /**
     * One [page] of the projects the server finds for [query], in the server's order. API-GAP G53: the order, its
     * stability between pages and what the search includes (the viewer's own projects, other statuses) are the
     * server's and are not described.
     */
    suspend fun search(query: ProjectQuery, page: PageRequest): Page<FoundProject>
}

/**
 * The spheres the sphere filter offers. They belong to the projects feature, which implements this where the app is
 * assembled, so the two features do not depend on each other.
 */
interface SphereProvider {
    /** Every sphere a project can belong to, in the order the filter shows them. */
    suspend fun getSpheres(): List<Sphere>
}
