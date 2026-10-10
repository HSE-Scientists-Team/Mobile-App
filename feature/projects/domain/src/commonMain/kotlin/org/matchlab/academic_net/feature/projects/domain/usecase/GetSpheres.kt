package org.matchlab.academic_net.feature.projects.domain.usecase

import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.utils.suspendRunCatching

/** "Выбрать сферу проекта": the spheres the user can pick from. */
class GetSpheresUseCase(private val repository: ProjectsRepository) {
    suspend operator fun invoke(): Result<List<Sphere>> =
        suspendRunCatching { repository.getSpheres() }
}
