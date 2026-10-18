package org.matchlab.academic_net.gateway.mapping

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import kotlin.time.Instant

// The translation of the Gateway's shapes into the models of :common, which several features share (the profile
// screens, the feed, the people search, a project's owner and members). A model of one feature is translated by its
// own data layer.

/**
 * The person as an author, a member or an owner is shown: the id, the name and whether the server verified them.
 * The name is as the person wrote it and may be empty (a new account has none yet; the UI writes the placeholder).
 * API-GAP G18: there is no avatar, the app draws a placeholder from the id.
 */
fun UserProfileDto.toUserSummary(): UserSummary =
    UserSummary(id = UserId(userId), name = details.displayName.trim(), avatarUrl = null, isVerified = verified)

/**
 * The person as a possible collaborator and as the top of a profile page: the university, the study and the field
 * of study (the API calls it `specialization`). API-GAP G10: the study level is the text the server has, as it is,
 * with a blank one left out; a year of 0 or less means "not given".
 */
fun UserProfileDto.toCollaborator(): Collaborator =
    Collaborator(
        user = toUserSummary(),
        university = details.university.orEmpty().trim(),
        study = Study(
            level = details.studyLevel?.trim()?.takeIf { it.isNotEmpty() },
            year = details.studyYear?.takeIf { it > 0 },
        ),
        field = details.specialization.orEmpty().trim(),
    )

/**
 * The mean rating of the reviews about the person, or `null` when there are no reviews: the API does not say what
 * `rating` is then (API-GAP G20), so a person without reviews has no rating.
 */
fun UserProfileDto.ratingOrNull(): Double? = rating.takeIf { reviewCount > 0 }

/**
 * The moment that is [seconds] after the start of the epoch (1970-01-01T00:00:00Z), the way the API writes its
 * `*_unix` fields; `null` when the server left the field out (`0`, protobuf JSON leaves zeros out) or sent a
 * negative number: a date of 1970 is not a date of this app's data.
 */
fun instantOfUnixSeconds(seconds: Long): Instant? =
    if (seconds > 0) Instant.fromEpochSeconds(seconds) else null
