package org.matchlab.academic_net.feature.projects.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.matchlab.academic_net.common.model.ProjectStatus

class ProjectLifecycleTest {
    private val start = ProjectLifecycleAction.Start
    private val complete = ProjectLifecycleAction.Complete
    private val cancel = ProjectLifecycleAction.Cancel

    @Test
    fun actionsByStatusFollowTheTable() {
        assertEquals(listOf(start, complete, cancel), ProjectLifecycle.actionsFor(null))
        assertEquals(listOf(start, cancel), ProjectLifecycle.actionsFor(ProjectStatus.Recruiting))
        assertEquals(listOf(start, cancel), ProjectLifecycle.actionsFor(ProjectStatus.Unpublished))
        assertEquals(listOf(complete, cancel), ProjectLifecycle.actionsFor(ProjectStatus.Started))
        assertEquals(emptyList(), ProjectLifecycle.actionsFor(ProjectStatus.Finished))
        assertEquals(emptyList(), ProjectLifecycle.actionsFor(ProjectStatus.Cancelled))
    }

    @Test
    fun everyStatusIsCoveredAndFinishedOnesOfferNothing() {
        for (status in ProjectStatus.entries) {
            val actions = ProjectLifecycle.actionsFor(status)
            assertEquals(actions.sortedBy { it.ordinal }, actions, "order for $status")
        }
    }

    @Test
    fun unpublishIsOfferedOnlyWhenRecruitingOrUnknown() {
        assertTrue(ProjectLifecycle.canUnpublish(null))
        assertTrue(ProjectLifecycle.canUnpublish(ProjectStatus.Recruiting))
        assertFalse(ProjectLifecycle.canUnpublish(ProjectStatus.Unpublished))
        assertFalse(ProjectLifecycle.canUnpublish(ProjectStatus.Started))
        assertFalse(ProjectLifecycle.canUnpublish(ProjectStatus.Finished))
        assertFalse(ProjectLifecycle.canUnpublish(ProjectStatus.Cancelled))
    }
}
