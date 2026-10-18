package org.matchlab.academic_net.feature.projectsearch.domain

/**
 * What the project search can ask the server it talks to; the filters sheet hides the rest. Closing a gap of
 * `docs/api-gaps.md` is setting its flag in the data source and sending the parameter.
 *
 * @property typeFilter API-GAP G40: `type` has no list of values. When `false` the "Тип проекта" group of 03 is hidden
 *  and [ProjectFilters.types] is never sent.
 * @property durationFilter API-GAP G45: `duration` has no list of values. When `false` the "Сроки" group of 03 is
 *  hidden and [ProjectFilters.durations] is never sent.
 */
data class ProjectSearchCapabilities(
    val typeFilter: Boolean,
    val durationFilter: Boolean,
)
