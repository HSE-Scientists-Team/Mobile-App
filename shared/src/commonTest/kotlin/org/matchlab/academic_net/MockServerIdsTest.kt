package org.matchlab.academic_net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.matchlab.academic_net.feature.chats.data.mock.MockChatPeers
import org.matchlab.academic_net.feature.collaborators.data.mock.MockCollaborators
import org.matchlab.academic_net.feature.feed.data.mock.MockFeedData
import org.matchlab.academic_net.mockserver.MockProjectCatalog

/**
 * The mock server, the mock chats, the people search and the feed are separate databases; the
 * people they share must have one id, or "message the initiator" opens a second, empty chat.
 */
class MockServerIdsTest {
    private val peopleOfOtherFeatures = buildList {
        add(MockChatPeers.elena)
        add(MockChatPeers.maria)
        add(MockChatPeers.daniil)
        MockCollaborators.all.mapTo(this) { it.collaborator.user }
        MockFeedData.recommendedPeople.mapTo(this) { it.user }
    }

    @Test
    fun initiatorOfTheCampusProjectIsTheSamePersonAsInTheMockChats() {
        val initiators = MockProjectCatalog.all.map { it.project.initiator }
        val elena = initiators.first { it.name == MockChatPeers.elena.name }

        assertEquals(MockChatPeers.elena.id, elena.id)
    }

    @Test
    fun noProjectInitiatorSharesANameWithAnotherFeaturesPersonUnderAnotherId() {
        val clashes = MockProjectCatalog.all.map { it.project.initiator }.filter { initiator ->
            peopleOfOtherFeatures.any { it.name == initiator.name && it.id != initiator.id }
        }

        assertTrue(clashes.isEmpty(), "same person, different ids: $clashes")
    }
}
