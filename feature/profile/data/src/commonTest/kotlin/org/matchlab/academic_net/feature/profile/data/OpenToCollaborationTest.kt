package org.matchlab.academic_net.feature.profile.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto

/** `details.open_to_collaboration` on its way to the profile page (block 1): a missing or `null` value is "not open". */
class OpenToCollaborationTest {
    @Test
    fun trueIsOpen() {
        assertTrue(profileDto(openToCollaboration = true).toRecord().openToCollaboration)
    }

    @Test
    fun falseAndNullAreNotOpen() {
        assertFalse(profileDto(openToCollaboration = false).toRecord().openToCollaboration)
        assertFalse(profileDto(openToCollaboration = null).toRecord().openToCollaboration)
    }

    @Test
    fun aProfileWhoseDetailsHaveNoFieldIsNotOpen() {
        val dto = UserProfileDto(userId = "u", details = UserProfileInputDto(displayName = "A"))

        assertEquals(null, dto.details.openToCollaboration)
        assertFalse(dto.toRecord().openToCollaboration)
    }

    @Test
    fun theFlagReachesTheProfilePageOfAnyoneAndOfTheViewer() {
        val open = profileDto(openToCollaboration = true).toRecord()
        val closed = profileDto(openToCollaboration = false).toRecord()

        assertTrue(assembleProfile(open, Portfolio.Empty, emptyList(), isViewer = false).openToCollaboration)
        assertTrue(assembleProfile(open, Portfolio.Empty, emptyList(), isViewer = true).openToCollaboration)
        assertFalse(assembleProfile(closed, Portfolio.Empty, emptyList(), isViewer = false).openToCollaboration)
    }

    @Test
    fun theRecordDefaultsToNotOpen() {
        assertEquals(false, profileDto().toRecord().copy(openToCollaboration = false).openToCollaboration)
    }
}
