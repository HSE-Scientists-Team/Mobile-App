package org.matchlab.academic_net.utils

/**
 * Terms of a search text: the text is split by commas, every term is trimmed and blank terms are
 * dropped. `"Python, , ML "` gives `["Python", "ML"]`; a blank text gives no terms.
 *
 * The search of the mock servers matches a record when at least one term [occursIn] it, and an
 * empty list of terms means "everything fits".
 */
fun searchTerms(text: String): List<String> =
    text.split(',').map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Whether this term occurs in [haystack], ignoring case. The term is trimmed; a blank term occurs
 * nowhere, so an empty filter never matches by accident.
 *
 * [haystack] is expected to be already lowercase: the caller builds it once for a record and
 * checks many terms against it, so it is not lowercased here again.
 */
fun String.occursIn(haystack: String): Boolean = isNotBlank() && trim().lowercase() in haystack

/** A copy of the set with [item] in it when [on] is `true` and without it otherwise (a chip toggle). */
fun <T> Set<T>.toggled(item: T, on: Boolean): Set<T> = if (on) this + item else this - item
