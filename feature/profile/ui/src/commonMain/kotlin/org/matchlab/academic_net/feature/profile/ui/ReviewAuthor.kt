package org.matchlab.academic_net.feature.profile.ui

import org.matchlab.academic_net.feature.profile.domain.Review

/**
 * Whether the author of the review has a page the screens can lead to.
 *
 * API-GAP G55: the id of the author is optional in the schema and the server leaves it out when it has none, so a
 * review may come with an empty one. There is no route to such a person (`profile/` matches nothing and would crash the
 * navigation) and no chat to start with them, so a screen shows the author as a plain row, not a button, and leaves the
 * chat icon out. A blank id (only whitespace) counts as empty.
 */
internal val Review.canOpenAuthor: Boolean get() = author.id.value.isNotBlank()

/**
 * Whether the chat icon is shown next to the author of the review: the author has an id ([canOpenAuthor], API-GAP G55)
 * and is not the viewer, who has nobody to message in a review they wrote themselves.
 */
internal val Review.canMessageAuthor: Boolean get() = canOpenAuthor && !isByViewer
