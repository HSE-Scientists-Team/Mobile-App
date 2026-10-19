package org.matchlab.academic_net.feature.projects.domain

/**
 * What the projects feature can do with the server it talks to. The screens read the flags and hide, or switch off
 * with a word of explanation, what the server cannot do, instead of showing made-up data. The Gateway source turns all
 * four on: the values the API leaves open (`type`, `status`, `planned_*`, `action`) are defined by the app
 * (`docs/api-values.md`, decision of 2026-10-19).
 *
 * @property canCreate "Создать проект" is offered: `POST /projects` creates the project already published. When `false`
 *  there is no "Создать проект", and [ProjectsRepository.publishProject] throws `FeatureUnavailableException`.
 * @property canListMine "Я инициатор" lists the viewer's projects: the catalog `GET /projects`, the projects whose
 *  `owner_user_id` is the viewer. When `false` "Я инициатор" shows a message and asks nothing, and
 *  [ProjectsRepository.observeMyProjects] fails with `FeatureUnavailableException`.
 * @property canUnpublish 07c offers "Снять с публикации": `POST /projects/{id}/transitions` with the action "pause". When
 *  `false` there is no such action, and [ProjectsRepository.unpublishProject] throws `FeatureUnavailableException`.
 */
data class ProjectsCapabilities(
    val canCreate: Boolean,
    val canListMine: Boolean,
    val canUnpublish: Boolean,
    /**
     * 07c offers "Начать проект", "Завершить проект", "Отменить проект": `POST /projects/{id}/transitions` with the actions
     * "start", "complete", "cancel" (`docs/api-values.md`, not confirmed by the backend). When `false` there are none of them, and
     * [ProjectsRepository.changeLifecycle] throws `FeatureUnavailableException`.
     */
    val canChangeLifecycle: Boolean = false,
)
