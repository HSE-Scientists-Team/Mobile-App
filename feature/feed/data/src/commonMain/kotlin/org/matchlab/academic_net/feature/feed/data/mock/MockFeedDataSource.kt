package org.matchlab.academic_net.feature.feed.data.mock

import kotlinx.coroutines.delay
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.feed.data.FeedDataSource
import org.matchlab.academic_net.mockserver.MockAccounts

/**
 * In-memory stand-in for the feed backend: answers with the people of the Figma screen "01c Лента
 * — люди" after [latencyMillis], like a network call. The ids are those of the same people in the
 * mock people search and chats.
 */
class MockFeedDataSource(
    private val viewer: UserSummary = MockFeedData.viewer,
    private val people: List<Collaborator> = MockFeedData.recommendedPeople,
    private val latencyMillis: Long = DefaultLatencyMillis,
) : FeedDataSource {
    override suspend fun getViewer(): UserSummary {
        simulateNetwork()
        return viewer
    }

    override suspend fun getRecommendedPeople(): List<Collaborator> {
        simulateNetwork()
        return people
    }

    private suspend fun simulateNetwork() {
        if (latencyMillis > 0) delay(latencyMillis)
    }

    private companion object {
        const val DefaultLatencyMillis = 300L
    }
}

object MockFeedData {
    /** The same user as the initiator of the mock projects: the account every mock sign-in lands on. */
    val viewer: UserSummary = MockAccounts.viewer

    val recommendedPeople: List<Collaborator> = listOf(
        Collaborator(
            user = UserSummary(UserId("user-arjun"), "Арджун Мехта", isVerified = true),
            university = "Stanford",
            study = Study("Бакалавриат", 3),
            field = "Computer Science",
        ),
        Collaborator(
            user = UserSummary(UserId("user-maria"), "Мария Соколова", isVerified = true),
            university = "МГУ",
            study = Study("Аспирантура"),
            field = "биоинформатика",
        ),
        Collaborator(
            user = UserSummary(UserId("user-daniil"), "Даниил Крюков", isVerified = true),
            university = "ИТМО",
            study = Study("Магистратура"),
            field = "робототехника",
        ),
    )
}
