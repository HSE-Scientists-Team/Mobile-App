package org.matchlab.academic_net.utils

import kotlin.math.abs

/** Form of a noun after a whole number in Russian: 1 роль, 2 роли, 5 ролей. */
enum class RussianPluralForm { One, Few, Many }

/**
 * Plural form for [count] by the Russian rules, independent of the device language (the app is
 * Russian-only, while resource plurals follow the device locale and would give "5 роли" on an
 * English phone).
 */
fun russianPluralForm(count: Int): RussianPluralForm {
    val n = abs(count)
    return when {
        n % 100 in 11..14 -> RussianPluralForm.Many
        n % 10 == 1 -> RussianPluralForm.One
        n % 10 in 2..4 -> RussianPluralForm.Few
        else -> RussianPluralForm.Many
    }
}
