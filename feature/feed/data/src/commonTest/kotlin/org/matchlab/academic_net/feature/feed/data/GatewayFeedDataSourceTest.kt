package org.matchlab.academic_net.feature.feed.data

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.RecommendationsApi
import org.matchlab.academic_net.gateway.dto.PeopleListDto
import org.matchlab.academic_net.gateway.dto.PersonDto
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** The recommendations that remember every `limit` they were asked for and answer (or fail) as the test says. */
private class FakeRecommendationsApi : RecommendationsApi {
    val limits = mutableListOf<Int>()
    var answer: suspend (Int) -> PeopleListDto = { PeopleListDto() }

    override suspend fun people(limit: Int): PeopleListDto {
        limits += limit
        return answer(limit)
    }
}

private fun person(id: String, name: String = "Имя $id") = PersonDto(userId = id, displayName = name)

private fun answerOf(vararg people: PersonDto) = PeopleListDto(people.toList(), total = people.size.toLong())

/**
 * The recommended people of the feed. API-GAP G33: the API does not say whether the viewer can be among them or whether a
 * person can come twice, so the viewer, the repeats and the records without an id are dropped here.
 */
class GatewayFeedDataSourceTest {
    private val viewer = UserId("viewer")
    private val api = FakeRecommendationsApi()
    private val source = GatewayFeedDataSource(api, viewerId = viewer)

    private suspend fun ids(): List<String> = source.getRecommendedPeople().map { it.user.id.value }

    // ---- the request ----

    @Test
    fun theFeedAsksForTenPeopleByDefault() = runTest {
        source.getRecommendedPeople()

        assertEquals(listOf(10), api.limits)
        assertEquals(10, GatewayFeedDataSource.DefaultLimit)
    }

    @Test
    fun aGivenLimitIsTheOneAskedFor() = runTest {
        GatewayFeedDataSource(api, viewer, limit = 3).getRecommendedPeople()

        assertEquals(listOf(3), api.limits)
    }

    @Test
    fun aLimitThatAsksForNobodyIsRefusedAtOnce() {
        assertFailsWith<IllegalArgumentException> { GatewayFeedDataSource(api, viewer, limit = 0) }
        assertFailsWith<IllegalArgumentException> { GatewayFeedDataSource(api, viewer, limit = -1) }
        GatewayFeedDataSource(api, viewer, limit = 1)

        assertEquals(emptyList(), api.limits)
    }

    @Test
    fun everyCallAsksTheServerAgain() = runTest {
        source.getRecommendedPeople()
        source.getRecommendedPeople()

        assertEquals(listOf(10, 10), api.limits)
    }

    // ---- the answer ----

    @Test
    fun thePeopleComeInTheOrderOfTheServer() = runTest {
        api.answer = { answerOf(person("c"), person("a"), person("b")) }

        assertEquals(listOf("c", "a", "b"), ids())
    }

    @Test
    fun aPersonIsAFullCollaborator() = runTest {
        api.answer = {
            answerOf(
                PersonDto(
                    userId = "u-1",
                    displayName = "  Анна Иванова ",
                    university = " МГУ ",
                    studyLevel = "магистратура",
                    studyYear = 2,
                    specialization = "Physics",
                    verified = true,
                ),
            )
        }

        val person = source.getRecommendedPeople().single()

        assertEquals("Анна Иванова", person.user.name)
        assertEquals(true, person.user.isVerified)
        assertEquals("МГУ", person.university)
        assertEquals(Study("магистратура", 2), person.study)
        assertEquals("Physics", person.field)
    }

    @Test
    fun theViewerIsNotRecommendedToThemselves() = runTest {
        api.answer = { answerOf(person("a"), person("viewer"), person("b")) }

        assertEquals(listOf("a", "b"), ids())
    }

    @Test
    fun aFeedOfOnlyTheViewerIsEmpty() = runTest {
        api.answer = { answerOf(person("viewer")) }

        assertEquals(emptyList(), source.getRecommendedPeople())
    }

    @Test
    fun aRecordWithoutAnIdIsDropped() = runTest {
        api.answer = { answerOf(person(""), person("a"), person("   "), person("b")) }

        assertEquals(listOf("a", "b"), ids())
    }

    @Test
    fun aPersonWhoComesTwiceIsKeptOnceAtTheFirstPlaceWithTheFirstRecord() = runTest {
        api.answer = {
            answerOf(person("a", "Первая запись"), person("b"), person("a", "Вторая запись"), person("c"), person("b"))
        }

        val people = source.getRecommendedPeople()

        assertEquals(listOf("a", "b", "c"), people.map { it.user.id.value })
        assertEquals("Первая запись", people.first().user.name)
    }

    @Test
    fun fewerThanTheLimitMayComeBack() = runTest {
        api.answer = { answerOf(person("viewer"), person("a"), person("a")) }

        assertEquals(listOf("a"), ids())
    }

    @Test
    fun nobodyIsAnEmptyList() = runTest {
        assertEquals(emptyList(), source.getRecommendedPeople())
    }

    // ---- failures ----

    @Test
    fun aFailureOfTheApiIsThrownAsItIs() = runTest {
        val cause = RemoteFailure.Http(500, "boom")
        api.answer = { throw cause }

        assertSame(cause, assertFailsWith<RemoteFailure.Http> { source.getRecommendedPeople() })
    }

    @Test
    fun noConnectionIsThrownAsItIs() = runTest {
        api.answer = { throw RemoteFailure.NoConnection() }

        assertFailsWith<RemoteFailure.NoConnection> { source.getRecommendedPeople() }
    }

    @Test
    fun aCancellationIsNotWrapped() = runTest {
        api.answer = { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { source.getRecommendedPeople() }
    }
}
