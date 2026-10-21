package org.matchlab.academic_net.feature.projects.domain

import kotlin.test.Test
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectPeriod
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectDraftTest {
    @Test
    fun emptyDraftReportsEveryRequiredField() {
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
            ProjectDraft().validate(),
        )
    }

    @Test
    fun filledDraftIsValidWithoutRolesAndTags() {
        assertTrue(validDraft().validate().isEmpty())
    }

    @Test
    fun blankTitleIsEmpty() {
        assertEquals(setOf(ProjectDraftError.TitleEmpty), validDraft().copy(title = "   ").validate())
    }

    @Test
    fun titleLongerThanLimitIsRejected() {
        val title = "а".repeat(ProjectLimits.MaxTitleLength + 1)

        assertEquals(setOf(ProjectDraftError.TitleTooLong), validDraft().copy(title = title).validate())
    }

    @Test
    fun titleOfMaxLengthIsAccepted() {
        val title = "а".repeat(ProjectLimits.MaxTitleLength)

        assertTrue(validDraft().copy(title = title).validate().isEmpty())
    }

    @Test
    fun bothMonthsAreRequired() {
        assertEquals(setOf(ProjectDraftError.StartMissing), validDraft().copy(start = null).validate())
        assertEquals(setOf(ProjectDraftError.EndMissing), validDraft().copy(end = null).validate())
    }

    @Test
    fun projectCannotEndBeforeItStarts() {
        val draft = validDraft().copy(start = YearMonth(2027, 3), end = YearMonth(2026, 10))

        assertEquals(setOf(ProjectDraftError.EndBeforeStart), draft.validate())
    }

    @Test
    fun oneMonthProjectIsAllowed() {
        val draft = validDraft().copy(start = YearMonth(2026, 10), end = YearMonth(2026, 10))

        assertTrue(draft.validate().isEmpty())
    }

    @Test
    fun newProjectCarriesThePeriod() {
        val newProject = assertNotNull(validDraft().toNewProject())

        assertEquals(ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)), newProject.period)
    }

    @Test
    fun toNewProjectNeedsBothMonths() {
        assertNull(validDraft().copy(start = null).toNewProject())
        assertNull(validDraft().copy(end = null).toNewProject())
    }

    @Test
    fun roleWithoutTitleInvalidatesDraft() {
        val draft = validDraft().copy(roles = listOf(RoleDraft("ML Engineer"), RoleDraft("  ")))

        assertEquals(setOf(ProjectDraftError.RoleInvalid), draft.validate())
    }

    @Test
    fun normalizedTrimsTextAndCleansTagsAndRoles() {
        val draft = validDraft().copy(
            title = "  AI   навигация ",
            problem = "  проблема \n",
            tags = listOf("Python", " python", "", "IoT"),
            roles = listOf(RoleDraft("  ML  Engineer ", listOf("Python", "python", " TensorFlow "))),
        ).normalized()

        assertEquals("AI навигация", draft.title)
        assertEquals("проблема", draft.problem)
        assertEquals(listOf("Python", "IoT"), draft.tags)
        assertEquals(RoleDraft("ML Engineer", listOf("Python", "TensorFlow")), draft.roles.single())
    }

    @Test
    fun toNewProjectNeedsTypeAndSphere() {
        assertNull(validDraft().copy(type = null).toNewProject())
        assertNull(validDraft().copy(sphere = null).toNewProject())
        assertNotNull(validDraft().toNewProject())
    }

    @Test
    fun roleDraftValidation() {
        assertEquals(setOf(RoleDraftError.TitleEmpty), RoleDraft("").validate())
        assertEquals(
            setOf(RoleDraftError.TitleTooLong),
            RoleDraft("a".repeat(ProjectLimits.MaxRoleTitleLength + 1)).validate(),
        )
        assertTrue(RoleDraft("Designer").validate().isEmpty())
    }
}
