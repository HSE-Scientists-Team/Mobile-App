package org.matchlab.academic_net.components.person

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_name_placeholder
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource

/**
 * The name of a person as it is shown: [name] itself, or "Без имени" ("No name") when it is empty or only
 * spaces. A person who has not filled in the name yet, or whom the server gave without one, must not leave
 * an empty line where a card, a list row or a bar expects a name.
 *
 * `PersonName` does this for every card that has a person on it; use this function for a name that is
 * drawn some other way (a title, a content description).
 *
 * A name that is not blank is returned as it came, spaces and all: the text comes from the server and is
 * shown the way it is.
 */
@Composable
fun nameOrPlaceholder(name: String): String =
    if (hasName(name)) name else stringResource(Res.string.components_name_placeholder)

/** Whether [name] says anything: it is not empty and not only whitespace. */
internal fun hasName(name: String): Boolean = name.isNotBlank()
