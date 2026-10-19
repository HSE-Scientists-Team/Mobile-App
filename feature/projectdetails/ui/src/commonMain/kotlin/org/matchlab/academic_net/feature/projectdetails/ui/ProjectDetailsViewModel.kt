package org.matchlab.academic_net.feature.projectdetails.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.projectdetails.domain.GetProjectDetailsUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.GetProjectTeamUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.LeaveProjectResult
import org.matchlab.academic_net.feature.projectdetails.domain.LeaveProjectUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.ObserveAppliedRolesUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.TeamMember
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetails as LoadedProject
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetailsResult

/** The initiator as the card under the project title shows them. */
@Immutable
data class InitiatorUiModel(
    val user: UserSummary,
    /** Stands in for the initiator's picture until profiles carry images. */
    val avatarKey: String,
    /** As the server wrote it; may be blank. */
    val university: String,
    /** Average of reviews, `null` while there are none. */
    val rating: Double?,
    val completedProjects: Int,
    /** `false` on the viewer's own project: nobody writes to themselves. */
    val canContact: Boolean,
)

/**
 * A person of "Команда" as the card shows them.
 *
 * @property avatarKey stands in for the person's picture until profiles carry images; unused without a profile
 * @property roleTitle the role the person works on, empty when unknown. The initiator's card has none: the screen
 *  writes "Инициатор" there ([isInitiator])
 * @property rating average of reviews, `null` while there are none or without a profile
 * @property hasProfile `false`: the profile could not be read, the card says "Без имени", has the neutral avatar, no
 *  stats line, no chat and is not clickable (04f)
 * @property canContact the chat button is offered: the person has a profile, is not the viewer and has an id
 */
@Immutable
data class TeamMemberUiModel(
    val user: UserSummary,
    val avatarKey: String,
    val roleTitle: String,
    val rating: Double?,
    val completedProjects: Int,
    val hasProfile: Boolean,
    val isInitiator: Boolean,
    val canContact: Boolean,
)

/** "Команда" of 04: hidden when the project has no members besides its initiator. */
sealed interface TeamUiState {
    /** The project has no members but its initiator: the block is not drawn. */
    data object Hidden : TeamUiState

    /** The people are being read (04e): the block has its title and "Загрузка…". */
    data object Loading : TeamUiState

    /** The initiator first, then the other members in the server's order. */
    data class Shown(val members: List<TeamMemberUiModel>) : TeamUiState
}

/**
 * A role of the project that has a free place, with the skills it needs.
 *
 * @property occupied places of the role that are taken, never more than [slots]
 * @property slots places of the role (API-GAP G49); the card says "N из M мест занято" when it is more than 1
 */
@Immutable
data class VacantRoleUiModel(
    val id: RoleId,
    val title: String,
    val skills: List<String>,
    val occupied: Int,
    val slots: Int,
)

/**
 * The project as the page shows it; carries no localized text, the screen builds the labels.
 *
 * @property type `null` when unknown (`docs/api-values.md`): the header has no chip
 * @property status `null` when unknown: the header has no chip
 * @property sphere the name of the sphere, `null` when unknown: the header has no chip
 * @property period `null` when unknown: the page has no card of the months
 * @property vacantRoles only the roles with a free place: the others are counted in [occupiedPlaces]
 * @property occupiedPlaces taken places of all roles, the "2" of "2 из 4 занято"
 * @property totalPlaces places of all roles, the "4" of "2 из 4 занято"
 * @property rolesCount how many roles the project has, whether they have free places or not; a project with none has
 *  no section of roles
 * @property team "Команда" under the tags; the page is shown first and the people arrive after it
 * @property canApply "Откликнуться" is offered on the free roles: the viewer is neither the initiator nor a member, and the
 *  project is recruiting or its status is unknown
 * @property appliedRoles the roles the viewer has applied to (waiting records): their button is "Вы откликнулись"
 * @property canLeave "Покинуть проект" is offered: the viewer is a member and not the initiator
 * @property canReport the flag in the app bar: anyone but the initiator may report the project
 */
