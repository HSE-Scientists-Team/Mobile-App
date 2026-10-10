package org.matchlab.academic_net.utils

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Source of identifiers for entities created on the device (in tests: a predictable one). */
fun interface IdGenerator {
    fun newId(): String
}

/** Random UUIDs. */
@OptIn(ExperimentalUuidApi::class)
object UuidIdGenerator : IdGenerator {
    override fun newId(): String = Uuid.random().toString()
}
