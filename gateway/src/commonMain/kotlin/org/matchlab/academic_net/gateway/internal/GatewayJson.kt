package org.matchlab.academic_net.gateway.internal

import kotlinx.serialization.json.Json

/**
 * The one `Json` of the client, for every request body and every answer.
 *
 * The schema is written in the style of protobuf JSON: zeros and empty arrays may be left out or come as
 * `null`, so DTO fields have defaults and an explicit `null` for a field with a default takes the default
 * ([Json.coerceInputValues]); fields the DTO does not know are skipped, so that the server may grow.
 * Requests never carry `null` ([Json.explicitNulls] off) and always carry the fields that have defaults
 * ([Json.encodeDefaults]): the server treats a field that is not sent as "not given".
 */
internal val GatewayJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}
