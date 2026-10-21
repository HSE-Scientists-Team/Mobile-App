package org.matchlab.academic_net.gateway.internal

import io.ktor.http.encodeURLPathPart

/**
 * [value] as one segment of a path: `/`, `?`, `#` and the like are escaped, so that an id which comes from outside
 * (a link, a route argument) can never reach another resource or add a query to the call.
 */
internal fun pathSegment(value: String): String = value.encodeURLPathPart()
