package org.matchlab.academic_net.feature.profile.data

import kotlinx.datetime.TimeZone
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentId
import org.matchlab.academic_net.feature.profile.domain.EditOutcome
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileCapabilities
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.ProfileDraft
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.feature.profile.domain.WorkId
import org.matchlab.academic_net.gateway.AchievementsApi
import org.matchlab.academic_net.gateway.ProfilesApi
import org.matchlab.academic_net.gateway.ReviewsApi
import org.matchlab.academic_net.gateway.UserDirectory
import org.matchlab.academic_net.gateway.dto.UserAchievementDto
import org.matchlab.academic_net.gateway.dto.UserAchievementInputDto

/**
 * [ProfileDataSource] over the profile calls of the Gateway: it turns the status code of each answer into the result
 * `docs/openapi.yaml` and the contract of [ProfileDataSource] ask for.
 *
 * | Call | Answer -> result |
 * |---|---|
 * | `getProfile` | the public profile from [users]; a 400, 403 or 404 there is `null` |
 * | `getPortfolio`, `getReviews` | 200 is the list, without the records that have no `id` (G55); 400, 403 and 404 are `null` |
 * | `getMyProfile` | 200 with a name is the record; a 404, or no name, is `null` (G19) |
 * | `getMyPortfolio` | 200 is the list; a 404 is an empty one (an account without a profile, G19) |
 * | `getMyWork`, `getMyDocument` | 200 with the right `kind` is the record; another kind, 400 and 404 are `null` |
 * | `updateWork`, `updateDocument` | 409 (also a revision that is not the current one) is [EditOutcome.Conflict], 404 is [EditOutcome.NotFound] |
 * | `submit` | 200 is done, 409 is a conflict, 404 is not found |
 * | `delete` | 204 and 404 are done, 409 is a conflict |
 *
 * Any other status, and every failure of the network, is thrown as the `RemoteFailure` it is.
 *
 * **The project types** of a profile are the codes of `ProjectType` (`docs/api-values.md`): they are read through
 * `ProjectType.fromCode` (an entry the app has no code for does not get into the model) and written as codes. A save
 * sends back, next to the chosen types, the entries of the server's `project_types` that the app does not know, as they
 * came, so the data of other clients is not lost ([withSelectedTypes]).
 *
 * **What it does not do, because the API does not define it.** API-GAP G12: the `status` of a record is not read, so
 * every status is `null`, and [capabilities] says so. API-GAP G13: the role of the author of a review is not read.
 * docs/api-values.md: `study_level` is a free text, read and written as it is. API-GAP G18, G17: no avatars, and the proof of a
 * document is the public `url`. API-GAP G55: the schema does not
 * require the `id` of a record, so a work, a document or a review that came without one is left out (a page of reviews
 * that lost a record is shorter than the server's page, and the repository reads a short page as the last one).
 *
 * @param users the public profiles of other people, shared with the other features of the session.
 * @param timeZone the zone the month of the end of a project is taken in, asked for each review.
 */