@Immutable
data class ProjectDetailsUiModel(
    val title: String,
    val type: ProjectType?,
    val status: ProjectStatus?,
    val sphere: String?,
    val problem: String,
    val goal: String,
    val tags: List<String>,
    val initiator: InitiatorUiModel,
    val vacantRoles: List<VacantRoleUiModel>,
    val occupiedPlaces: Int,
    val totalPlaces: Int,
    val rolesCount: Int,
    val period: ProjectPeriod?,
    val team: TeamUiState = TeamUiState.Hidden,
    val canApply: Boolean = false,
    val appliedRoles: Set<RoleId> = emptySet(),
    val canLeave: Boolean = false,
    val canReport: Boolean = false,
)

/** What the project page shows: the loading, the answer of the server or the failure. */
sealed interface ProjectDetailsUiState {
    /** The project is being requested. */
    data object Loading : ProjectDetailsUiState

    /** The server has no such project for the viewer: unknown or taken off publication. */
    data object NotFound : ProjectDetailsUiState

    /** The request failed for [cause]; the screen says why (see `failureText`) and "Повторить" asks again. */
    data class Error(val cause: Throwable) : ProjectDetailsUiState

    /**
     * The project was found; [project] is everything the page shows.
     *
     * @property isLeaveConfirmVisible the confirmation 04y is open
     * @property isLeaving leaving is on its way (04g), or the page is being read again after it: the button shows an arc
     *  and a second leave cannot start
     * @property leaveFailure why leaving failed (04h and the banner of a failure); cleared by the next try
     * @property hasLeft the viewer has left the project: the banner 04i "Вы покинули проект" is at the top
     */
    data class Content(
        val project: ProjectDetailsUiModel,
        val isLeaveConfirmVisible: Boolean = false,
        val isLeaving: Boolean = false,
        val leaveFailure: LeaveFailure? = null,
        val hasLeft: Boolean = false,
    ) : ProjectDetailsUiState
}

/** Why "Покинуть проект" did not take the viewer out of the project. */
sealed interface LeaveFailure {
    /** The server refused (403, 409): "Действие недоступно в текущем состоянии проекта" (04h). */
    data object NotAllowed : LeaveFailure

    /** The request failed for [cause]; the screen says why (see `failureText`). */
    data class Failed(val cause: Throwable) : LeaveFailure
}

/**
 * "04 Проект": the page of a published project, read-only. The viewer's own project can be here too (the search may
 * find it, API-GAP G53): it is shown the same way, without the chat with its initiator. It loads the project once, when
 * it is created, and again on [onRetry], which works only after a failure.
 *
 * **Applying.** [ProjectDetailsUiModel.canApply] says whether the free roles have "Откликнуться"; the roles the viewer has
 * applied to come from [observeAppliedRoles] (the data layer's list of the viewer's records) and are
 * [ProjectDetailsUiModel.appliedRoles]: "Вы откликнулись" (04x), which follows a new application by itself.
 *
 * **Leaving.** A member who is not the initiator can leave: [onLeaveClick] opens 04y, [onLeaveConfirm] sends it under the
 * guard [ProjectDetailsUiState.Content.isLeaving]. `Left` reads the page again (the viewer is not on the team now) and
 * shows the banner 04i; `NotAllowed` is 04h; `NotFound` is the screen "Проект не найден"; a failure is told by its kind.
 *
 * **Team.** Once the project is found the page is shown, and "Команда" is [TeamUiState.Loading] when the project has
 * members besides its initiator (else [TeamUiState.Hidden]) while [getProjectTeam] reads their profiles; then
 * [TeamUiState.Shown] with the initiator first. The team never fails: a person whose profile could not be read is a
 * card "Без имени". [onRetry] after a failure reads the page again and the team with it.
 */
