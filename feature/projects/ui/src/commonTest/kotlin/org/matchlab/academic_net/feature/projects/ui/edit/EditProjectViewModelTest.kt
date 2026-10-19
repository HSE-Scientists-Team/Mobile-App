package org.matchlab.academic_net.feature.projects.ui.edit

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.domain.ProjectDraftError
import org.matchlab.academic_net.feature.projects.domain.ProjectLimits
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.feature.projects.domain.RoleDraftError
import org.matchlab.academic_net.feature.projects.domain.RoleNotRemovableException
import org.matchlab.academic_net.feature.projects.domain.usecase.AddRoleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.GetSpheresUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.LoadProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.RemoveRoleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UpdateProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UpdateRoleUseCase
import org.matchlab.academic_net.feature.projects.ui.FakeProjectsRepository
import org.matchlab.academic_net.feature.projects.ui.Gate
import org.matchlab.academic_net.feature.projects.ui.TestSphere
import org.matchlab.academic_net.feature.projects.ui.caps
import org.matchlab.academic_net.feature.projects.ui.installMainDispatcher
import org.matchlab.academic_net.feature.projects.ui.project
import org.matchlab.academic_net.feature.projects.ui.role
import org.matchlab.academic_net.feature.projects.ui.uninstallMainDispatcher