class GatewayProfileDataSource(
    private val profiles: ProfilesApi,
    private val achievements: AchievementsApi,
    private val reviews: ReviewsApi,
    private val users: UserDirectory,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ProfileDataSource {
    // API-GAP G12 (the states of a record) is not closed: the server writes them and the app cannot define them, so they
    // are off. The project types are the app's own codes (docs/api-values.md): on.
    override val capabilities: ProfileCapabilities =
        ProfileCapabilities(verificationStatuses = false, interestTypes = true)

    override suspend fun getProfile(id: UserId): ProfileRecord? = users.profile(id.value)?.toRecord()

    override suspend fun getPortfolio(id: UserId): Portfolio? =
        orNoSuchPerson { achievements.userAchievements(id.value, limit = AchievementsApi.MaxLimit).toPortfolio() }

    override suspend fun getReviews(id: UserId, offset: Int, limit: Int): List<Review>? =
        orNoSuchPerson {
            val zone = timeZone()
            // API-GAP G55: a review without an id is left out, so a page may come back shorter than the server's.
            reviews.userReviews(id.value, limit = limit, offset = offset).items.mapNotNull { it.toReview(zone) }
        }

    override suspend fun getMyProfile(): ProfileRecord? {
        val profile = try {
            profiles.myProfile()
        } catch (failure: RemoteFailure.Http) {
            if (failure.status == NOT_FOUND) return null else throw failure
        }
        // API-GAP G19: an account without a profile may get an empty one instead of a 404.
        return if (profile.details.displayName.isBlank()) null else profile.toRecord()
    }

    override suspend fun updateMyProfile(draft: ProfileDraft): ProfileRecord =
        profiles.updateMyProfile { details -> details.withDraft(draft) }.toRecord()

    override suspend fun getMyPortfolio(): Portfolio =
        try {
            achievements.myAchievements(limit = AchievementsApi.MaxLimit).toPortfolio()
        } catch (failure: RemoteFailure.Http) {
            if (failure.status == NOT_FOUND) Portfolio.Empty else throw failure
        }

    override suspend fun getMyWork(id: WorkId): PortfolioWork? = myAchievement(id.value)?.toWork()

    override suspend fun getMyDocument(id: DocumentId): ProfileDocument? = myAchievement(id.value)?.toDocument()

    override suspend fun createWork(draft: WorkDraft): PortfolioWork {
        val input = draft.toInput()
        return achievements.create(input).withInputIfEmpty(input).toWork() ?: throw RemoteFailure.Malformed()
    }

    override suspend fun createDocument(draft: DocumentDraft): ProfileDocument {
        val input = draft.toInput()
        return achievements.create(input).withInputIfEmpty(input).toDocument() ?: throw RemoteFailure.Malformed()
    }

    override suspend fun updateWork(id: WorkId, draft: WorkDraft, expectedRevision: Int): EditOutcome<PortfolioWork> =
        edit {
            achievements.updateMine(id.value, expectedRevision) { details -> details.withDraft(draft) }
                .toWork() ?: throw RemoteFailure.Malformed()
        }

    override suspend fun updateDocument(
        id: DocumentId,
        draft: DocumentDraft,
        expectedRevision: Int,
    ): EditOutcome<ProfileDocument> = edit {
        achievements.updateMine(id.value, expectedRevision) { details -> details.withDraft(draft) }
            .toDocument() ?: throw RemoteFailure.Malformed()
    }

    override suspend fun submit(subject: VerificationSubject): EditOutcome<Unit> = edit {
        // The answer is the record after the send; the caller reads the profile again and needs nothing of it.
        achievements.submit(subject.recordId())
    }

    override suspend fun delete(subject: VerificationSubject): EditOutcome<Unit> =
        try {
            achievements.delete(subject.recordId())
            EditOutcome.Done(Unit)
        } catch (failure: RemoteFailure.Http) {
            when (failure.status) {
                // A record that is gone is as good as deleted.
                NOT_FOUND -> EditOutcome.Done(Unit)
                CONFLICT -> EditOutcome.Conflict
                else -> throw failure
            }
        }

    /** One record of the viewer; `null` for a 400 or a 404 (not a record id, or not the viewer's). */
    private suspend fun myAchievement(id: String): UserAchievementDto? =
        try {
            achievements.myAchievement(id)
        } catch (failure: RemoteFailure.Http) {
            if (failure.status == BAD_REQUEST || failure.status == NOT_FOUND) null else throw failure
        }

    /** Runs a change of a record: 409 is a conflict and 404 is "gone"; the rest is thrown. */
    private suspend fun <T> edit(change: suspend () -> T): EditOutcome<T> =
        try {
            EditOutcome.Done(change())
        } catch (failure: RemoteFailure.Http) {
            when (failure.status) {
                CONFLICT -> EditOutcome.Conflict
                NOT_FOUND -> EditOutcome.NotFound
                else -> throw failure
            }
        }

    /** The answer for somebody who may not be there: a 400, 403 or 404 is "no such person" (`null`). */
    private suspend fun <T : Any> orNoSuchPerson(request: suspend () -> T): T? =
        try {
            request()
        } catch (failure: RemoteFailure.Http) {
            if (failure.status in NO_SUCH_PERSON) null else throw failure
        }

    private fun VerificationSubject.recordId(): String = when (this) {
        is VerificationSubject.Work -> id.value
        is VerificationSubject.Document -> id.value
    }

    // The answer to a create is the record; if the server left its details out (the schema omits empty fields), the
    // record is what was sent.
    private fun UserAchievementDto.withInputIfEmpty(input: UserAchievementInputDto): UserAchievementDto =
        if (details.kind.isBlank()) copy(details = input) else this

    private companion object {
        const val BAD_REQUEST = 400
        const val NOT_FOUND = 404
        const val CONFLICT = 409

        /** 400 (not a user id), 403 (not visible) and 404 (unknown): the person is not there for the viewer. */
        val NO_SUCH_PERSON = setOf(400, 403, 404)
    }
}
