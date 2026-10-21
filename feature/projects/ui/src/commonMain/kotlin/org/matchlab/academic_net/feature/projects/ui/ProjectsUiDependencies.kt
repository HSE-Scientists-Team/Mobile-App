package org.matchlab.academic_net.feature.projects.ui

import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.usecase.AddRoleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ChangeProjectLifecycleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.GetSpheresUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.GetParticipatingProjectsUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.LoadProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveMyProjectsUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.PublishProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.RemoveMemberUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.RemoveRoleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UnpublishProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UpdateProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UpdateRoleUseCase

/**
 * What the screens of the projects feature need from the outside; the app creates it once and hands it to every
 * `…Route`.
 *
 * @param capabilities what the source of the data can do (see `docs/api-values.md`). Without
 *  [ProjectsCapabilities.canListMine] "Я инициатор" shows a message instead of the list, without
 *  [ProjectsCapabilities.canCreate] there is no "Создать проект", without [ProjectsCapabilities.canUnpublish] 07c has
 *  no "Снять с публикации", without [ProjectsCapabilities.canChangeLifecycle] it has no "Начать", "Завершить" and
 *  "Отменить проект".
 */
class ProjectsUiDependencies(
    val observeMyProjects: ObserveMyProjectsUseCase,
    val observeProject: ObserveProjectUseCase,
    val loadProject: LoadProjectUseCase,
    val publishProject: PublishProjectUseCase,
    val unpublishProject: UnpublishProjectUseCase,
    val addRole: AddRoleUseCase,
    val getSpheres: GetSpheresUseCase,
    val changeLifecycle: ChangeProjectLifecycleUseCase,
    val updateProject: UpdateProjectUseCase,
    val updateRole: UpdateRoleUseCase,
    val removeRole: RemoveRoleUseCase,
    val removeMember: RemoveMemberUseCase,
    val getParticipatingProjects: GetParticipatingProjectsUseCase,
    val capabilities: ProjectsCapabilities,
)
