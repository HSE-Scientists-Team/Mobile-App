package org.matchlab.academic_net.gateway

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.PeopleListDto
import org.matchlab.academic_net.gateway.dto.PersonDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `GET /api/v1/search/people`: what the client sends for the filters and the page, and how it reads a `PeopleList`.
 * The parameters are read back from the parsed URL of the request the engine got.
 */
class PeopleSearchApiTest {
    private fun server(body: String = PeopleSpec.list(total = "0")) = PeopleSessionFixture { json(body) }

    private suspend fun PeopleSessionFixture.search(
        params: PeopleSearchParams = PeopleSearchParams(),
        page: PageRequest = PageRequest.First,
    ): PeopleListDto = session.peopleSearch.search(params, page)

    // ---- the request ----

    @Test
    fun aSearchIsAGetOfTheSearchPathWithTheBearerTokenAndThePage() = runTest {
        val server = server()

        server.search(page = PageRequest(offset = 40, limit = 20))

        val request = server.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/search/people", request.url.encodedPath)
        assertEquals(listOf("Bearer ${PeopleSessionFixture.Token}"), request.authorizations)
        assertEquals("20", request.url.parameters["limit"])
        assertEquals("40", request.url.parameters["offset"])
        assertEquals("", request.bodyText())
    }

    @Test
    fun theLimitAndTheOffsetAreAlwaysSentAndNothingElseWithoutFilters() = runTest {
        val server = server()

        server.search(page = PageRequest(offset = 0, limit = 1))
        server.search(page = PageRequest(offset = 10_000, limit = 100))

        assertEquals(setOf("limit", "offset"), server.requests[0].url.parameters.names())
        assertEquals("1", server.requests[0].url.parameters["limit"])
        assertEquals("0", server.requests[0].url.parameters["offset"])
        assertEquals(setOf("limit", "offset"), server.requests[1].url.parameters.names())
        assertEquals("100", server.requests[1].url.parameters["limit"])
        assertEquals("10000", server.requests[1].url.parameters["offset"])
    }

    @Test
    fun everyFilterOfTheSpecGoesUnderItsOwnName() = runTest {
        val server = server()

        server.search(
            PeopleSearchParams(
                q = "Computer Vision, Python",
                university = "НИУ ВШЭ",
                projectType = "2",
                skills = listOf("Kotlin", "SQL"),
                interests = listOf("ML", "NLP"),
                myUniversity = true,
                openOnly = true,
                verifiedOnly = true,
                verifiedProjects = true,
                publications = true,
            ),
        )

        val parameters = server.requests.single().url.parameters
        assertEquals("Computer Vision, Python", parameters["q"])
        assertEquals("НИУ ВШЭ", parameters["university"])
        assertEquals("2", parameters["project_type"])
        assertEquals(listOf("Kotlin", "SQL"), parameters.getAll("skill"))
        assertEquals(listOf("ML", "NLP"), parameters.getAll("interest"))
        for (flag in listOf("my_university", "open_only", "verified_only", "verified_projects", "publications")) {
            assertEquals(listOf("true"), parameters.getAll(flag), flag)
        }
        // These are all the names: in particular no role (API-GAP G31).
        assertEquals(
            setOf(
                "limit", "offset", "q", "university", "project_type", "skill", "interest",
                "my_university", "open_only", "verified_only", "verified_projects", "publications",
            ),
            parameters.names(),
        )
    }

    @Test
    fun theProjectTypeGoesAsItsCodeOnlyWhenSet() = runTest {
        val server = server()

        server.search(PeopleSearchParams(projectType = "1"))
        server.search(PeopleSearchParams(projectType = ""))
        server.search(PeopleSearchParams(projectType = "  "))
        server.search(PeopleSearchParams(projectType = null))

        assertEquals("1", server.requests[0].url.parameters["project_type"])
        assertEquals(setOf("limit", "offset", "project_type"), server.requests[0].url.parameters.names())
        for (request in server.requests.drop(1)) assertEquals(setOf("limit", "offset"), request.url.parameters.names())
    }