/** "04d Редактирование": the form filled once, the guarded save and role calls, and every outcome of the server. */
class EditProjectViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val id = ProjectId("p1")
    private val repository = FakeProjectsRepository(caps(canChangeLifecycle = true))
    private val cause = IllegalStateException("offline")
    private val period = ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3))

    private fun viewModel() = EditProjectViewModel(
        projectId = id,
        observeProject = ObserveProjectUseCase(repository),
        loadProject = LoadProjectUseCase(repository),
        updateProject = UpdateProjectUseCase(repository),
        addRole = AddRoleUseCase(repository),
        updateRole = UpdateRoleUseCase(repository),
        removeRole = RemoveRoleUseCase(repository),
        getSpheres = GetSpheresUseCase(repository),
    )

    private val EditProjectViewModel.state get() = uiState.value

    private fun serve(
        roles: List<org.matchlab.academic_net.common.model.Role> = listOf(role("free"), role("taken", occupied = 1)),
        type: ProjectType? = ProjectType.Scientific,
        sphere: Sphere? = TestSphere,
        period: ProjectPeriod? = this.period,
        title: String = "Название",
    ) {
        repository.server[id] = project("p1", title = title, roles = roles, type = type, sphere = sphere, period = period)
    }

    private fun opened(): EditProjectViewModel {
        serve()
        return viewModel()
    }

    // ---- the form is filled once ----

    @Test
    fun theFormIsFilledFromTheProject() {
        val viewModel = opened()

        with(viewModel.state) {
            assertTrue(isFormReady)
            assertFalse(isLoading)
            assertEquals("Название", draft.title)
            assertEquals(ProjectType.Scientific, draft.type)
            assertEquals(TestSphere, draft.sphere)
            assertEquals("Проблема", draft.problem)
            assertEquals("Цель", draft.goal)
            assertEquals(listOf("ML"), draft.tags)
            assertEquals(YearMonth(2026, 10), draft.start)
            assertEquals(YearMonth(2027, 3), draft.end)
            assertEquals(listOf("free", "taken"), roles.map { it.id.value })
        }
    }

    @Test
    fun theScreenIsLoadingUntilTheProjectComes() {
        serve()
        repository.loadGate = Gate()

        val viewModel = viewModel()

        assertTrue(viewModel.state.isLoading)
        repository.loadGate!!.open()
        assertTrue(viewModel.state.isFormReady)
    }

    @Test
    fun valuesTheAppDoesNotKnowAreEmptyAndMustBeChosenBeforeSaving() {
        serve(type = null, sphere = null, period = null)
        val viewModel = viewModel()

        assertNull(viewModel.state.draft.type)
        assertNull(viewModel.state.draft.sphere)
        assertNull(viewModel.state.draft.start)

        viewModel.onSaveClick()

        assertEquals(
            setOf(
                ProjectDraftError.TypeMissing,
                ProjectDraftError.SphereMissing,
                ProjectDraftError.StartMissing,
                ProjectDraftError.EndMissing,
            ),
            viewModel.state.errors,
        )
        assertEquals(emptyList(), repository.updatedInfos)
    }

    @Test
    fun theFormIsNotOverwrittenWhenTheProjectChangesButTheRolesFollow() {
        val viewModel = opened()
        viewModel.onTitleChange("Мой вариант")

        repository.cache.value = repository.cache.value + (id to project("p1", title = "С сервера", roles = listOf(role("new"))))

        assertEquals("Мой вариант", viewModel.state.draft.title)
        assertEquals(listOf("new"), viewModel.state.roles.map { it.id.value })
    }

    @Test
    fun aSecondReadOfTheProjectDoesNotRefillTheForm() {
        val viewModel = opened()
        viewModel.onGoalChange("Новая цель")

        repository.server[id] = project("p1", title = "Другое")
        viewModel.onRetry() // no failure: nothing happens
        repository.cache.value = repository.cache.value + (id to repository.server.getValue(id))

        assertEquals("Новая цель", viewModel.state.draft.goal)
        assertEquals("Название", viewModel.state.draft.title)
    }

    // ---- loading failures ----

    @Test
    fun aFailureWithNothingToShowIsTheErrorAndTheRetryReadsAgain() {
        serve()
        repository.loadFailure = cause

        val viewModel = viewModel()

        assertSame(cause, viewModel.state.loadFailure)
        assertFalse(viewModel.state.isLoading)
        repository.loadFailure = null
        viewModel.onRetry()
        assertNull(viewModel.state.loadFailure)
        assertTrue(viewModel.state.isFormReady)
        assertEquals(2, repository.loads.size)
    }

    @Test
    fun theRetryWhileTheReadIsOnItsWayAsksNothing() {
        serve()
        repository.loadFailure = cause
        val viewModel = viewModel()
        repository.loadFailure = null
        repository.loadGate = Gate()

        viewModel.onRetry()
        viewModel.onRetry()

        assertEquals(2, repository.loads.size)
    }

    @Test
    fun theRetryWithoutAFailureDoesNothing() {
        val viewModel = opened()

        viewModel.onRetry()

        assertEquals(1, repository.loads.size)
    }

    @Test
    fun noSuchProjectIsTheNotFoundScreen() {
        val viewModel = viewModel()

        assertTrue(viewModel.state.isGone)
        assertFalse(viewModel.state.isLoading)
        assertFalse(viewModel.state.isFormReady)
    }

    // ---- saving ----

    @Test
    fun anInvalidFormShowsTheErrorsAndSendsNothing() {
        val viewModel = opened()
        viewModel.onTitleChange("  ")
        viewModel.onProblemChange("")

        viewModel.onSaveClick()

        assertEquals(setOf(ProjectDraftError.TitleEmpty, ProjectDraftError.ProblemEmpty), viewModel.state.errors)
        assertFalse(viewModel.state.isSaving)
        assertEquals(emptyList(), repository.updatedInfos)
    }

    @Test
    fun theEndBeforeTheStartIsRefused() {
        val viewModel = opened()
        viewModel.onStartFieldClick()
        viewModel.onMonthSelect(YearMonth(2028, 1))

        viewModel.onSaveClick()

        assertEquals(setOf(ProjectDraftError.EndBeforeStart), viewModel.state.errors)
        assertEquals(emptyList(), repository.updatedInfos)
    }

    @Test
    fun theErrorsFollowTheEditsAfterTheFirstTry() {
        val viewModel = opened()
        viewModel.onTitleChange("")
        viewModel.onSaveClick()
        assertEquals(setOf(ProjectDraftError.TitleEmpty), viewModel.state.errors)

        viewModel.onTitleChange("а".repeat(ProjectLimits.MaxTitleLength + 1))
        assertEquals(setOf(ProjectDraftError.TitleTooLong), viewModel.state.errors)
        viewModel.onTitleChange("Нормально")
        assertEquals(emptySet(), viewModel.state.errors)
    }

    @Test
    fun aValidFormIsSavedNormalizedAndTheScreenCloses() {
        val viewModel = opened()
        viewModel.onTitleChange("  Новое   название ")
        viewModel.onTypeSelect(ProjectType.Practical)
        viewModel.onAddTag("Kotlin")
        viewModel.onAddTag(" kotlin ")
        viewModel.onRemoveTag("ML")
        viewModel.onSphereSelect(Sphere("3", "Инженерия"))

        viewModel.onSaveClick()

        val (pid, info) = repository.updatedInfos.single()
        assertEquals(id, pid)
        assertEquals("Новое название", info.title)
        assertEquals(ProjectType.Practical, info.type)
        assertEquals("Инженерия", info.sphere.name)
        assertEquals(listOf("Kotlin"), info.tags)
        assertEquals(period, info.period)
        assertTrue(viewModel.state.isSaved)
        assertNull(viewModel.state.saveFailure)
        assertNull(viewModel.state.banner)
    }

    @Test
    fun theGuardOfSavingStaysOnAfterSuccessSoANewTapSendsNothing() {
        val viewModel = opened()

        viewModel.onSaveClick()
        assertTrue(viewModel.state.isSaving, "the form is leaving the screen")
        viewModel.onSaveClick()
        viewModel.onSaveClick()

        assertEquals(1, repository.updatedInfos.size)
    }

    @Test
    fun aSecondTapWhileTheRequestIsOnItsWayDoesNotSendTwice() {
        val viewModel = opened()
        repository.updateProjectGate = Gate()

        viewModel.onSaveClick()
        assertTrue(viewModel.state.isSaving)
        viewModel.onSaveClick()

        assertEquals(1, repository.updatedInfos.size)
        repository.updateProjectGate!!.open()
        assertTrue(viewModel.state.isSaved)
    }

    @Test
    fun savingBeforeTheFormIsReadSendsNothing() {
        serve()
        repository.loadGate = Gate()
        val viewModel = viewModel()

        viewModel.onSaveClick()

        assertEquals(emptyList(), repository.updatedInfos)
        assertFalse(viewModel.state.isSaving)
    }

    @Test
    fun aConflictIsABannerAndTheFormKeepsWhatWasTyped() {
        val viewModel = opened()
        viewModel.onGoalChange("Моя цель")
        repository.updateProjectFailure = ProjectConflictException(id)

        viewModel.onSaveClick()

        with(viewModel.state) {
            assertEquals(EditBanner.Conflict, banner)
            assertFalse(isSaving)
            assertFalse(isSaved)
            assertEquals("Моя цель", draft.goal)
        }
        repository.updateProjectFailure = null
        viewModel.onSaveClick()
        assertTrue(viewModel.state.isSaved)
        assertNull(viewModel.state.banner)
    }

    @Test
    fun aGoneProjectIsTheNotFoundScreen() {
        val viewModel = opened()
        repository.updateProjectFailure = ProjectNotFoundException(id)

        viewModel.onSaveClick()

        assertTrue(viewModel.state.isGone)
        assertFalse(viewModel.state.isSaving)
        assertFalse(viewModel.state.isSaved)
    }

    @Test
    fun anotherFailureIsShownUnderTheButtonAndEditingClearsIt() {
        val viewModel = opened()
        repository.updateProjectFailure = cause

        viewModel.onSaveClick()

        assertSame(cause, viewModel.state.saveFailure)
        assertFalse(viewModel.state.isSaving)
        viewModel.onTitleChange("Другое")
        assertNull(viewModel.state.saveFailure)
    }

    // ---- the pickers ----

    @Test
    fun theMonthPickerSetsTheFieldItWasOpenedFor() {
        val viewModel = opened()

        viewModel.onEndFieldClick()
        viewModel.onMonthSelect(YearMonth(2028, 5))

        assertEquals(YearMonth(2028, 5), viewModel.state.draft.end)
        assertEquals(YearMonth(2026, 10), viewModel.state.draft.start)
        assertNull(viewModel.state.monthPickerFor)
        // A pick without an open picker changes nothing.
        viewModel.onMonthSelect(YearMonth(2030, 1))
        assertEquals(YearMonth(2028, 5), viewModel.state.draft.end)
    }

    @Test
    fun theSpherePickerOpensAndPicks() {
        val viewModel = opened()

        viewModel.onSphereFieldClick()
        assertTrue(viewModel.state.isSpherePickerVisible)
        viewModel.onSpherePickerDismiss()
        assertFalse(viewModel.state.isSpherePickerVisible)
        viewModel.onSphereFieldClick()
        viewModel.onSphereSelect(Sphere("5", "Физика"))

        assertEquals("Физика", viewModel.state.draft.sphere?.name)
        assertFalse(viewModel.state.isSpherePickerVisible)
    }

    // ---- the roles ----

    @Test
    fun thePencilOpensTheSheetFilledWithTheRole() {
        serve(roles = listOf(role("r1", "Дизайнер", skills = listOf("Figma"))))
        val viewModel = viewModel()

        viewModel.onRoleEditClick(RoleId("r1"))

        val editor = viewModel.state.roleEditor!!
        assertEquals(RoleId("r1"), editor.editedRoleId)
        assertEquals(RoleDraft("Дизайнер", listOf("Figma")), editor.draft)
        assertTrue(editor.isEditing)
    }

    @Test
    fun thePencilOfARoleThatIsNotThereOpensNothing() {
        val viewModel = opened()

        viewModel.onRoleEditClick(RoleId("ghost"))

        assertNull(viewModel.state.roleEditor)
    }

    @Test
    fun savingAnEditedRoleUpdatesItAndAddsNothing() {
        val viewModel = opened()
        viewModel.onRoleEditClick(RoleId("free"))
        viewModel.onRoleTitleChange("Новая")
        viewModel.onRoleSkillAdd("Kotlin")

        viewModel.onRoleSave()

        assertEquals(listOf(Triple(id, RoleId("free"), RoleDraft("Новая", listOf("Kotlin")))), repository.updatedRoles)
        assertEquals(emptyList(), repository.addedRoles)
        assertNull(viewModel.state.roleEditor)
        assertFalse(viewModel.state.isSavingRole)
        assertEquals("Новая", viewModel.state.roles.first { it.id.value == "free" }.title)
    }

    @Test
    fun savingANewRoleAddsItAndUpdatesNothing() {
        val viewModel = opened()
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("Тестировщик")

        viewModel.onRoleSave()

        assertEquals(listOf(id to RoleDraft("Тестировщик")), repository.addedRoles)
        assertEquals(emptyList(), repository.updatedRoles)
        assertNull(viewModel.state.roleEditor)
        assertEquals(3, viewModel.state.roles.size)
    }

    @Test
    fun aRoleWithoutATitleKeepsTheSheetWithTheErrorAndSendsNothing() {
        val viewModel = opened()
        viewModel.onRoleEditClick(RoleId("free"))
        viewModel.onRoleTitleChange("")

        viewModel.onRoleSave()

        assertEquals(setOf(RoleDraftError.TitleEmpty), viewModel.state.roleEditor?.errors)
        assertEquals(emptyList(), repository.updatedRoles)
        assertFalse(viewModel.state.isSavingRole)
    }

    @Test
    fun aSecondSaveOfTheRoleWhileItIsOnItsWayMakesNoSecondRequest() {
        val viewModel = opened()
        repository.updateRoleGate = Gate()
        viewModel.onRoleEditClick(RoleId("free"))
        viewModel.onRoleTitleChange("Новая")

        viewModel.onRoleSave()
        assertTrue(viewModel.state.isSavingRole)
        viewModel.onRoleSave()
        viewModel.onRoleSave()

        assertEquals(1, repository.updatedRoles.size)
        repository.updateRoleGate!!.open()
        assertFalse(viewModel.state.isSavingRole)
    }

    @Test
    fun aSecondSaveOfANewRoleWhileItIsOnItsWayAddsOnce() {
        val viewModel = opened()
        repository.addRoleGate = Gate()
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("Новая")

        viewModel.onRoleSave()
        viewModel.onRoleSave()

        assertEquals(1, repository.addedRoles.size)
        repository.addRoleGate!!.open()
    }

    @Test
    fun savingWithoutTheSheetDoesNothing() {
        val viewModel = opened()

        viewModel.onRoleSave()

        assertEquals(emptyList(), repository.updatedRoles)
        assertEquals(emptyList(), repository.addedRoles)
    }

    @Test
    fun aFailureOfTheRoleKeepsTheSheetAndTheNextTryClearsIt() {
        val viewModel = opened()
        repository.updateRoleFailure = cause
        viewModel.onRoleEditClick(RoleId("free"))
        viewModel.onRoleTitleChange("Новая")

        viewModel.onRoleSave()

        assertSame(cause, viewModel.state.roleFailure)
        assertTrue(viewModel.state.roleEditor != null)
        repository.updateRoleFailure = null
        viewModel.onRoleSave()
        assertNull(viewModel.state.roleFailure)
        assertNull(viewModel.state.roleEditor)
    }

    @Test
    fun aConflictOfTheRoleStaysInTheSheet() {
        val viewModel = opened()
        repository.updateRoleFailure = ProjectConflictException(id)
        viewModel.onRoleEditClick(RoleId("free"))
        viewModel.onRoleTitleChange("Новая")

        viewModel.onRoleSave()

        assertIs<ProjectConflictException>(viewModel.state.roleFailure)
        assertTrue(viewModel.state.roleEditor != null)
    }

    @Test
    fun aGoneProjectWhileSavingARoleClosesTheSheetAndIsTheNotFoundScreen() {
        val viewModel = opened()
        repository.updateRoleFailure = ProjectNotFoundException(id)
        viewModel.onRoleEditClick(RoleId("free"))
        viewModel.onRoleTitleChange("Новая")

        viewModel.onRoleSave()

        assertTrue(viewModel.state.isGone)
        assertNull(viewModel.state.roleEditor)
    }

    @Test
    fun closingTheSheetClearsItsFailure() {
        val viewModel = opened()
        repository.updateRoleFailure = cause
        viewModel.onRoleEditClick(RoleId("free"))
        viewModel.onRoleTitleChange("Новая")
        viewModel.onRoleSave()

        viewModel.onRoleEditorDismiss()

        assertNull(viewModel.state.roleEditor)
        assertNull(viewModel.state.roleFailure)
        viewModel.onAddRoleClick()
        assertNull(viewModel.state.roleFailure)
        assertFalse(viewModel.state.roleEditor!!.isEditing)
    }

    @Test
    fun theSheetSkillsAreEditedInPlace() {
        val viewModel = opened()
        viewModel.onAddRoleClick()

        viewModel.onRoleSkillAdd("Python")
        viewModel.onRoleSkillAdd(" python ")
        viewModel.onRoleSkillAdd("SQL")
        viewModel.onRoleSkillRemove("PYTHON")

        assertEquals(listOf("SQL"), viewModel.state.roleEditor?.draft?.skills)
        // Editing without a sheet does nothing.
        viewModel.onRoleEditorDismiss()
        viewModel.onRoleTitleChange("x")
        assertNull(viewModel.state.roleEditor)
    }

    // ---- deleting a role ----

    @Test
    fun theTrashOfARoleWithPeopleOnItOpensNoDialog() {
        val viewModel = opened()

        viewModel.onRoleDeleteClick(RoleId("taken"))

        assertNull(viewModel.state.roleToDelete)
        assertEquals(emptyList(), repository.removedRoles)
    }

    @Test
    fun onlyAFreeRoleCanBeDeletedAndTheStateTellsIt() {
        val viewModel = opened()

        assertEquals(listOf(true, false), viewModel.state.roles.map { it.canDelete })
    }

    @Test
    fun theTrashOfAFreeRoleAsksAndDismissingDeletesNothing() {
        val viewModel = opened()

        viewModel.onRoleDeleteClick(RoleId("free"))
        assertEquals(RoleId("free"), viewModel.state.roleToDelete?.id)
        viewModel.onRoleDeleteDismiss()

        assertNull(viewModel.state.roleToDelete)
        assertEquals(emptyList(), repository.removedRoles)
    }

    @Test
    fun theTrashOfARoleThatIsNotThereOpensNothing() {
        val viewModel = opened()

        viewModel.onRoleDeleteClick(RoleId("ghost"))

        assertNull(viewModel.state.roleToDelete)
    }

    @Test
    fun confirmingDeletesTheRoleAndTheProjectFollowsFromTheCache() {
        val viewModel = opened()
        viewModel.onRoleDeleteClick(RoleId("free"))

        viewModel.onRoleDeleteConfirm()

        assertEquals(listOf(id to RoleId("free")), repository.removedRoles)
        assertEquals(listOf("taken"), viewModel.state.roles.map { it.id.value })
        assertFalse(viewModel.state.isRemovingRole)
        assertNull(viewModel.state.roleToDelete)
        assertNull(viewModel.state.banner)
    }

    @Test
    fun confirmingWithoutTheDialogDoesNothing() {
        val viewModel = opened()

        viewModel.onRoleDeleteConfirm()

        assertEquals(emptyList(), repository.removedRoles)
    }

    @Test
    fun aSecondConfirmWhileTheDeletionIsOnItsWayDeletesOnce() {
        val viewModel = opened()
        repository.removeRoleGate = Gate()
        viewModel.onRoleDeleteClick(RoleId("free"))

        viewModel.onRoleDeleteConfirm()
        assertTrue(viewModel.state.isRemovingRole)
        viewModel.onRoleDeleteConfirm()
        // Another trash is tapped meanwhile: no dialog while a deletion is on its way.
        viewModel.onRoleDeleteClick(RoleId("free"))
        viewModel.onRoleDeleteConfirm()

        assertEquals(1, repository.removedRoles.size)
        assertNull(viewModel.state.roleToDelete)
        repository.removeRoleGate!!.open()
        assertFalse(viewModel.state.isRemovingRole)
    }

    @Test
    fun aRoleTheServerRefusedToDeleteIsABanner() {
        val viewModel = opened()
        repository.removeRoleFailure = RoleNotRemovableException(id, RoleId("free"))
        viewModel.onRoleDeleteClick(RoleId("free"))

        viewModel.onRoleDeleteConfirm()

        assertEquals(EditBanner.RoleNotRemovable, viewModel.state.banner)
        assertFalse(viewModel.state.isRemovingRole)
        assertEquals(2, viewModel.state.roles.size)
    }

    @Test
    fun aGoneProjectWhileDeletingIsTheNotFoundScreen() {
        val viewModel = opened()
        repository.removeRoleFailure = ProjectNotFoundException(id)
        viewModel.onRoleDeleteClick(RoleId("free"))

        viewModel.onRoleDeleteConfirm()

        assertTrue(viewModel.state.isGone)
    }

    @Test
    fun anotherFailureOfADeletionIsABannerWithItsCause() {
        val viewModel = opened()
        repository.removeRoleFailure = cause
        viewModel.onRoleDeleteClick(RoleId("free"))

        viewModel.onRoleDeleteConfirm()

        assertSame(cause, assertIs<EditBanner.RoleDeleteFailed>(viewModel.state.banner).cause)
    }

    @Test
    fun theBannerGoesAwayWithTheNextAction() {
        val viewModel = opened()
        repository.removeRoleFailure = cause
        viewModel.onRoleDeleteClick(RoleId("free"))
        viewModel.onRoleDeleteConfirm()
        assertTrue(viewModel.state.banner != null)

        repository.removeRoleFailure = null
        viewModel.onRoleDeleteClick(RoleId("free"))

        assertNull(viewModel.state.banner)
    }
}
