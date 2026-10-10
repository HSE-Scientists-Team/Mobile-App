package org.matchlab.academic_net.utils

/** Tag as the user typed it: outer spaces trimmed, inner runs of whitespace collapsed to one space. */
fun String.normalizeTag(): String = trim().split(Regex("\\s+")).joinToString(" ")

/** [normalizeTag] results that are not blank, with duplicates (ignoring case) removed; the first spelling wins. */
fun List<String>.normalizeTags(): List<String> {
    val seen = mutableSetOf<String>()
    return map { it.normalizeTag() }
        .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
}

/** Adds [tag] to the end unless it is blank or already present (ignoring case). */
fun List<String>.withTag(tag: String): List<String> = (this + tag).normalizeTags()

/** Removes [tag] (ignoring case). */
fun List<String>.withoutTag(tag: String): List<String> {
    val target = tag.normalizeTag().lowercase()
    return filterNot { it.normalizeTag().lowercase() == target }
}
