package org.matchlab.academic_net.feature.profile.data.mock

import org.matchlab.academic_net.feature.profile.domain.VerificationOutcome

/** What was sent to a moderator. */
sealed interface ModerationSubmission {
    /**
     * A work of the profile.
     *
     * @property link its link, `null` if the author gave none.
     * @property hasProof the author attached a proof.
     */
    data class Work(val link: String?, val hasProof: Boolean) : ModerationSubmission

    /** A document; it always comes with a proof. */
    data object Document : ModerationSubmission
}

/**
 * The mock server's moderator: a pure function of what was submitted, so the decisions can be
 * predicted in tests and in the demo.
 *
 * Rules (the contract for the backend's moderation):
 * - a document is confirmed: it cannot be submitted without a proof;
 * - a work is confirmed when it has a proof or its link leads to one of [trustedSites] (or to a
 *   subdomain of one); otherwise it is rejected ("Нужны доказательства").
 */
class MockModerator(
    private val trustedSites: Set<String> = DefaultTrustedSites,
) {
    /**
     * The decision about [submission]. It is always final: this mock never asks for more
     * information and never leaves a submission pending.
     *
     * @return [VerificationOutcome.Verified], or [VerificationOutcome.Rejected] for a work without
     *  a proof and without a link to a trusted site
     */
    fun decide(submission: ModerationSubmission): VerificationOutcome = when (submission) {
        ModerationSubmission.Document -> VerificationOutcome.Verified
        is ModerationSubmission.Work -> {
            val host = submission.link?.let(::webLinkHost)
            val trusted = host != null && trustedSites.any { hostIsOf(host, it) }
            if (submission.hasProof || trusted) VerificationOutcome.Verified else VerificationOutcome.Rejected
        }
    }

    companion object {
        /** Sites where a work is trusted without a proof: the publications and the repositories. */
        val DefaultTrustedSites: Set<String> = setOf("doi.org", "arxiv.org", "github.com", "gitlab.com")
    }
}
