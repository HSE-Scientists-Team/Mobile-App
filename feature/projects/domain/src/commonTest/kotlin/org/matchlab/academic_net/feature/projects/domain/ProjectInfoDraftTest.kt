package org.matchlab.academic_net.feature.projects.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectType

class ProjectInfoDraftTest {
    private fun valid() = ProjectInfoDraft.of(testProject())

    @Test
    fun theFormIsFilledFromTheProject() {
        val project = testProject().copy(tags = listOf("a", "b"))

        val draft = ProjectInfoDraft.of(project)

        assertEquals(project.title, draft.title)
        assertEquals(project.type, draft.type)
        assertEquals(project.sphere, draft.sphere)
        assertEquals(project.problem, draft.problem)
        assertEquals(project.goal, draft.goal)
        assertEquals(listOf("a", "b"), draft.tags)
        assertEquals(YearMonth(2026, 10), draft.start)
        assertEquals(YearMonth(2027, 3), draft.end)
        assertTrue(draft.validate().isEmpty())
    }

    @Test
    fun unknownValuesOfTheProjectLeaveTheFieldsEmptyAndMustBeChosen() {
        val draft = ProjectInfoDraft.of(testProject().copy(type = null, sphere = null, period = null))

        assertEquals(null, draft.type)
        assertEquals(null, draft.sphere)
        assertEquals(null, draft.start)
        assertEquals(null, draft.end)
        assertEquals(
            setOf(
                ProjectDraftError.TypeMissing,
                ProjectDraftError.SphereMissing,
                ProjectDraftError.StartMissing,
                ProjectDraftError.EndMissing,
            ),
            draft.validate(),
        )
    }

    @Test
    fun emptyDraftReportsEveryRequiredFieldButNeverRoles() {
        val errors = ProjectInfoDraft().validate()

        assertEquals(
            setOf(
                ProjectDraftError.TitleEmpty,
                ProjectDraftError.TypeMissing,
                ProjectDraftError.SphereMissing,
                ProjectDraftError.ProblemEmpty,
                ProjectDraftError.GoalEmpty,
                ProjectDraftError.StartMissing,
                ProjectDraftError.EndMissing,
            ),
            errors,
        )
    }

    @Test
    fun theEndMayEqualTheStartButNotPrecedeIt() {
        assertTrue(valid().copy(start = YearMonth(2027, 1), end = YearMonth(2027, 1)).validate().isEmpty())
        assertEquals(
            setOf(ProjectDraftError.EndBeforeStart),
            valid().copy(start = YearMonth(2027, 2), end = YearMonth(2027, 1)).validate(),
        )
    }

    @Test
    fun titleLimitIsInclusive() {
        assertTrue(valid().copy(title = "а".repeat(ProjectLimits.MaxTitleLength)).validate().isEmpty())
        assertEquals(
            setOf(ProjectDraftError.TitleTooLong),
            valid().copy(title = "а".repeat(ProjectLimits.MaxTitleLength + 1)).validate(),
        )
    }

    @Test
    fun normalizingTrimsTextsAndDedupesTags() {
        val normalized = valid().copy(
            title = "  Big   title ",
            problem = "  p ",
            goal = " g  ",
            tags = listOf("Kotlin", " kotlin ", "", "Ktor"),
        ).normalized()

        assertEquals("Big title", normalized.title)
        assertEquals("p", normalized.problem)
        assertEquals("g", normalized.goal)
        assertEquals(listOf("Kotlin", "Ktor"), normalized.tags)
    }

    @Test
    fun projectDraftKeepsItsValidationAfterDelegatingToTheInfoDraft() {
        val draft = validDraft().copy(
            roles = listOf(RoleDraft("")),
            start = YearMonth(2027, 5),
            end = YearMonth(2027, 1),
        )

        assertEquals(setOf(ProjectDraftError.RoleInvalid, ProjectDraftError.EndBeforeStart), draft.validate())
        assertEquals(setOf(ProjectDraftError.EndBeforeStart), validDraft().copy(start = YearMonth(2027, 5), end = YearMonth(2027, 1)).validate())
        assertEquals(ProjectType.Scientific, validDraft().type)
        assertEquals(ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)), validDraft().toNewProject()?.period)
    }
}