class ProjectDetailsViewModel(
    private val projectId: ProjectId,
    private val getProjectDetails: GetProjectDetailsUseCase,
    private val getProjectTeam: GetProjectTeamUseCase,
    observeAppliedRoles: ObserveAppliedRolesUseCase,
    private val leaveProject: LeaveProjectUseCase,
) : ViewModel() {
    private val state = MutableStateFlow<ProjectDetailsUiState>(ProjectDetailsUiState.Loading)
    private val applied = observeAppliedRoles(projectId)
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val uiState: StateFlow<ProjectDetailsUiState> = combine(state, applied) { current, appliedRoles ->
        if (current is ProjectDetailsUiState.Content) {
            current.copy(project = current.project.copy(appliedRoles = appliedRoles))
        } else {
            current
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), ProjectDetailsUiState.Loading)

    init {
        load()
    }

    /** "Покинуть проект": opens the confirmation 04y. */
    fun onLeaveClick() = updateContent {
        if (it.project.canLeave && !it.hasLeft && !it.isLeaving) {
            it.copy(isLeaveConfirmVisible = true, leaveFailure = null)
        } else {
            it
        }
    }

    fun onLeaveDismiss() = updateContent { it.copy(isLeaveConfirmVisible = false) }

    /** "Покинуть" of 04y: works once per dialog (the button can be tapped again while the dialog fades out). */
    fun onLeaveConfirm() {
        var started = false
        updateContent {
            started = it.isLeaveConfirmVisible && !it.isLeaving
            if (started) it.copy(isLeaveConfirmVisible = false, isLeaving = true, leaveFailure = null) else it
        }
        if (!started) return
        viewModelScope.launch {
            when (val result = leaveProject(projectId)) {
                // The viewer is not on the team now: the page is read again, and the banner stays while it does.
                LeaveProjectResult.Left -> {
                    updateContent { it.copy(hasLeft = true) }
                    reload()
                }
                LeaveProjectResult.NotAllowed ->
                    updateContent { it.copy(isLeaving = false, leaveFailure = LeaveFailure.NotAllowed) }
                LeaveProjectResult.NotFound -> state.value = ProjectDetailsUiState.NotFound
                is LeaveProjectResult.Failure ->
                    updateContent { it.copy(isLeaving = false, leaveFailure = LeaveFailure.Failed(result.cause)) }
            }
        }
    }

    private inline fun updateContent(crossinline transform: (ProjectDetailsUiState.Content) -> ProjectDetailsUiState.Content) {
        state.update { current -> if (current is ProjectDetailsUiState.Content) transform(current) else current }
    }

    /** Reads the page again after leaving; the banner of leaving and the screen stay, whatever the answer is. */
    private suspend fun reload() {
        when (val result = getProjectDetails(projectId)) {
            is ProjectDetailsResult.Found -> {
                val details = result.details
                val hasTeam = details.hasTeam()
                updateContent {
                    it.copy(
                        project = details.toUiModel().copy(team = if (hasTeam) TeamUiState.Loading else TeamUiState.Hidden),
                        isLeaving = false,
                    )
                }
                if (hasTeam) loadTeam(details)
            }
            ProjectDetailsResult.NotFound -> state.value = ProjectDetailsUiState.NotFound
            // The viewer has left; what the page shows is out of date, but the screen is not taken away from them.
            is ProjectDetailsResult.Failure -> updateContent { it.copy(isLeaving = false) }
        }
    }

    /** "Повторить" after a failed load; a request that is still running is not doubled. */
    fun onRetry() {
        if (state.value !is ProjectDetailsUiState.Error) return
        state.value = ProjectDetailsUiState.Loading
        load()
    }

    private fun load() {
        viewModelScope.launch {
            when (val result = getProjectDetails(projectId)) {
                is ProjectDetailsResult.Found -> {
                    val details = result.details
                    val hasTeam = details.hasTeam()
                    state.value = ProjectDetailsUiState.Content(
                        details.toUiModel().copy(team = if (hasTeam) TeamUiState.Loading else TeamUiState.Hidden),
                    )
                    if (hasTeam) loadTeam(details)
                }
                ProjectDetailsResult.NotFound -> state.value = ProjectDetailsUiState.NotFound
                is ProjectDetailsResult.Failure -> state.value = ProjectDetailsUiState.Error(result.cause)
            }
        }
    }

    /** Reads the people and puts them under the page; a page that is gone or was replaced meanwhile is left alone. */
    private suspend fun loadTeam(details: LoadedProject) {
        val members = getProjectTeam(details.project)
        state.update { current ->
            if (current !is ProjectDetailsUiState.Content) {
                current
            } else {
                val team = if (members.isEmpty()) TeamUiState.Hidden else TeamUiState.Shown(details.teamOf(members))
                current.copy(project = current.project.copy(team = team))
            }
        }
    }
}

