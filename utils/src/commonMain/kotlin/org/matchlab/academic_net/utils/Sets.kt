package org.matchlab.academic_net.utils

/** A copy of the set with [item] in it when [on] is `true` and without it otherwise (a chip toggle). */
fun <T> Set<T>.toggled(item: T, on: Boolean): Set<T> = if (on) this + item else this - item
