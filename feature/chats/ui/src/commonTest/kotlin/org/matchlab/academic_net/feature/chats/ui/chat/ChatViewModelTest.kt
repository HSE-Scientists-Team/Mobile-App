package org.matchlab.academic_net.feature.chats.ui.chat

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.chats.domain.ChatLimits
import org.matchlab.academic_net.feature.chats.domain.ConversationId
import org.matchlab.academic_net.feature.chats.ui.FailingSendDataSource
import org.matchlab.academic_net.feature.chats.ui.TestGraph
import org.matchlab.academic_net.feature.chats.ui.TestZone
import org.matchlab.academic_net.feature.chats.ui.installMainDispatcher
import org.matchlab.academic_net.feature.chats.ui.keepCollecting
import org.matchlab.academic_net.feature.chats.ui.uninstallMainDispatcher

class ChatViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private fun viewModel(graph: TestGraph, id: ConversationId = graph.elenaChat) = ChatViewModel(
        conversationId = id,
        observeConversation = graph.observeConversation,
        observeMessages = graph.observeMessages,
        sendMessage = graph.sendMessage,
        markConversationRead = graph.markRead,
        timeZone = { TestZone },
    )

    @Test
    fun showsPeerAndMessagesOldestFirstWithTimes() = runTest {
        val viewModel = viewModel(TestGraph())
        keepCollecting(viewModel.uiState)

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        val peer = assertNotNull(state.peer)
        assertEquals("Проф. Елена Васкес", peer.name)
        assertTrue(peer.isVerified)
        // The header of the chat leads to this person's profile.
        assertEquals(UserId("user-elena"), peer.userId)
        assertEquals(listOf(false, false, true, false), state.messages.map { it.isOutgoing })
        assertEquals(listOf("10:40", "10:41", "10:52", "11:57"), state.messages.map { it.time })
        assertEquals("Отлично. Готовы созвониться завтра в 15:00?", state.messages.last().text)
    }

    @Test
    fun openingTheChatMarksItRead() = runTest {
        val graph = TestGraph()
        viewModel(graph)

        assertEquals(0, graph.observeConversation(graph.elenaChat).first()?.unreadCount)
    }

    @Test
    fun sentMessageIsAppendedAndDraftCleared() = runTest {
        val viewModel = viewModel(TestGraph())
        keepCollecting(viewModel.uiState)

        viewModel.onDraftChange("Да, готов!")
        viewModel.onSendClick()

        val state = viewModel.uiState.value
        assertEquals("", state.draft)
        assertFalse(state.isSending)
        assertNull(state.sendProblem)
        assertEquals("Да, готов!", state.messages.last().text)
        assertTrue(state.messages.last().isOutgoing)
        assertEquals(5, state.messages.size)
    }

    @Test
    fun blankDraftIsNotSent() = runTest {
        val viewModel = viewModel(TestGraph())
        keepCollecting(viewModel.uiState)

        viewModel.onDraftChange("   ")
        viewModel.onSendClick()

        assertEquals(4, viewModel.uiState.value.messages.size)
        assertEquals("   ", viewModel.uiState.value.draft)
    }

    @Test
    fun tooLongDraftIsKeptWithAProblem() = runTest {
        val viewModel = viewModel(TestGraph())
        keepCollecting(viewModel.uiState)
        val text = "а".repeat(ChatLimits.MaxMessageLength + 1)

        viewModel.onDraftChange(text)
        viewModel.onSendClick()

        val state = viewModel.uiState.value
        assertEquals(SendProblem.TooLong, state.sendProblem)
        assertEquals(text, state.draft)
        assertEquals(4, state.messages.size)
    }

    @Test
    fun networkFailureKeepsDraftAndReportsIt() = runTest {
        val viewModel = viewModel(TestGraph(FailingSendDataSource()))
        keepCollecting(viewModel.uiState)

        viewModel.onDraftChange("Привет")
        viewModel.onSendClick()

        val state = viewModel.uiState.value
        assertEquals(SendProblem.Failed, state.sendProblem)
        assertEquals("Привет", state.draft)
        assertFalse(state.isSending)
    }

    @Test
    fun editingTheDraftClearsTheProblem() = runTest {
        val viewModel = viewModel(TestGraph(FailingSendDataSource()))
        keepCollecting(viewModel.uiState)
        viewModel.onDraftChange("Привет")
        viewModel.onSendClick()

        viewModel.onDraftChange("Привет!")

        assertNull(viewModel.uiState.value.sendProblem)
    }

    @Test
    fun unknownChatIsLoadedButEmpty() = runTest {
        val graph = TestGraph()
        val viewModel = viewModel(graph, ConversationId("missing"))
        keepCollecting(viewModel.uiState)

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertNull(state.peer)
        assertTrue(state.messages.isEmpty())
    }
}
