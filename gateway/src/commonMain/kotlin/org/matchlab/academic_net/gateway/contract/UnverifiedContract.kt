package org.matchlab.academic_net.gateway.contract

/**
 * Marks what depends on a format that `docs/openapi.yaml` does not define and that has not been checked against a
 * live server. Today that is one thing: the shape of the link in the confirmation letter, which [ConfirmationLinks]
 * reads (API-GAP G01 of `docs/api-gaps.md`).
 *
 * Using it is a warning, not an error, so that the guess works in the app today; the warning says where the
 * app leans on something unconfirmed. Only [ConfirmationLinks] is marked. The other objects of the `contract` package
 * are not: [AchievementKinds] holds the values the spec does define (the enum of the `kind` parameter of the
 * achievement lists). There are no tables of the values of strings that the spec leaves open (a project's status or
 * type, the status of an achievement and so on): such a string is neither guessed at nor sent, and it is read as
 * "unknown" (`docs/api-gaps.md` lists them).
 *
 * Opt in with `@OptIn(UnverifiedContract::class)` once the assumption is written down in `docs/api-gaps.md`.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "Not checked against the live server: the OpenAPI spec does not define this value",
)
@Retention(AnnotationRetention.BINARY)
annotation class UnverifiedContract
