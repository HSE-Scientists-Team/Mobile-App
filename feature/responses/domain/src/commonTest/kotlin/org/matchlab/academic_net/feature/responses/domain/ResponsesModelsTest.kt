package org.matchlab.academic_net.feature.responses.domain

import org.matchlab.academic_net.common.model.ProjectJoinStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ResponsesModelsTest {
    @Test
    fun aRecordWaitsForADecisionWhenPendingOrUnknown() {
        val waiting = ProjectJoinStatus.entries.associateWith { join(status = it).isWaitingForDecision } +
            (null to join(status = null).isWaitingForDecision)

        assertEquals(
            mapOf<ProjectJoinStatus?, Boolean>(
                ProjectJoinStatus.Pending to true,
                ProjectJoinStatus.Accepted to false,
                ProjectJoinStatus.Declined to false,
                ProjectJoinStatus.Withdrawn to false,
                null to true,
            ),
            waiting,
        )
    }

    @Test
    fun theMessageLimitIs500() {
        assertEquals(500, JoinMessageMaxLength)
    }
}
