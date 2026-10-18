package org.matchlab.academic_net.feature.collaborators.data

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorFilters
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorQuery
import org.matchlab.academic_net.gateway.PeopleSearchApi
import org.matchlab.academic_net.gateway.PeopleSearchParams
import org.matchlab.academic_net.gateway.dto.PeopleListDto
import org.matchlab.academic_net.gateway.dto.PersonDto
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A people search that remembers every call and answers (or fails) as the test says. */
private class FakePeopleSearchApi : PeopleSearchApi {
    val calls = mutableListOf<Pair<PeopleSearchParams, PageRequest>>()
    var answer: suspend (PeopleSearchParams, PageRequest) -> PeopleListDto = { _, _ -> PeopleListDto() }

    override suspend fun search(params: PeopleSearchParams, page: PageRequest): PeopleListDto {
        calls += params to page
        return answer(params, page)
    }
}

private fun person(id: String, name: String = "Имя $id") = PersonDto(userId = id, displayName = name)

/**
 * `GatewayCollaboratorsDataSource` over the people search: what the domain's query becomes in the call (the rules of
 * `toParams()`), how the answer becomes a page, and that nothing the API does not define is sent.
 */
class GatewayCollaboratorsDataSourceTest {
    private val api = FakePeopleSearchApi()
    private val source = GatewayCollaboratorsDataSource(api)

    // ---- toParams: the table of the contract ----

    @Test
    fun theTextIsCutOfItsOuterSpacesAndNothingElse() {
        assertEquals("Python, Kotlin", CollaboratorQuery("  Python, Kotlin \n").toParams().q)
        // API-GAP G31: how the server reads the text is unknown, so commas and the rest go as typed.
        assertEquals("Computer Vision, Python; C++ & ML", CollaboratorQuery("Computer Vision, Python; C++ & ML").toParams().q)
    }

    @Test
    fun anEmptyOrBlankTextIsNotSent() {
        assertEquals(null, CollaboratorQuery("").toParams().q)
        assertEquals(null, CollaboratorQuery("   \t").toParams().q)
    }

    @Test
    fun eachSwitchOfTheSheetIsItsOwnParameterAndOnlyWhenOn() {
        assertEquals(
            PeopleSearchParams(verifiedOnly = true),
            CollaboratorQuery(filters = CollaboratorFilters(verifiedProfile = true)).toParams(),
        )
        assertEquals(
            PeopleSearchParams(verifiedProjects = true),
            CollaboratorQuery(filters = CollaboratorFilters(hasVerifiedProjects = true)).toParams(),
        )
        assertEquals(
            PeopleSearchParams(publications = true),
            CollaboratorQuery(filters = CollaboratorFilters(hasPublications = true)).toParams(),
        )
        assertEquals(PeopleSearchParams(), CollaboratorQuery(filters = CollaboratorFilters()).toParams())
    }

    @Test
    fun theUniversityIsCutOfItsOuterSpacesAndAnEmptyOneIsNotSent() {
        assertEquals("НИУ ВШЭ", CollaboratorQuery(filters = CollaboratorFilters(university = "  НИУ ВШЭ ")).toParams().university)
        assertEquals(null, CollaboratorQuery(filters = CollaboratorFilters(university = "")).toParams().university)
        assertEquals(null, CollaboratorQuery(filters = CollaboratorFilters(university = "   ")).toParams().university)
    }

    @Test
    fun theKeywordsAreSkillsCutOfSpacesWithoutEmptiesOrRepeatsInTheirOrder() {
        val filters = CollaboratorFilters(keywords = listOf(" Kotlin ", "", "SQL", "Kotlin", "   ", "ml", "SQL"))

        assertEquals(listOf("Kotlin", "SQL", "ml"), CollaboratorQuery(filters = filters).toParams().skills)
    }

    @Test
    fun keywordsThatDifferInCaseAreDifferentKeywords() {
        // The data layer does not guess how the server compares them.
        val filters = CollaboratorFilters(keywords = listOf("Kotlin", "kotlin"))

        assertEquals(listOf("Kotlin", "kotlin"), CollaboratorQuery(filters = filters).toParams().skills)
    }

    @Test
    fun noKeywordsIsNoSkills() {
        assertEquals(emptyList(), CollaboratorQuery().toParams().skills)
        assertEquals(emptyList(), CollaboratorQuery(filters = CollaboratorFilters(keywords = listOf(" ", ""))).toParams().skills)
    }

    @Test
    fun theTypesOfWorksNeverChangeTheCall() {
        // API-GAP G11: `project_type` has no list of values, so nothing is sent for it (and the params have no field for it).
        val plain = CollaboratorQuery("kotlin", CollaboratorFilters(verifiedProfile = true))
        val typed = plain.copy(filters = plain.filters.copy(workTypes = setOf(ProjectType.Scientific, ProjectType.Practical)))

        assertEquals(plain.toParams(), typed.toParams())
    }

    @Test
    fun theFiltersThatTheSheetDoesNotHaveAreNeverSent() {
        val everything = CollaboratorQuery(
            text = "kotlin",
            filters = CollaboratorFilters(
                verifiedProfile = true,
                hasVerifiedProjects = true,
                hasPublications = true,
                workTypes = setOf(ProjectType.Scientific),
                university = "МГУ",
                keywords = listOf("ML"),
            ),
        ).toParams()

        assertEquals(emptyList(), everything.interests)
        assertFalse(everything.myUniversity)
        assertFalse(everything.openOnly)
    }

