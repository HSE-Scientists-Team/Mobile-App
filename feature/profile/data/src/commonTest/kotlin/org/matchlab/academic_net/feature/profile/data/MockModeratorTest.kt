package org.matchlab.academic_net.feature.profile.data

import kotlin.test.Test
import kotlin.test.assertEquals
import org.matchlab.academic_net.feature.profile.data.mock.MockModerator
import org.matchlab.academic_net.feature.profile.data.mock.ModerationSubmission
import org.matchlab.academic_net.feature.profile.domain.VerificationOutcome

class MockModeratorTest {
    private val moderator = MockModerator()

    private fun work(link: String?, proof: Boolean = false) =
        moderator.decide(ModerationSubmission.Work(link, proof))

    @Test
    fun documentIsAlwaysConfirmed() {
        assertEquals(VerificationOutcome.Verified, moderator.decide(ModerationSubmission.Document))
    }

    @Test
    fun workWithAProofIsConfirmedWhateverTheLink() {
        assertEquals(VerificationOutcome.Verified, work(link = null, proof = true))
        assertEquals(VerificationOutcome.Verified, work(link = "https://drive.example.com/a", proof = true))
    }

    @Test
    fun workWithATrustedLinkIsConfirmed() {
        for (link in listOf(
            "https://doi.org/10.1234/cv",
            "https://arxiv.org/abs/2403.01234",
            "https://github.com/user/repo",
            "https://gitlab.com/user/repo",
        )) {
            assertEquals(VerificationOutcome.Verified, work(link), link)
        }
    }

    @Test
    fun subdomainsAndWwwOfTrustedSitesAreTrusted() {
        assertEquals(VerificationOutcome.Verified, work("https://www.github.com/user/repo"))
        assertEquals(VerificationOutcome.Verified, work("https://gist.github.com/user/1"))
        assertEquals(VerificationOutcome.Verified, work("https://dx.doi.org/10.1/x"))
        assertEquals(VerificationOutcome.Verified, work("HTTPS://GitHub.com/User"))
    }

    @Test
    fun workWithAnUnknownSiteAndNoProofIsRejected() {
        assertEquals(VerificationOutcome.Rejected, work("https://drive.example.com/a"))
    }

    @Test
    fun workWithoutLinkAndProofIsRejected() {
        assertEquals(VerificationOutcome.Rejected, work(link = null))
    }

    @Test
    fun lookalikeHostsAreNotTrusted() {
        assertEquals(VerificationOutcome.Rejected, work("https://notgithub.com/user/repo"))
        assertEquals(VerificationOutcome.Rejected, work("https://github.com.evil.example/user/repo"))
        assertEquals(VerificationOutcome.Rejected, work("https://evil.example/github.com"))
    }

    @Test
    fun textThatIsNotAWebLinkIsNotTrusted() {
        assertEquals(VerificationOutcome.Rejected, work("github.com/user/repo"))
        assertEquals(VerificationOutcome.Rejected, work("ftp://github.com/user/repo"))
    }

    @Test
    fun trustedSitesAreConfigurable() {
        val strict = MockModerator(trustedSites = setOf("example.edu"))

        assertEquals(
            VerificationOutcome.Verified,
            strict.decide(ModerationSubmission.Work("https://lab.example.edu/x", hasProof = false)),
        )
        assertEquals(
            VerificationOutcome.Rejected,
            strict.decide(ModerationSubmission.Work("https://github.com/x", hasProof = false)),
        )
    }
}
