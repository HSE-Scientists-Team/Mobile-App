package org.matchlab.academic_net.feature.projectsearch.domain

import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.utils.suspendRunCatching

/** "Найти подходящий проект": the search is the server's, this only asks. */
class SearchProjectsUseCase(private val repository: ProjectSearchRepository) {
    suspend operator fun invoke(query: ProjectQuery): Result<List<FoundProject>> =
        suspendRunCatching { repository.search(query) }
}

/** The spheres to pick from in the filters sheet. */
class GetSearchSpheresUseCase(private val provider: SphereProvider) {
    suspend operator fun invoke(): Result<List<Sphere>> =
        suspendRunCatching { provider.getSpheres() }
}
