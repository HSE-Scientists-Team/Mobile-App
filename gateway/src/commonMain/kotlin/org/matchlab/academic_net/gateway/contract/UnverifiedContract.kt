package org.matchlab.academic_net.gateway.contract

/**
 * Marks what depends on a format or a value that `docs/openapi.yaml` does not define, that the server itself interprets
 * and that has not been checked against a live server. Today that is two things:
 * - the shape of the link in the confirmation letter, which [ConfirmationLinks] reads (API-GAP G01 of
 *   `docs/api-gaps.md`);
 * - the `action` of the transitions of a project, which [ProjectActions] holds (`docs/api-values.md`, §1 rule 4).
 *
 * Using it is a warning, not an error, so that the guess works in the app today; the warning says where the
 * app leans on something unconfirmed. The other objects of the `contract` package are not marked:
 * [AchievementKinds] holds the values the spec does define (the enum of the `kind` parameter of the achievement
 * lists). The values the app defines for the strings the spec leaves open and that the server only stores (a project's
 * type, sphere and duration, the project types of a profile) live next to their models, in `:common`, and are described
 * in `docs/api-values.md`; the strings the server writes itself and the app does not know (the status of an
 * achievement, the role of a review's author) are neither guessed at nor sent, and are read as "unknown"
 * (`docs/api-gaps.md` lists them).
 *
 * Opt in with `@OptIn(UnverifiedContract::class)` once the assumption is written down in `docs/api-gaps.md` or
 * `docs/api-values.md`.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "Not checked against the live server: the OpenAPI spec does not define this value",
)
@Retention(AnnotationRetention.BINARY)
annotation class UnverifiedContract