    @Test
    fun anArrayGoesAsTheParameterRepeated() = runTest {
        val server = server()

        server.search(PeopleSearchParams(skills = listOf("Kotlin", "SQL")))

        val query = server.requests.single().url.encodedQuery
        assertTrue(query.endsWith("skill=Kotlin&skill=SQL"), query)
        assertFalse("skill[]" in query, query)
        assertFalse("," in query, query)
    }

    @Test
    fun nothingThatMeansNoRestrictionIsSent() = runTest {
        val server = server()

        server.search(
            PeopleSearchParams(
                q = "   ",
                university = "",
                skills = listOf("", "  "),
                interests = listOf(""),
            ),
        )
        server.search(PeopleSearchParams(q = null, university = null, skills = emptyList(), interests = emptyList()))

        for (request in server.requests) {
            assertEquals(setOf("limit", "offset"), request.url.parameters.names())
        }
    }

    @Test
    fun aSwitchThatIsOffIsNotSentAtAll() = runTest {
        val server = server()

        server.search(PeopleSearchParams(verifiedOnly = true))

        val parameters = server.requests.single().url.parameters
        assertEquals(listOf("true"), parameters.getAll("verified_only"))
        // `verified_only=false` would be a filter the user did not choose: the other switches are not sent.
        assertEquals(setOf("limit", "offset", "verified_only"), parameters.names())
    }

    @Test
    fun aBlankItemOfAListIsLeftOutAndTheOthersGoAsGivenInOrder() = runTest {
        val server = server()

        server.search(PeopleSearchParams(skills = listOf(" Kotlin ", "", "SQL", "  ", "Kotlin"), interests = listOf("ML", " ")))

        val parameters = server.requests.single().url.parameters
        // Cutting the outer spaces and removing the repeats is the job of the data layer, not of the call.
        assertEquals(listOf(" Kotlin ", "SQL", "Kotlin"), parameters.getAll("skill"))
        assertEquals(listOf("ML"), parameters.getAll("interest"))
    }

    @Test
    fun aTextTravelsAsTypedEvenWhenItLooksLikeTheSyntaxOfAUrl() = runTest {
        val server = server()
        val text = "C++ & Kotlin=fun, 100% #1 а/б"

        server.search(PeopleSearchParams(q = text, university = "Университет «Дружбы»; народов"))

        val parameters = server.requests.single().url.parameters
        assertEquals(text, parameters["q"])
        assertEquals("Университет «Дружбы»; народов", parameters["university"])
        assertEquals(setOf("limit", "offset", "q", "university"), parameters.names())
    }

    // ---- the answer ----

    @Test
    fun aPersonIsReadWithEveryField() = runTest {
        val server = server(PeopleSpec.list("\"42\"", PeopleSpec.FullPerson))

        val answer = server.search()

        assertEquals(42L, answer.total)
        assertEquals(
            PersonDto(
                userId = "u-1",
                displayName = "  Анна Иванова ",
                avatarFileId = "file-1",
                university = " МГУ ",
                studyLevel = "магистратура",
                studyYear = 2,
                specialization = "Physics",
                skills = listOf("Python", "Math"),
                interests = listOf("ML"),
                openToCollaboration = true,
                verified = true,
                rating = 4.5,
                completedProjectCount = 3,
            ),
            answer.items.single(),
        )
    }

    @Test
    fun theFieldsAPersonLeavesOutTakeTheirDefaults() = runTest {
        // Protobuf JSON leaves zeros, empty strings and empty arrays out.
        val server = server(PeopleSpec.list("1", """{"user_id":"u-2"}"""))

        val person = server.search().items.single()

        assertEquals(PersonDto(userId = "u-2"), person)
        assertEquals("", person.displayName)
        assertEquals(0, person.studyYear)
        assertEquals(emptyList(), person.skills)
        assertFalse(person.verified)
    }

    @Test
    fun aNullInPlaceOfAnArrayOrATextIsTheDefault() = runTest {
        val server = server(
            PeopleSpec.list(
                "1",
                """{"user_id":"u-3","display_name":null,"skills":null,"interests":null,"study_year":null,"verified":null}""",
            ),
        )

        val person = server.search().items.single()

        assertEquals(PersonDto(userId = "u-3"), person)
    }

    @Test
    fun aNullListOfPeopleIsAnEmptyOne() = runTest {
        val server = server("""{"items":null,"total":"5"}""")

        val answer = server.search()

        assertEquals(emptyList(), answer.items)
        assertEquals(5L, answer.total)
    }

