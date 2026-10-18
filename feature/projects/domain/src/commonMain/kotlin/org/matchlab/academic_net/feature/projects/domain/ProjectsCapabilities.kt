package org.matchlab.academic_net.feature.projects.domain

/**
 * What the projects feature can do with the server it talks to. The screens read the flags and hide, or switch off
 * with a word of explanation, what the server cannot do, instead of showing made-up data. Every flag is closed by
 * gaps of `docs/api-gaps.md`: when the backend fills them, the data source sets the flag and implements the call.
 *
 * @property canCreate API-GAP G40, G43: `POST /projects` requires `type` (no list of values) and `planned_start` /
 *  `planned_end` (no format). When `false` there is no "Создать проект", and [ProjectsRepository.publishProject] throws
 *  `FeatureUnavailableException`.
 * @property canListMine API-GAP G44: `mine` of `GET /projects` has no list of values. When `false` "Я инициатор" shows
 *  a message and asks nothing; [ProjectsRepository.observeMyProjects] fails with `FeatureUnavailableException`.
 * @property canUnpublish API-GAP G42 (and G41): `action` of the transitions has no list of values, and the status of a
 *  project is unknown. When `false` 07c has no "Снять с публикации"; [ProjectsRepository.unpublishProject] throws
 *  `FeatureUnavailableException`.
 */
data class ProjectsCapabilities(
    val canCreate: Boolean,
    val canListMine: Boolean,
    val canUnpublish: Boolean,
)
