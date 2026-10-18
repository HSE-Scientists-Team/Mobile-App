package org.matchlab.academic_net.gateway.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads an `int64` field whether the server sent it as a JSON number (`1760000000`) or as a string
 * (`"1760000000"`). The spec is written in the style of protobuf JSON, which writes 64-bit integers as
 * strings, and some endpoints (Media) send plain numbers; the two cannot be told apart in advance.
 *
 * A value that is not a whole number that fits into a `Long` (`12.5`, `"abc"`, `true`, `null`, an object) is a
 * [SerializationException]; its message never repeats the value. A missing field or an explicit `null` is
 * handled by the field's default and never reaches the serializer (the client's `Json` coerces them).
 *
 * Writes a plain number: the server accepts both. For a nullable field use `FlexibleLongSerializer.nullable`.
 */
internal object FlexibleLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleLong", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        // Outside JSON there is no difference between a number and a string; read the plain number.
        val json = decoder as? JsonDecoder ?: return decoder.decodeLong()

        val element = json.decodeJsonElement()
        if (element !is JsonPrimitive || element is JsonNull) {
            throw SerializationException("An int64 must be a number or a string with a number")
        }
        return element.content.trim().toLongOrNull()
            ?: throw SerializationException("An int64 must be a whole number that fits into 64 bits")
    }

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }
}
