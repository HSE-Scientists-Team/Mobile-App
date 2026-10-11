package org.matchlab.academic_net.feature.projectsearch.domain

import org.matchlab.academic_net.common.model.Sphere

/**
 * Search of the published projects of other people. The server matches and orders the projects.
 * The call throws on failure (e.g. no network); the use case turns that into a result.
 */
interface ProjectSearchRepository {
    /** Projects for [query] in the server's order (the newest first). */
    suspend fun search(query: ProjectQuery): List<FoundProject>
}

/**
 * The spheres the sphere filter offers. They belong to the projects feature, which implements
 * this where the app is assembled, so the two features do not depend on each other.
 */
interface SphereProvider {
    /** Every sphere a project can belong to, in the order the filter shows them. */
    suspend fun getSpheres(): List<Sphere>
}
