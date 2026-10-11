package org.matchlab.academic_net.mockserver

/**
 * The mock registry of university e-mail domains that may register in the app.
 *
 * On the real backend the registry is kept by the administration panel; the app never receives it
 * and only learns the verdict for an address. Whether a subdomain of a registered domain
 * (`student.hse.ru` under `hse.ru`) is trusted is the sign-in server's rule, not the registry's.
 */
object MockTrustedDomains {
    /** Lower-case registered domains. */
    val domains: Set<String> = setOf(
        "hse.ru",
        "msu.ru",
        "spbu.ru",
        "itmo.ru",
        "mipt.ru",
        "urfu.ru",
        "stanford.edu",
    )
}
