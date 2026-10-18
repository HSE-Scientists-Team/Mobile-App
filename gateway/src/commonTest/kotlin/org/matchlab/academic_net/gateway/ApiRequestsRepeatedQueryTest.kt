package org.matchlab.academic_net.gateway

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.gateway.dto.PeopleListDto
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The parameter of the query that may come several times (`skill=a&skill=b`, the way OpenAPI writes an array in the
 * query by default) in [org.matchlab.academic_net.gateway.internal.ApiRequests], and the plain `query` next to it.
 */
class ApiRequestsRepeatedQueryTest {
    private val server = PeopleSessionFixture { json("{}") }

    private suspend fun get(
        query: Map<String, String?> = emptyMap(),
        repeatedQuery: Map<String, List<String>> = emptyMap(),
    ): PeopleListDto = server.session.requests.get("probe", query = query, repeatedQuery = repeatedQuery)

    private val lastUrl get() = server.requests.last().url

    @Test
    fun everyValueIsAParameterOfItsOwnInTheOrderOfTheList() = runTest {
        get(repeatedQuery = mapOf("tag" to listOf("b", "a", "c")))

        assertEquals(listOf("b", "a", "c"), lastUrl.parameters.getAll("tag"))
        assertEquals("tag=b&tag=a&tag=c", lastUrl.encodedQuery)
    }

    @Test
    fun onlyEmptyStringsAreLeftOut() = runTest {
        get(repeatedQuery = mapOf("tag" to listOf("", " ", "x", "", "  y  ")))

        // A blank (not empty) string is the caller's to drop: the helper drops what cannot be a value at all.
        assertEquals(listOf(" ", "x", "  y  "), lastUrl.parameters.getAll("tag"))
    }

    @Test
    fun anEmptyListAddsNoParameterAtAll() = runTest {
        get(repeatedQuery = mapOf("tag" to emptyList(), "skill" to listOf("")))

        assertEquals(emptySet(), lastUrl.parameters.names())
        assertEquals("", lastUrl.encodedQuery)
    }

    @Test
    fun theSameNameInQueryAndRepeatedQueryIsAddedUpNotReplaced() = runTest {
        get(query = mapOf("skill" to "first"), repeatedQuery = mapOf("skill" to listOf("second", "third")))

        assertEquals(listOf("first", "second", "third"), lastUrl.parameters.getAll("skill"))
    }

    @Test
    fun severalNamesCanBeRepeatedAtOnce() = runTest {
        get(repeatedQuery = mapOf("skill" to listOf("a", "b"), "interest" to listOf("x", "y")))

        assertEquals(listOf("a", "b"), lastUrl.parameters.getAll("skill"))
        assertEquals(listOf("x", "y"), lastUrl.parameters.getAll("interest"))
    }

    @Test
    fun theValuesAreEncodedSoNoValueCanAddAParameter() = runTest {
        val values = listOf("C++ & Kotlin", "a=b", "100%", "x#y", "а б", "skill=injected")

        get(repeatedQuery = mapOf("skill" to values))

        assertEquals(values, lastUrl.parameters.getAll("skill"))
        assertEquals(setOf("skill"), lastUrl.parameters.names())
    }

    @Test
    fun theUsualQueryStillWorksAndAValueOfNullIsLeftOut() = runTest {
        get(query = mapOf("limit" to "5", "q" to null, "offset" to "0"))

        assertEquals("limit=5&offset=0", lastUrl.encodedQuery)
    }

    @Test
    fun noRepeatedQueryMeansTheCallOfBefore() = runTest {
        get()

        assertEquals("", lastUrl.encodedQuery)
        assertEquals("https://api.matchlab.space/api/v1/probe", lastUrl.toString())
    }

    @Test
    fun theBearerTokenAndTheRepeatedQueryGoTogether() = runTest {
        get(repeatedQuery = mapOf("tag" to listOf("a", "b")))

        assertEquals(listOf("Bearer ${PeopleSessionFixture.Token}"), server.requests.last().authorizations)
    }
}
