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
 * API-GAP G18: there is no avatar, the app draws a placeholder from the id. API-GAP G55: the schema does not require
 * `user_id`, so the id is empty when the server left it out; whoever asked for a person by id gives the profile that
 * id (`copy(id = …)`), as the profile data layer does.
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
        study = studyOf(details.studyLevel, details.studyYear),
        field = details.specialization.orEmpty().trim(),
    )

/**
 * The study of a person from the two fields the API has for it, in a profile and in a row of the people search alike.
 * API-GAP G10: [level] is the text the server has, with its outer spaces cut and a blank one left out (`null`); a
 * [year] of 0 or less (protobuf JSON leaves a zero out) or a missing one is "not given" (`null`).
 */
internal fun studyOf(level: String?, year: Int?): Study =
    Study(level = level?.trim()?.takeIf { it.isNotEmpty() }, year = year?.takeIf { it > 0 })

/**
 * The mean rating of the reviews about the person, or `null` when there are no reviews: the API does not say what
 * `rating` is then (API-GAP G20), so a person without reviews has no rating.
 */
fun UserProfileDto.ratingOrNull(): Double? = rating.takeIf { reviewCount > 0 }

/**
 * The moment that is [seconds] after the start of the epoch (1970-01-01T00:00:00Z), the way the API writes its
 * `*_unix` fields; `null` when the field is not a date of this app's data:
 * - `0`, the server left the field out (protobuf JSON leaves zeros out), or a negative number: a date of 1970 or
 *   before is not a date of this app's data;
 * - a number past the end of the year 9999 ([MaxUnixSeconds]), such as `Long.MAX_VALUE`: the calendar cannot turn a
 *   moment like that into a month and a year, and a garbage number of the server must not fail the mapping of the
 *   whole page it came with. It is "unknown", like a field that was not there.
 */
fun instantOfUnixSeconds(seconds: Long): Instant? =
    if (seconds in 1..MaxUnixSeconds) Instant.fromEpochSeconds(seconds) else null

/** The last second of the year 9999 (9999-12-31T23:59:59Z) in seconds since the epoch: the latest date accepted. */
private const val MaxUnixSeconds = 253_402_300_799L
