package org.matchlab.academic_net.gateway.contract

/**
 * Marks what depends on a value or a format that `docs/openapi.yaml` does not define and that has not been
 * checked against a live server: the shape of the link in the confirmation letter, the strings behind enums
 * such as a project's status or type.
 *
 * Using it is a warning, not an error, so that the guess works in the app today; the warning says where the
 * app leans on something unconfirmed. Everything in the `contract` package is marked. Reading such a value is
 * tolerant (case does not matter, an unknown value becomes `null`), but an unknown value is never written to
 * the server: the actions that need one wait for the backend's answer.
 *
 * Opt in with `@OptIn(UnverifiedContract::class)` once the guess is written down in `docs/sprint-1.md`.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "Not checked against the live server: the OpenAPI spec does not define this value",
)
@Retention(AnnotationRetention.BINARY)
annotation class UnverifiedContract
