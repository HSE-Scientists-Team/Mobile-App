package org.matchlab.academic_net.gateway.contract

/**
 * The values of the `kind` of an achievement: the API defines them, as the enum of the `kind` parameter of the
 * achievement lists (`docs/openapi.yaml`). They are the only strings of the achievements that the client may
 * send and tell apart; the `status` of an achievement is not defined and is never read (API-GAP G12).
 *
 * The works of a portfolio are [Scientific] and [Project], the documents of "Образование и документы" are
 * [Education] and [Certificate]. A record of any other kind is not one the app knows, and is left out.
 */
object AchievementKinds {
    const val Education = "education"
    const val Certificate = "certificate"
    const val Scientific = "scientific"
    const val Project = "project"

    /** The kinds of the works of a portfolio. */
    val Works: Set<String> = setOf(Scientific, Project)

    /** The kinds of the documents of a profile. */
    val Documents: Set<String> = setOf(Education, Certificate)
}