    @Test
    fun theTotalIsReadFromAStringAndFromANumber() = runTest {
        val asString = server(PeopleSpec.list("\"7\"")).search()
        val asNumber = server(PeopleSpec.list("7")).search()
        val beyondInt = server(PeopleSpec.list("\"3000000000\"")).search()

        assertEquals(7L, asString.total)
        assertEquals(7L, asNumber.total)
        assertEquals(3_000_000_000L, beyondInt.total)
    }

    @Test
    fun aMissingTotalIsZero() = runTest {
        val answer = server(PeopleSpec.list(total = null, PeopleSpec.person("u-1"))).search()

        assertEquals(0L, answer.total)
        assertEquals(1, answer.items.size)
    }

    @Test
    fun anEmptyObjectIsAnEmptyList() = runTest {
        val answer = server("{}").search()

        assertEquals(PeopleListDto(items = emptyList(), total = 0), answer)
    }

    @Test
    fun whatTheClientDoesNotKnowIsSkipped() = runTest {
        val server = server("""{"items":[{"user_id":"u-1","future":{"a":[1,2]}}],"total":"1","next_cursor":"x"}""")

        assertEquals("u-1", server.search().items.single().userId)
    }

    @Test
    fun aRatingComesAsAWholeNumberOrAFraction() = runTest {
        val server = server(
            PeopleSpec.list(
                "2",
                """{"user_id":"a","rating":4}""",
                """{"user_id":"b","rating":4.8}""",
            ),
        )

        assertEquals(listOf(4.0, 4.8), server.search().items.map { it.rating })
    }

    @Test
    fun theOrderOfTheServerIsKept() = runTest {
        val server = server(PeopleSpec.list("3", PeopleSpec.person("z"), PeopleSpec.person("a"), PeopleSpec.person("m")))

        assertEquals(listOf("z", "a", "m"), server.search().items.map { it.userId })
    }

    @Test
    fun anAnswerThatIsNotAPeopleListIsMalformed() = runTest {
        for (body in listOf("not json at all", """{"items":"people"}""", """{"items":[1,2]}""", """["a"]""")) {
            val server = server(body)

            failureOf<RemoteFailure.Malformed> { server.search() }
        }
    }

    // ---- failures ----

    @Test
    fun everyErrorStatusIsAnHttpFailureWithItsStatusAndText() = runTest {
        // 422 is not in the spec of this path, and is a failure like the others.
        for (status in listOf(400, 403, 404, 409, 422, 500, 503)) {
            val server = PeopleSessionFixture { jsonError(HttpStatusCode.fromValue(status), "причина $status") }

            val failure = failureOf<RemoteFailure.Http> { server.search() }

            assertEquals(status, failure.status)
            assertEquals("причина $status", failure.serverMessage)
            assertEquals(1, server.requests.size, "a failed search is not repeated by the client")
        }
    }

    @Test
    fun aFailureWithoutAnErrorBodyKeepsTheStatus() = runTest {
        val server = PeopleSessionFixture { respond("<html>Bad Gateway</html>", HttpStatusCode.BadGateway) }

        val failure = failureOf<RemoteFailure.Http> { server.search() }

        assertEquals(502, failure.status)
    }

    @Test
    fun aRefusedTokenIsReportedExactlyOncePerCall() = runTest {
        val server = PeopleSessionFixture { jsonError(HttpStatusCode.Unauthorized, "expired") }

        val failure = failureOf<RemoteFailure.Http> { server.search() }

        assertEquals(401, failure.status)
        assertEquals(listOf(PeopleSessionFixture.Token), server.rejected)

        failureOf<RemoteFailure.Http> { server.search() }

        assertEquals(listOf(PeopleSessionFixture.Token, PeopleSessionFixture.Token), server.rejected)
    }

    @Test
    fun aFailureOtherThanTheRefusalOfTheTokenIsNotReported() = runTest {
        val server = PeopleSessionFixture { jsonError(HttpStatusCode.Forbidden, "no") }

        failureOf<RemoteFailure.Http> { server.search() }

        assertEquals(emptyList(), server.rejected)
    }
}
