package org.matchlab.academic_net.feature.projectsearch.domain

/**
 * What the project search can ask the server it talks to; the filters sheet hides the rest. The Gateway source turns both
 * flags on: the values of `type` and `duration` are defined by the app (`docs/api-values.md`, decision of 2026-10-19).
 *
 * @property typeFilter the "Тип проекта" group of 03 is offered and [ProjectFilters.types] is sent as `type`. When `false`
 *  the group is hidden and the types are never sent.
 * @property durationFilter the "Сроки" group of 03 is offered and [ProjectFilters.durations] is sent as `duration`. When
 *  `false` the group is hidden and the durations are never sent.
 */
data class ProjectSearchCapabilities(
    val typeFilter: Boolean,
    val durationFilter: Boolean,
)
