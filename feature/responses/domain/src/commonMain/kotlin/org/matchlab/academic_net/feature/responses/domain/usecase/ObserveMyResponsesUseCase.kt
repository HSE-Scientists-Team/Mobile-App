package org.matchlab.academic_net.feature.responses.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.matchlab.academic_net.feature.responses.domain.MyJoinsRepository
import org.matchlab.academic_net.feature.responses.domain.MyResponse
import org.matchlab.academic_net.feature.responses.domain.ResponsesProjectSource

/**
 * 15: the viewer's records with their projects. Each distinct project is asked once per emission of the list
 * (`getProject`, in parallel); a record whose project is `null` (the viewer may not see it) is left out; a failure of a
 * project read fails the flow. `null` until the list is read.
 */
class ObserveMyResponsesUseCase(
    private val joins: MyJoinsRepository,
    private val projects: ResponsesProjectSource,
) {
    operator fun invoke(): Flow<List<MyResponse>?> = joins.observeMyJoins().map { list ->
        if (list == null) return@map null
        val projectsById = coroutineScope {
            list.map { it.projectId }.distinct()
                .map { id -> async { id to projects.getProject(id) } }
                .awaitAll()
                .toMap()
        }
        list.mapNotNull { join ->
            val project = projectsById[join.projectId] ?: return@mapNotNull null
            MyResponse(join = join, project = project, role = project.roles.firstOrNull { it.id == join.roleId })
        }
    }
}
