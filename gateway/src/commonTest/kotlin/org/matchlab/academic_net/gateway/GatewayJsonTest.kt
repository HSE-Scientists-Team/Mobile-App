package org.matchlab.academic_net.gateway

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.matchlab.academic_net.gateway.internal.GatewayJson
import kotlin.test.Test
import kotlin.test.assertEquals

/** The one `Json` of the client, in the way the schema of the API needs it. */
class GatewayJsonTest {
    @Serializable
    private data class Profile(
        @SerialName("display_name") val displayName: String = "",
        @SerialName("study_year") val studyYear: Int? = null,
        val interests: List<String> = emptyList(),
        @SerialName("open_to_collaboration") val openToCollaboration: Boolean = false,
    )

    @Test
    fun nullsAreNeverSent() {
        assertEquals(
            """{"display_name":"Ада","interests":[],"open_to_collaboration":false}""",
            GatewayJson.encodeToString(Profile(displayName = "Ада", studyYear = null)),
        )
    }

    @Test
    fun fieldsWithDefaultsAreSentAnyway() {
        assertEquals(
            """{"display_name":"","interests":[],"open_to_collaboration":false}""",
            GatewayJson.encodeToString(Profile()),
        )
    }

    @Test
    fun aValueThatIsGivenIsSent() {
        val json = GatewayJson.encodeToString(Profile("Ада", 3, listOf("ml"), true))

        assertEquals(
            """{"display_name":"Ада","study_year":3,"interests":["ml"],"open_to_collaboration":true}""",
            json,
        )
    }

    @Test
    fun fieldsTheClientDoesNotKnowAreSkipped() {
        val profile = GatewayJson.decodeFromString<Profile>(
            """{"display_name":"Ада","rating":4.5,"badges":[{"id":1}],"nested":{"a":{"b":null}}}""",
        )

        assertEquals(Profile(displayName = "Ада"), profile)
    }

    @Test
    fun fieldsLeftOutByTheServerTakeTheirDefaults() {
        assertEquals(Profile(), GatewayJson.decodeFromString<Profile>("{}"))
    }

    @Test
    fun anExplicitNullTakesTheDefaultOfTheField() {
        val profile = GatewayJson.decodeFromString<Profile>(
            """{"display_name":null,"study_year":null,"interests":null,"open_to_collaboration":null}""",
        )

        assertEquals(Profile(), profile)
    }
}
