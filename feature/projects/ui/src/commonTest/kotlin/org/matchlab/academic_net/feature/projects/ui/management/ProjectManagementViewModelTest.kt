package org.matchlab.academic_net.feature.projects.ui.management

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.RoleMember
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.domain.ProjectLimits
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.feature.projects.domain.RoleDraftError
import org.matchlab.academic_net.feature.projects.domain.usecase.AddRoleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.LoadProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UnpublishProjectUseCase
import org.matchlab.academic_net.feature.projects.ui.FakeProjectsRepository
import org.matchlab.academic_net.feature.projects.ui.Gate
import org.matchlab.academic_net.feature.projects.ui.caps
import org.matchlab.academic_net.feature.projects.ui.installMainDispatcher
import org.matchlab.academic_net.feature.projects.ui.keepCollecting
import org.matchlab.academic_net.feature.projects.ui.project
import org.matchlab.academic_net.feature.projects.ui.role
import org.matchlab.academic_net.feature.projects.ui.uninstallMainDispatcher

/**
 * "07 Управление проектом": the project is read when the screen opens, the cache is what is shown, and what the server
 * cannot do is not offered. The fake data layer keeps a cache and a "server" that `loadProject` reads into it.
 */
class ProjectManagementViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val id = ProjectId("p1")
    private val repository = FakeProjectsRepository(caps(canUnpublish = true))
    private val cause = IllegalStateException("offline")

    private fun TestScope.viewModel(capabilities: ProjectsCapabilities = repository.capabilities): ProjectManagementViewModel {
        val viewModel = ProjectManagementViewModel(
            projectId = id,
            observeProject = ObserveProjectUseCase(repository),
            loadProject = LoadProjectUseCase(repository),
            unpublishProject = UnpublishProjectUseCase(repository),
            addRole = AddRoleUseCase(repository),
            capabilities = capabilities,
        )
        keepCollecting(viewModel.uiState)
        return viewModel
    }

    private val ProjectManagementViewModel.state get() = uiState.value

    private fun serve(
        status: ProjectStatus? = ProjectStatus.Recruiting,
        roles: List<org.matchlab.academic_net.common.model.Role> = emptyList(),
        viewerIsInitiator: Boolean = true,
    ) {
        repository.server[id] = project("p1", status = status, roles = roles, viewerIsInitiator = viewerIsInitiator)
    }

    // ---- reading ----

    @Test
    fun theProjectIsReadWhenTheScreenOpensAndShown() = runTest {
        serve(roles = listOf(role("r1", "ML Engineer", skills = listOf("Python"))))

        val viewModel = viewModel()

        assertEquals(listOf(id), repository.loads)
        with(viewModel.state) {
            assertFalse(isLoading)
            assertNull(loadFailure)
            assertEquals("Проект p1", project?.title)
            assertEquals(listOf("ML Engineer"), project?.roles?.map { it.title })
        }
    }

    @Test
    fun theScreenIsLoadingUntilTheProjectComes() = runTest {
        serve()
        repository.loadGate = Gate()

        val viewModel = viewModel()

        with(viewModel.state) {
            assertTrue(isLoading)
            assertNull(project)
        }
        repository.loadGate!!.open()
        assertFalse(viewModel.state.isLoading)
        assertNotNull(viewModel.state.project)
    }

    @Test
    fun aProjectReadBeforeIsOnTheScreenAtOnceWhileItIsReadAgain() = runTest {
        serve()
        repository.cache.value = mapOf(id to project("p1", title = "Из кэша"))
        repository.loadGate = Gate()

        val viewModel = viewModel()

        assertFalse(viewModel.state.isLoading)
        assertEquals("Из кэша", viewModel.state.project?.title)
        repository.loadGate!!.open()
        assertEquals("Проект p1", viewModel.state.project?.title, "refreshed in the background")
    }

    @Test
    fun noSuchProjectIsNeitherLoadingNorAnError() = runTest {
        val viewModel = viewModel()

        with(viewModel.state) {
            assertNull(project)
            assertNull(loadFailure)
            assertFalse(isLoading, "the screen says \"Проект не найден\"")
        }
    }

    @Test
    fun theServersNoSuchProjectBeatsWhatTheCacheStillHolds() = runTest {
        repository.cache.value = mapOf(id to project("p1"))

        val viewModel = viewModel()

        assertNull(viewModel.state.project)
        assertFalse(viewModel.state.isLoading)
        assertNull(viewModel.state.loadFailure)
    }

    @Test
    fun aFailureWithNothingToShowIsTheFullScreenError() = runTest {
        repository.loadFailure = cause

        val viewModel = viewModel()

        with(viewModel.state) {
            assertSame(cause, loadFailure)
            assertFalse(isLoading)
            assertNull(project)
        }
    }

    @Test
    fun aFailureWithAProjectOnTheScreenChangesNothing() = runTest {
        repository.cache.value = mapOf(id to project("p1", title = "Из кэша"))
        repository.loadFailure = cause

        val viewModel = viewModel()

        with(viewModel.state) {
            assertNull(loadFailure)
            assertEquals("Из кэша", project?.title)
        }
    }

    @Test
    fun theRetryReadsTheProjectAgain() = runTest {
        repository.loadFailure = cause
        val viewModel = viewModel()
        serve()
        repository.loadFailure = null

        viewModel.onRetry()

        assertEquals(2, repository.loads.size)
        with(viewModel.state) {
            assertNull(loadFailure)
            assertEquals("Проект p1", project?.title)
        }
    }

    @Test
    fun theRetryShowsTheProgressIndicatorWhileItWaits() = runTest {
        repository.loadFailure = cause
        val viewModel = viewModel()
        serve()
        repository.loadFailure = null
        repository.loadGate = Gate()

        viewModel.onRetry()

        assertTrue(viewModel.state.isLoading)
        assertNull(viewModel.state.loadFailure)
    }

    @Test
    fun aSecondRetryWhileTheReadIsOnItsWayAsksNothing() = runTest {
        repository.loadFailure = cause
        val viewModel = viewModel()
        repository.loadFailure = null
        repository.loadGate = Gate()

        viewModel.onRetry()
        viewModel.onRetry()
        viewModel.onRetry()

        assertEquals(2, repository.loads.size, "the first read and one retry")
    }

    @Test
    fun theRetryWithoutAFailureDoesNothing() = runTest {
        serve()
        val viewModel = viewModel()

        viewModel.onRetry()

        assertEquals(1, repository.loads.size)
    }

    @Test
    fun aFailedRetryShowsTheErrorAgain() = runTest {
        repository.loadFailure = cause
        val viewModel = viewModel()

        viewModel.onRetry()

        assertSame(cause, viewModel.state.loadFailure)
        assertEquals(2, repository.loads.size)
    }

    // ---- unpublishing ----

    @Test
    fun unpublishingIsOfferedWhileTheProjectIsNotAlreadyUnpublishedAndTheSourceCanDoIt() = runTest {
        serve(ProjectStatus.Recruiting)
        assertTrue(viewModel(caps(canUnpublish = true)).state.project!!.canUnpublish)
        assertFalse(viewModel(caps(canUnpublish = false)).state.project!!.canUnpublish, "the source cannot")

        serve(ProjectStatus.Unpublished)
        assertFalse(viewModel(caps(canUnpublish = true)).state.project!!.canUnpublish, "already off publication")
    }

    @Test
    fun aProjectOfAnUnknownStatusIsOfferedForUnpublishingToo() = runTest {
        serve(status = null)

        assertTrue(viewModel(caps(canUnpublish = true)).state.project!!.canUnpublish)
        assertFalse(viewModel(caps(canUnpublish = false)).state.project!!.canUnpublish)
    }

    @Test
    fun unpublishingIsNotOfferedToAViewerWhoIsNotTheInitiator() = runTest {
        for (status in listOf(ProjectStatus.Recruiting, ProjectStatus.Unpublished, null)) {
            serve(status, viewerIsInitiator = false)

            assertFalse(viewModel(caps(canUnpublish = true)).state.project!!.canUnpublish, "status $status")
        }
    }

    @Test
    fun theUnknownStatusBecomesUnpublishedAfterTheConfirmation() = runTest {
        serve(status = null)
        val viewModel = viewModel()

        viewModel.onUnpublishClick()
        viewModel.onUnpublishConfirm()

        assertEquals(listOf(id), repository.unpublishes)
        assertEquals(ProjectStatus.Unpublished, viewModel.state.project?.status)
        assertFalse(viewModel.state.project!!.canUnpublish, "nothing to take off any more")
    }

    @Test
    fun theDialogOpensAndCloses() = runTest {
        serve()
        val viewModel = viewModel()

        viewModel.onUnpublishClick()
        assertTrue(viewModel.state.isUnpublishConfirmVisible)
        viewModel.onUnpublishDismiss()

        assertFalse(viewModel.state.isUnpublishConfirmVisible)
        assertEquals(emptyList(), repository.unpublishes)
    }

    @Test
    fun confirmingWithoutTheDialogDoesNothing() = runTest {
        serve()
        val viewModel = viewModel()

        viewModel.onUnpublishConfirm()

        assertEquals(emptyList(), repository.unpublishes)
    }

    @Test
    fun confirmingUnpublishesAndTheNewStatusArrivesThroughTheProject() = runTest {
        serve()
        val viewModel = viewModel()
        viewModel.onUnpublishClick()

        viewModel.onUnpublishConfirm()

        assertEquals(listOf(id), repository.unpublishes)
        with(viewModel.state) {
            assertFalse(isUnpublishConfirmVisible)
            assertFalse(isUnpublishing)
            assertFalse(actionFailed)
            assertEquals(ProjectStatus.Unpublished, project?.status)
            assertFalse(project!!.canUnpublish)
        }
    }

    @Test
    fun aSecondConfirmWhileTheRequestIsOnItsWayDoesNotUnpublishTwice() = runTest {
        serve()
        repository.unpublishGate = Gate()
        val viewModel = viewModel()
        viewModel.onUnpublishClick()

        viewModel.onUnpublishConfirm()
        assertTrue(viewModel.state.isUnpublishing)
        viewModel.onUnpublishConfirm()
        // The dialog's button is tapped again as it fades out, and the dialog could be opened again meanwhile.
        viewModel.onUnpublishClick()
        viewModel.onUnpublishConfirm()

        assertEquals(1, repository.unpublishes.size)
        repository.unpublishGate!!.open()
        assertFalse(viewModel.state.isUnpublishing)
    }

    @Test
    fun aFailureOfUnpublishingIsShownAndTheNextActionClearsIt() = runTest {
        serve()
        repository.unpublishFailure = cause
        val viewModel = viewModel()
        viewModel.onUnpublishClick()

        viewModel.onUnpublishConfirm()

        assertTrue(viewModel.state.actionFailed)
        assertFalse(viewModel.state.isUnpublishing)
        viewModel.onUnpublishClick()
        assertFalse(viewModel.state.actionFailed)
    }

    @Test
    fun aProjectThatIsGoneWhileUnpublishingIsAFailureToo() = runTest {
        serve()
        val viewModel = viewModel()
        repository.cache.value = emptyMap()
        repository.server.clear()
        viewModel.onUnpublishClick()

        viewModel.onUnpublishConfirm()

        assertTrue(viewModel.state.actionFailed)
    }

    // ---- adding a role ----

    @Test
    fun theRoleSheetOpensEmptyAndIsEditedInPlace() = runTest {
        serve()
        val viewModel = viewModel()

        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("Дизайнер")
        viewModel.onRoleSkillAdd("Figma")
        viewModel.onRoleSkillAdd("figma")
        viewModel.onRoleSkillAdd("UX")
        viewModel.onRoleSkillRemove("UX")

        assertEquals(RoleDraft("Дизайнер", listOf("Figma")), viewModel.state.roleEditor?.draft)
    }

    @Test
    fun editingWithoutTheSheetDoesNothing() = runTest {
        serve()
        val viewModel = viewModel()

        viewModel.onRoleTitleChange("Дизайнер")
        viewModel.onRoleSkillAdd("Figma")
        viewModel.onRoleSave()

        assertNull(viewModel.state.roleEditor)
        assertEquals(emptyList(), repository.addedRoles)
    }

    @Test
    fun aRoleWithoutATitleKeepsTheSheetWithTheErrorAndSendsNothing() = runTest {
        serve()
        val viewModel = viewModel()
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("   ")

        viewModel.onRoleSave()

        assertEquals(setOf(RoleDraftError.TitleEmpty), viewModel.state.roleEditor?.errors)
        assertFalse(viewModel.state.isSavingRole)
        assertEquals(emptyList(), repository.addedRoles)
        viewModel.onRoleTitleChange("Дизайнер")
        assertEquals(emptySet(), viewModel.state.roleEditor?.errors, "the errors go as the user fixes them")
    }

    @Test
    fun aTooLongTitleIsRefusedBeforeAnythingIsSent() = runTest {
        serve()
        val viewModel = viewModel()
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("а".repeat(ProjectLimits.MaxRoleTitleLength + 1))

        viewModel.onRoleSave()

        assertEquals(setOf(RoleDraftError.TitleTooLong), viewModel.state.roleEditor?.errors)
        assertEquals(emptyList(), repository.addedRoles)
    }

    @Test
    fun aSavedRoleClosesTheSheetAndAppearsInTheProject() = runTest {
        serve()
        val viewModel = viewModel()
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("  Дизайнер ")
        viewModel.onRoleSkillAdd("Figma")

        viewModel.onRoleSave()

        assertEquals(listOf(id to RoleDraft("Дизайнер", listOf("Figma"))), repository.addedRoles)
        with(viewModel.state) {
            assertNull(roleEditor)
            assertFalse(isSavingRole)
            assertNull(roleFailure)
            assertEquals(listOf("Дизайнер"), project?.roles?.map { it.title })
        }
    }

    @Test
    fun aSecondSaveWhileTheRoleIsOnItsWayAddsNothing() = runTest {
        serve()
        repository.addRoleGate = Gate()
        val viewModel = viewModel()
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("Дизайнер")

        viewModel.onRoleSave()
        assertTrue(viewModel.state.isSavingRole)
        viewModel.onRoleSave()
        viewModel.onRoleSave()

        assertEquals(1, repository.addedRoles.size)
        repository.addRoleGate!!.open()
        assertFalse(viewModel.state.isSavingRole)
        assertEquals(1, viewModel.state.project?.roles?.size)
    }

    @Test
    fun aFailureKeepsTheSheetWithItsCauseAndTheNextTryClearsIt() = runTest {
        serve()
        repository.addRoleFailure = cause
        val viewModel = viewModel()
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("Дизайнер")

        viewModel.onRoleSave()

        with(viewModel.state) {
            assertSame(cause, roleFailure)
            assertNotNull(roleEditor)
            assertFalse(isSavingRole)
        }
        repository.addRoleFailure = null
        viewModel.onRoleSave()
        with(viewModel.state) {
            assertNull(roleFailure)
            assertNull(roleEditor)
        }
        assertEquals(2, repository.addedRoles.size)
    }

    @Test
    fun aConflictStaysInTheSheetSoTheUserCanCheckAndTryAgain() = runTest {
        serve()
        val conflict = ProjectConflictException(id)
        repository.addRoleFailure = conflict
        val viewModel = viewModel()
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("Дизайнер")

        viewModel.onRoleSave()

        with(viewModel.state) {
            assertSame(conflict, roleFailure)
            assertEquals("Дизайнер", roleEditor?.draft?.title)
            assertNotNull(project, "the project stays on the screen")
        }
    }

    @Test
    fun aProjectThatIsGoneClosesTheSheetAndTheScreenSaysSo() = runTest {
        serve()
        repository.addRoleFailure = ProjectNotFoundException(id)
        val viewModel = viewModel()
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("Дизайнер")

        viewModel.onRoleSave()

        with(viewModel.state) {
            assertNull(roleEditor)
            assertNull(project)
            assertNull(loadFailure)
            assertFalse(isLoading)
            assertNull(roleFailure)
        }
    }

    @Test
    fun closingTheSheetClearsTheFailure() = runTest {
        serve()
        repository.addRoleFailure = cause
        val viewModel = viewModel()
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("Дизайнер")
        viewModel.onRoleSave()

        viewModel.onRoleEditorDismiss()

        assertNull(viewModel.state.roleEditor)
        assertNull(viewModel.state.roleFailure)
    }

    @Test
    fun theSheetOpensCleanAfterAFailedTry() = runTest {
        serve()
        repository.addRoleFailure = cause
        val viewModel = viewModel()
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("Дизайнер")
        viewModel.onRoleSave()

        viewModel.onAddRoleClick()

        assertEquals("", viewModel.state.roleEditor?.draft?.title)
        assertNull(viewModel.state.roleFailure)
    }

    // ---- the pure rules ----

    @Test
    fun aRoleWithAFreePlaceHasACard() {
        assertTrue(roleItem(slots = 1, occupied = 0).showsCard)
        assertTrue(roleItem(slots = 3, occupied = 1).showsCard)
    }

    @Test
    fun aRoleWhoseEveryPlaceHasItsKnownPersonIsToldByThePeopleAlone() {
        assertFalse(roleItem(slots = 1, occupied = 1, members = 1).showsCard)
        assertFalse(roleItem(slots = 2, occupied = 2, members = 2).showsCard)
    }

    @Test
    fun aRoleWhosePlacesAreTakenByPeopleTheAppDoesNotKnowKeepsItsCard() {
        // API-GAP G52: the members of the server are not read, yet the places they take are counted.
        assertTrue(roleItem(slots = 1, occupied = 1, members = 0).showsCard)
        assertTrue(roleItem(slots = 3, occupied = 3, members = 2).showsCard)
    }

    @Test
    fun thePlacesLineIsForARoleOfSeveralPlacesOrOneWithNoneFree() {
        assertFalse(roleItem(slots = 1, occupied = 0).showsPlaces)
        assertTrue(roleItem(slots = 2, occupied = 0).showsPlaces)
        assertTrue(roleItem(slots = 1, occupied = 1).showsPlaces)
    }

    @Test
    fun theDetailsOfAProjectCutTheTakenPlacesAtTheSlotsAndCountThem() {
        val project = project(
            "p1",
            status = ProjectStatus.Recruiting,
            roles = listOf(role("r1", slots = 1, occupied = 4), role("r2", slots = 3, occupied = 1)),
        )

        val details = project.toDetails(caps(canUnpublish = true))

        assertEquals(listOf(1, 1), details.roles.map { it.occupied })
        assertEquals(listOf(1, 3), details.roles.map { it.slots })
        assertEquals(2, details.occupiedPlaces)
        assertEquals(4, details.totalPlaces)
        assertEquals(2, details.rolesCount)
        assertTrue(details.canUnpublish)
    }

    @Test
    fun onlyTheInitiatorLooksForPeopleAndOnlyForARoleWithAFreePlace() {
        val roles = listOf(role("free", slots = 2, occupied = 1), role("full", slots = 1, occupied = 1))

        val own = project("p1", viewerIsInitiator = true, roles = roles).toDetails(caps())
        val foreign = project("p1", viewerIsInitiator = false, roles = roles).toDetails(caps())

        assertEquals(listOf(true, false), own.roles.map { it.canFindPeople })
        assertEquals(listOf(false, false), foreign.roles.map { it.canFindPeople })
    }

    @Test
    fun theMembersOfARoleAreShownWithTheirNumbers() {
        val member = RoleMember(UserSummary(UserId("u9"), "Мария", isVerified = true), rating = 4.5, completedProjects = 3)
        val details = project("p1", roles = listOf(role("r1", slots = 2, occupied = 1, members = listOf(member)))).toDetails(caps())

        assertEquals(
            listOf(RoleMemberItem(name = "Мария", avatarKey = "u9", rating = 4.5, completedProjects = 3)),
            details.roles.single().members,
        )
        assertEquals(RoleId("r1"), details.roles.single().id)
    }

    @Test
    fun anUnknownTypeStatusSphereAndPeriodStayUnknownAndTheProjectIsShown() {
        val details = project("p1", status = null, sphere = null).toDetails(caps(canUnpublish = true))

        assertNull(details.type)
        assertNull(details.status)
        assertNull(details.sphere)
        assertNull(details.period)
        assertEquals("Проект p1", details.title)
        assertTrue(details.canUnpublish, "an unknown status does not hide the action")
    }

    @Test
    fun theKnownValuesAreShownAsTheyAre() {
        val period = ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3))

        val details = project(
            "p1",
            status = ProjectStatus.Recruiting,
            type = ProjectType.Practical,
            sphere = Sphere("12", "Дизайн"),
            period = period,
        ).toDetails(caps())

        assertEquals(ProjectType.Practical, details.type)
        assertEquals(ProjectStatus.Recruiting, details.status)
        assertEquals("Дизайн", details.sphere)
        assertEquals(period, details.period)
        assertFalse(details.canUnpublish, "the source cannot")
    }

    @Test
    fun aProjectWithoutASphereIsShownByTheScreenState() = runTest {
        repository.server[id] = project("p1", sphere = null)

        assertNull(viewModel().state.project?.sphere)
    }

    private fun roleItem(slots: Int, occupied: Int, members: Int = 0) = RoleItem(
        id = RoleId("r"),
        title = "Роль",
        skills = emptyList(),
        slots = slots,
        occupied = occupied,
        members = List(members) { RoleMemberItem("M$it", "k$it", null, 0) },
        canFindPeople = false,
    )
}