/** The project has members other than its initiator (the team is asked for only then). */
private fun LoadedProject.hasTeam(): Boolean = project.members.any { it.userId != project.initiator.id }

/** The initiator first (from what the page already read about them), then [members] as the use case returned them. */
private fun LoadedProject.teamOf(members: List<TeamMember>): List<TeamMemberUiModel> {
    val initiatorId = project.initiator.id
    // The owner's profile is read together with the project; the page carries no flag of it, but a profile that could
    // not be read leaves the initiator without a name (the same card as a member without a profile, 04f).
    val initiatorHasProfile = project.initiator.name.isNotBlank()
    val initiatorCard = TeamMemberUiModel(
        user = project.initiator,
        avatarKey = initiatorId.value,
        roleTitle = "",
        rating = if (initiatorHasProfile) initiator.rating else null,
        completedProjects = if (initiatorHasProfile) initiator.completedProjects else 0,
        hasProfile = initiatorHasProfile,
        isInitiator = true,
        canContact = initiatorHasProfile && !project.viewerIsInitiator && initiatorId.value.isNotBlank(),
    )
    val others = members.map { member ->
        val person = member.person
        TeamMemberUiModel(
            user = person.user,
            avatarKey = person.user.id.value,
            roleTitle = member.roleTitle,
            rating = person.rating,
            completedProjects = person.completedProjects,
            hasProfile = person.hasProfile,
            isInitiator = false,
            canContact = person.hasProfile && !member.isViewer && person.user.id.value.isNotBlank(),
        )
    }
    return listOf(initiatorCard) + others
}

private fun LoadedProject.toUiModel(): ProjectDetailsUiModel = ProjectDetailsUiModel(
    title = project.title,
    type = project.type,
    status = project.status,
    sphere = project.sphere?.name,
    problem = project.problem,
    goal = project.goal,
    tags = project.tags,
    initiator = InitiatorUiModel(
        user = project.initiator,
        avatarKey = project.initiator.id.value,
        university = initiator.university,
        rating = initiator.rating,
        completedProjects = initiator.completedProjects,
        canContact = !project.viewerIsInitiator,
    ),
    vacantRoles = project.vacantRoles.map { role ->
        VacantRoleUiModel(
            id = role.id,
            title = role.title,
            skills = role.skills,
            occupied = minOf(role.occupied, role.slots),
            slots = role.slots,
        )
    },
    occupiedPlaces = project.occupiedPlaces,
    totalPlaces = project.totalPlaces,
    rolesCount = project.roles.size,
    period = project.period,
    canApply = !project.viewerIsInitiator && !project.viewerIsMember &&
        (project.status == null || project.status == ProjectStatus.Recruiting),
    canLeave = project.viewerIsMember && !project.viewerIsInitiator,
    canReport = !project.viewerIsInitiator,
)

private const val StopTimeoutMillis = 5_000L
