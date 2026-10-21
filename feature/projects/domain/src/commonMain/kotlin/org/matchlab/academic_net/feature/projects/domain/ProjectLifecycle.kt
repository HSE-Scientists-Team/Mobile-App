package org.matchlab.academic_net.feature.projects.domain

import org.matchlab.academic_net.common.model.ProjectStatus

/** A move of a project through its life that its initiator makes in 07c. */
enum class ProjectLifecycleAction { Start, Complete, Cancel }

/**
 * Which moves the app offers for a status. API-GAP G62: the API does not describe which transition is allowed from which
 * status, so this table is the app's assumption; the server is the judge (a refused move is a 409). An unknown status
 * (`null`) offers everything.
 *
 * | status | actions | unpublish |
 * |---|---|---|
 * | `null` | Start, Complete, Cancel | yes |
 * | Recruiting | Start, Cancel | yes |
 * | Unpublished | Start, Cancel | no |
 * | Started | Complete, Cancel | no |
 * | Finished, Cancelled | none | no |
 */
object ProjectLifecycle {
    /** The actions for [status], in the order 07c lists them: Start, Complete, Cancel. */
    fun actionsFor(status: ProjectStatus?): List<ProjectLifecycleAction> = when (status) {
        null -> listOf(ProjectLifecycleAction.Start, ProjectLifecycleAction.Complete, ProjectLifecycleAction.Cancel)
        ProjectStatus.Recruiting, ProjectStatus.Unpublished ->
            listOf(ProjectLifecycleAction.Start, ProjectLifecycleAction.Cancel)
        ProjectStatus.Started -> listOf(ProjectLifecycleAction.Complete, ProjectLifecycleAction.Cancel)
        ProjectStatus.Finished, ProjectStatus.Cancelled -> emptyList()
    }

    /** "Снять с публикации" is offered: the status is unknown or [ProjectStatus.Recruiting]. */
    fun canUnpublish(status: ProjectStatus?): Boolean = status == null || status == ProjectStatus.Recruiting
}
