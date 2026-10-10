package org.matchlab.academic_net.feature.chats.ui.list

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.chats.ui.TestGraph
import org.matchlab.academic_net.feature.chats.ui.TestNow
import org.matchlab.academic_net.feature.chats.ui.TestZone
import org.matchlab.academic_net.feature.chats.ui.asClock
import org.matchlab.academic_net.feature.chats.ui.installMainDispatcher
import org.matchlab.academic_net.feature.chats.ui.keepCollecting
import org.matchlab.academic_net.feature.chats.ui.uninstallMainDispatcher

class ChatsViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private fun viewModel(graph: TestGraph) =
        ChatsViewModel(graph.observeConversations, { TestNow }.asClock(), { TestZone })

    @Test
    fun startsLoading() = runTest {
        assertTrue(viewModel(TestGraph()).uiState.value.isLoading)
    }

    @Test
    fun showsChatsNewestFirstWithListTimes() = runTest {
        val viewModel = viewModel(TestGraph())
        keepCollecting(viewModel.uiState)

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertEquals(
            listOf("Проф. Елена Васкес", "Мария Соколова", "Даниил Крюков"),
            state.chats.map { it.name },
        )
        assertEquals(listOf("11:57", "Вчера", "8 сен"), state.chats.map { it.time })
        assertEquals("Отлично. Готовы созвониться завтра в 15:00?", state.chats.first().lastMessage)
        assertEquals(2, state.chats.first().unreadCount)
    }

    @Test
    fun chatAppearsAfterTheFirstMessage() = runTest {
        val graph = TestGraph()
        val viewModel = viewModel(graph)
        keepCollecting(viewModel.uiState)
        val stranger = UserSummary(UserId("user-new"), "Новый Человек")

        val id = graph.openConversation(stranger).getOrThrow()
        assertEquals(3, viewModel.uiState.value.chats.size)

        graph.sendMessage(id, "Здравствуйте!")

        val chats = viewModel.uiState.value.chats
        assertEquals(4, chats.size)
        assertEquals("Новый Человек", chats.first().name)
        assertEquals("Здравствуйте!", chats.first().lastMessage)
        assertEquals("12:00", chats.first().time)
    }
}
