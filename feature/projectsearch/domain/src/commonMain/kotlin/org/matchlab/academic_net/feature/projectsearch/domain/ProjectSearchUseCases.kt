package org.matchlab.academic_net.feature.projectsearch.domain

import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.utils.suspendRunCatching

/** "Найти подходящий проект": one page of the server's answer; the search itself is the server's. */
class SearchProjectsUseCase(private val repository: ProjectSearchRepository) {
    suspend operator fun invoke(query: ProjectQuery, page: PageRequest): Result<Page<FoundProject>> =
        suspendRunCatching { repository.search(query, page) }
}

/** The spheres to pick from in the filters sheet. */
class GetSearchSpheresUseCase(private val provider: SphereProvider) {
    suspend operator fun invoke(): Result<List<Sphere>> =
        suspendRunCatching { provider.getSpheres() }
}
