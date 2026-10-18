package org.matchlab.academic_net.feature.profile.domain

import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * What saving a work or a document of the viewer does, the same for both ([T] is the record): the use cases of
 * `SaveWork` and `SaveDocument` pass the calls of their repository.
 *
 * - Nothing changed and no sending was asked for: no request at all (a `PUT` would reset the confirmation of the
 *   record for nothing).
 * - Changed: [update] first; a [EditOutcome.Conflict] or [EditOutcome.NotFound] ends it (nothing is saved).
 * - [send] asked for, and the record can be verified ([sendsForVerification]): [submit] after the save. If nothing
 *   was changed, this is the only request and its failure is the call's failure (it throws, `Conflict` and
 *   `NotFound` are reported). If the record was just saved, a sending that does not succeed leaves it saved:
 *   [PortfolioChange.Saved] with `sent = false`.
 *
 * @param base the record the form was based on; it is the result when there is nothing to save.
 */
internal suspend fun <T> savePortfolioItem(
    base: T,
    changed: Boolean,
    send: Boolean,
    sendsForVerification: Boolean,
    update: suspend () -> EditOutcome<T>,
    submit: suspend () -> EditOutcome<Unit>,
): PortfolioChange<T> {
    if (!changed && !send) return PortfolioChange.Saved(base, sent = false)

    var saved = base
    if (changed) {
        when (val outcome = update()) {
            is EditOutcome.Done -> saved = outcome.value
            EditOutcome.Conflict -> return PortfolioChange.Conflict
            EditOutcome.NotFound -> return PortfolioChange.NotFound
        }
    }
    if (!send || !sendsForVerification) return PortfolioChange.Saved(saved, sent = false)

    if (!changed) {
        return when (submit()) {
            is EditOutcome.Done -> PortfolioChange.Saved(saved, sent = true)
            EditOutcome.Conflict -> PortfolioChange.Conflict
            EditOutcome.NotFound -> PortfolioChange.NotFound
        }
    }
    val sent = suspendRunCatching { submit() }.getOrNull() is EditOutcome.Done
    return PortfolioChange.Saved(saved, sent)
}
