package org.matchlab.academic_net.feature.responses.domain

import org.matchlab.academic_net.common.model.ProjectJoinStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ResponsesModelsTest {
    @Test
    fun aRecordWaitsForADecisionOnlyWhenPending() {
        val waiting = ProjectJoinStatus.entries.associateWith { join(status = it).isWaitingForDecision } +
            (null to join(status = null).isWaitingForDecision)

        assertEquals(
            mapOf<ProjectJoinStatus?, Boolean>(
                ProjectJoinStatus.Pending to true,
                ProjectJoinStatus.Accepted to false,
                ProjectJoinStatus.Rejected to false,
                ProjectJoinStatus.Cancelled to false,
                null to false,
            ),
            waiting,
        )
    }

    @Test
    fun theMessageLimitIs500() {
        assertEquals(500, JoinMessageMaxLength)
    }
}
