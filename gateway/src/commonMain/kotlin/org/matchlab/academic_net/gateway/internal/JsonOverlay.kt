package org.matchlab.academic_net.gateway.internal

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonObject

/**
 * An answer read twice: as the DTO the client works with, and as the `details` object of the raw JSON, which a
 * read-modify-write lays its change over (see [layOver]). [rawDetails] is `null` when the answer had no `details`.
 */
internal class AnswerWithDetails<out T>(val dto: T, val rawDetails: JsonObject?)

/** The `details` object of the JSON text of an answer, `null` if the text is not an object or has no such object. */
internal fun rawDetailsOf(answer: String): JsonObject? =
    (GatewayJson.parseToJsonElement(answer) as? JsonObject)?.get("details") as? JsonObject

/** The names the server uses for the fields of [serializer]'s class (`display_name`, not `displayName`). */
internal fun serialNamesOf(serializer: KSerializer<*>): Set<String> = serializer.descriptor.elementNames.toSet()

/**
 * The body of a `PUT` that replaces a whole record: the [typed] fields over what the server sent as [raw].
 *
 * A key the DTO knows ([knownKeys]) is taken from [typed] alone, so that a field the change cleared is gone and
 * not brought back by the old answer. Every other key of [raw] stays as it came: the API may add fields that this
 * client does not know yet, and a `PUT` that does not repeat them would erase them.
 */
internal fun layOver(raw: JsonObject?, typed: JsonObject, knownKeys: Set<String>): JsonObject =
    JsonObject(
        buildMap {
            raw?.forEach { (key, value) -> if (key !in knownKeys) put(key, value) }
            putAll(typed)
        },
    )
