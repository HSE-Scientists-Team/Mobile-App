package org.matchlab.academic_net.gateway

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.matchlab.academic_net.gateway.dto.FlexibleLongSerializer
import org.matchlab.academic_net.gateway.internal.GatewayJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FlexibleLongSerializerTest {
    @Serializable
    private data class Stamped(
        @Serializable(with = FlexibleLongSerializer::class) val at: Long = 0,
        val name: String = "",
    )

    @Serializable
    private data class WithDefault(@Serializable(with = FlexibleLongSerializer::class) val at: Long = 7)

    private fun read(json: String): Long = GatewayJson.decodeFromString<Stamped>(json).at

    @Test
    fun aNumberIsRead() {
        assertEquals(1_760_000_000L, read("""{"at":1760000000}"""))
    }

    @Test
    fun aStringWithANumberIsRead() {
        assertEquals(1_760_000_000L, read("""{"at":"1760000000"}"""))
    }

    @Test
    fun theTwoFormsGiveTheSameValue() {
        assertEquals(read("""{"at":42}"""), read("""{"at":"42"}"""))
    }

    @Test
    fun zeroAndNegativeNumbersAreRead() {
        assertEquals(0L, read("""{"at":0}"""))
        assertEquals(0L, read("""{"at":"0"}"""))
        assertEquals(-5L, read("""{"at":-5}"""))
        assertEquals(-5L, read("""{"at":"-5"}"""))
    }

    @Test
    fun theLimitsOfInt64AreRead() {
        assertEquals(Long.MAX_VALUE, read("""{"at":"9223372036854775807"}"""))
        assertEquals(Long.MAX_VALUE, read("""{"at":9223372036854775807}"""))
        assertEquals(Long.MIN_VALUE, read("""{"at":"-9223372036854775808"}"""))
        assertEquals(Long.MIN_VALUE, read("""{"at":-9223372036854775808}"""))
    }

    @Test
    fun aNumberBeyondInt64IsRejected() {
        assertFailsWith<SerializationException> { read("""{"at":"9223372036854775808"}""") }
        assertFailsWith<SerializationException> { read("""{"at":9223372036854775808}""") }
    }

    @Test
    fun aStringWithSpacesAroundTheNumberIsRead() {
        assertEquals(12L, read("""{"at":" 12 "}"""))
    }

    @Test
    fun whatIsNotAWholeNumberIsRejected() {
        val rejected = listOf(
            """{"at":12.5}""",
            """{"at":"12.5"}""",
            """{"at":"abc"}""",
            """{"at":""}""",
            """{"at":"   "}""",
            """{"at":"1 2"}""",
            """{"at":true}""",
            """{"at":{"x":1}}""",
            """{"at":[1]}""",
        )
        for (json in rejected) {
            assertFailsWith<SerializationException>(json) { read(json) }
        }
    }

    @Test
    fun theMessageOfARejectionNeverRepeatsTheValue() {
        val error = assertFailsWith<SerializationException> { read("""{"at":"top-secret-value"}""") }

        assertFalse("top-secret-value" in error.message.orEmpty())
    }

    @Test
    fun aMissingFieldTakesTheDefault() {
        assertEquals(Stamped(at = 0, name = "n"), GatewayJson.decodeFromString<Stamped>("""{"name":"n"}"""))
    }

    @Test
    fun anExplicitNullTakesTheDefaultToo() {
        assertEquals(7L, GatewayJson.decodeFromString<WithDefault>("""{"at":null}""").at)
    }

    @Test
    fun otherFieldsAroundItAreUnaffected() {
        val stamped = GatewayJson.decodeFromString<Stamped>("""{"name":"Ада","at":"15","extra":[1,2]}""")

        assertEquals(Stamped(at = 15, name = "Ада"), stamped)
    }

    @Test
    fun itIsWrittenAsAPlainNumber() {
        assertEquals("""{"at":1760000000,"name":"x"}""", GatewayJson.encodeToString(Stamped(1_760_000_000L, "x")))
    }

    @Test
    fun whatItWritesItReadsBack() {
        val original = Stamped(Long.MIN_VALUE, "min")

        assertEquals(original, GatewayJson.decodeFromString<Stamped>(GatewayJson.encodeToString(original)))
    }

    @Test
    fun aNullableFieldCanUseItsNullableForm() {
        val serializer = FlexibleLongSerializer.nullable

        assertNull(GatewayJson.decodeFromString(serializer, "null"))
        assertEquals(5L, GatewayJson.decodeFromString(serializer, "\"5\""))
        assertEquals(5L, GatewayJson.decodeFromString(serializer, "5"))
    }

    @OptIn(ExperimentalSerializationApi::class)
    private class PlainLongDecoder(private val value: Long) : AbstractDecoder() {
        override val serializersModule: SerializersModule = EmptySerializersModule()

        override fun decodeElementIndex(descriptor: SerialDescriptor): Int = CompositeDecoder.DECODE_DONE

        override fun decodeLong(): Long = value
    }

    @Test
    fun aFormatWithoutStringsForNumbersJustReadsTheNumber() {
        assertEquals(5L, FlexibleLongSerializer.deserialize(PlainLongDecoder(5L)))
    }

    @Test
    fun aBareNullIsRejectedByTheNonNullableForm() {
        assertFailsWith<SerializationException> { GatewayJson.decodeFromString(FlexibleLongSerializer, "null") }
    }

    @Test
    fun aBareValueIsReadOutsideAnObjectToo() {
        assertEquals(31L, GatewayJson.decodeFromString(FlexibleLongSerializer, "\"31\""))
        assertEquals(31L, GatewayJson.decodeFromString(FlexibleLongSerializer, "31"))
    }
}
