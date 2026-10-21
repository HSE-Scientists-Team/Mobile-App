package org.matchlab.academic_net.feature.projects.domain.usecase

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.domain.ProjectLifecycle
import org.matchlab.academic_net.feature.projects.domain.ProjectLifecycleAction
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.utils.suspendRunCatching

/** How a move of a project through its life ended; the screen has a state for each case. */
sealed interface ChangeLifecycleResult {
    /** The server moved the project; [project] is the project as it is now. */
    data class Changed(val project: Project) : ChangeLifecycleResult

    /** Not offered for the status (nothing was sent), or the server refused it (409; the project was read again). */
    data object NotAllowed : ChangeLifecycleResult

    /** There is no such project, or the viewer may not see it. */
    data object NotFound : ChangeLifecycleResult

    /** The request failed (no network, a server error), or [cause] is `FeatureUnavailableException`. */
    data class Failure(val cause: Throwable) : ChangeLifecycleResult
}

/**
 * "Начать", "Завершить", "Отменить проект" of 07c. Takes the project from the cache (`getProject`; none is
 * [ChangeLifecycleResult.NotFound], a failure is [ChangeLifecycleResult.Failure]); an action that is not in
 * [ProjectLifecycle.actionsFor] its status is [ChangeLifecycleResult.NotAllowed] without a request; then
 * `changeLifecycle`: [ProjectNotFoundException] is NotFound, [ProjectConflictException] is NotAllowed, anything else is
 * Failure. Without `ProjectsCapabilities.canChangeLifecycle` the screen does not offer the moves.
 */
class ChangeProjectLifecycleUseCase(private val repository: ProjectsRepository) {
    suspend operator fun invoke(id: ProjectId, action: ProjectLifecycleAction): ChangeLifecycleResult {
        val project = suspendRunCatching { repository.getProject(id) }
            .getOrElse { return ChangeLifecycleResult.Failure(it) }
            ?: return ChangeLifecycleResult.NotFound
        if (action !in ProjectLifecycle.actionsFor(project.status)) return ChangeLifecycleResult.NotAllowed

        return suspendRunCatching { repository.changeLifecycle(id, action) }.fold(
            onSuccess = { ChangeLifecycleResult.Changed(it) },
            onFailure = {
                when (it) {
                    is ProjectNotFoundException -> ChangeLifecycleResult.NotFound
                    is ProjectConflictException -> ChangeLifecycleResult.NotAllowed
                    else -> ChangeLifecycleResult.Failure(it)
                }
            },
        )
    }
}
