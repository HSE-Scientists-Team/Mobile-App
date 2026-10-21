package org.matchlab.academic_net

import me.tatarka.inject.annotations.Inject
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.feature.projectsearch.domain.SphereProvider

/**
 * Tells the project search which spheres a project can belong to. The spheres are owned by the
 * projects feature; the two features do not know each other, so this adapter, living in the app,
 * connects them.
 */
@Inject
internal class ProjectsSphereProvider(
    private val projects: ProjectsRepository,
) : SphereProvider {
    override suspend fun getSpheres(): List<Sphere> = projects.getSpheres()
}