    @Test
    fun aQueryWithEverythingOffAsksForEveryone() {
        assertEquals(PeopleSearchParams(), CollaboratorQuery().toParams())
    }

    // ---- search ----

    @Test
    fun aSearchAsksTheApiWithTheParamsOfTheQueryAndThePageAsGiven() = runTest {
        val query = CollaboratorQuery("kotlin", CollaboratorFilters(hasPublications = true, keywords = listOf("ML")))
        val page = PageRequest(offset = 40, limit = 20)

        source.search(query, page)

        assertEquals(1, api.calls.size)
        assertEquals(query.toParams(), api.calls.single().first)
        assertSame(page, api.calls.single().second)
    }

    @Test
    fun theAnswerBecomesAPageOfCollaboratorsInTheOrderOfTheServer() = runTest {
        api.answer = { _, _ -> PeopleListDto(listOf(person("z"), person("a")), total = 30) }
        val request = PageRequest(0, 20)

        val page = source.search(CollaboratorQuery(), request)

        assertEquals(listOf("z", "a"), page.items.map { it.user.id.value })
        assertEquals(30, page.total)
        assertEquals(request, page.request)
        assertEquals(2, page.received)
        assertTrue(page.hasMore)
    }

    @Test
    fun aRecordWithoutAnIdIsNotShownButTheNextPageStartsAfterIt() = runTest {
        api.answer = { _, _ -> PeopleListDto(listOf(person("a"), person(""), person("c")), total = 50) }

        val page = source.search(CollaboratorQuery(), PageRequest(offset = 20, limit = 3))

        assertEquals(listOf("a", "c"), page.items.map { it.user.id.value })
        assertEquals(3, page.received)
        assertEquals(PageRequest(offset = 23, limit = 3), page.next())
    }

    @Test
    fun theTotalIsNeverLessThanWhatWasReceivedUpToTheEndOfThePage() = runTest {
        api.answer = { _, _ -> PeopleListDto(listOf(person("a"), person("b")), total = 0) }

        val page = source.search(CollaboratorQuery(), PageRequest(offset = 20, limit = 20))

        assertEquals(22, page.total)
        assertFalse(page.hasMore)
    }

    @Test
    fun noPeopleIsAnEmptyPageThatEndsTheList() = runTest {
        val page = source.search(CollaboratorQuery(text = "nobody"), PageRequest.First)

        assertEquals(emptyList(), page.items)
        assertEquals(0, page.total)
        assertFalse(page.hasMore)
    }

    @Test
    fun aFailureOfTheApiIsThrownAsItIs() = runTest {
        val cause = RemoteFailure.Http(503, "unavailable")
        api.answer = { _, _ -> throw cause }

        val thrown = assertFailsWith<RemoteFailure.Http> { source.search(CollaboratorQuery(), PageRequest.First) }

        assertSame(cause, thrown)
    }

    @Test
    fun noConnectionAndTimeoutAreThrownAsTheyAre() = runTest {
        api.answer = { _, _ -> throw RemoteFailure.NoConnection() }
        assertFailsWith<RemoteFailure.NoConnection> { source.search(CollaboratorQuery(), PageRequest.First) }

        api.answer = { _, _ -> throw RemoteFailure.Timeout() }
        assertFailsWith<RemoteFailure.Timeout> { source.search(CollaboratorQuery(), PageRequest.First) }
    }

    @Test
    fun aCancellationIsNotWrapped() = runTest {
        api.answer = { _, _ -> throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { source.search(CollaboratorQuery(), PageRequest.First) }
    }

    // ---- count ----

    @Test
    fun aCountAsksForTheSmallestPageAndReturnsTheTotal() = runTest {
        api.answer = { _, _ -> PeopleListDto(listOf(person("a")), total = 128) }
        val query = CollaboratorQuery("kotlin", CollaboratorFilters(verifiedProfile = true))

        val count = source.count(query)

        assertEquals(128, count)
        assertEquals(1, api.calls.size)
        assertEquals(query.toParams(), api.calls.single().first)
        assertEquals(PageRequest(offset = 0, limit = 1), api.calls.single().second)
    }

    @Test
    fun aCountOfNobodyIsZero() = runTest {
        api.answer = { _, _ -> PeopleListDto(emptyList(), total = 0) }

        assertEquals(0, source.count(CollaboratorQuery("nobody")))
    }

    @Test
    fun aCountIsNeverLessThanThePeopleThatCame() = runTest {
        // The server sent a record but left the total out.
        api.answer = { _, _ -> PeopleListDto(listOf(person("a")), total = 0) }

        assertEquals(1, source.count(CollaboratorQuery()))
    }

    @Test
    fun aCountBeyondIntIsCutToIt() = runTest {
        api.answer = { _, _ -> PeopleListDto(listOf(person("a")), total = 5_000_000_000L) }

        assertEquals(Int.MAX_VALUE, source.count(CollaboratorQuery()))
    }

    @Test
    fun aFailureOfACountIsThrown() = runTest {
        api.answer = { _, _ -> throw RemoteFailure.Timeout() }

        assertFailsWith<RemoteFailure.Timeout> { source.count(CollaboratorQuery()) }
    }

    @Test
    fun aCancellationOfACountIsNotWrapped() = runTest {
        api.answer = { _, _ -> throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { source.count(CollaboratorQuery()) }
    }

    // ---- capabilities ----

    @Test
    fun theTypeFilterIsOffBecauseTheApiHasNoListOfTypes() {
        // API-GAP G11.
        assertFalse(source.capabilities.projectTypeFilter)
    }
}
