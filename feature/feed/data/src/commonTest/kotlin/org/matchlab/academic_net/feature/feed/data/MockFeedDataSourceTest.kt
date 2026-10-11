package org.matchlab.academic_net.feature.feed.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.feature.feed.data.mock.MockFeedData
import org.matchlab.academic_net.feature.feed.data.mock.MockFeedDataSource

class MockFeedDataSourceTest {
    private val repository = FeedRepositoryImpl(MockFeedDataSource(latencyMillis = 0))

    @Test
    fun viewerIsTheUserOfTheMockProjects() = runTest {
        assertEquals("user-me", repository.getViewer().id.value)
    }

    @Test
    fun recommendsThePeopleOfTheDesign() = runTest {
        val people = repository.getRecommendedPeople()

        assertEquals(listOf("Арджун Мехта", "Мария Соколова", "Даниил Крюков"), people.map { it.user.name })
        assertEquals("Stanford · 3 курс бакалавриата · Computer Science", people.first().subtitle)
    }

    @Test
    fun recommendedPeopleHaveUniqueIds() {
        val ids = MockFeedData.recommendedPeople.map { it.user.id }

        assertTrue(ids.isNotEmpty())
        assertEquals(ids.size, ids.toSet().size)
    }
}
