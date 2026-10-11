package org.matchlab.academic_net.feature.projectsearch.data

import org.matchlab.academic_net.feature.projectsearch.domain.FoundProject
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery

/**
 * Where the projects come from. The server matches and orders them; the only implementation for
 * now is [MockProjectSearchDataSource], which plays the server. A remote one (with DTOs and their
 * mappers) replaces it when the backend appears.
 */
interface ProjectSearchDataSource {
    suspend fun search(query: ProjectQuery): List<FoundProject>
}
