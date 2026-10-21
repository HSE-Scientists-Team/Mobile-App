package org.matchlab.academic_net.components.format

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_failure_no_connection
import academicnet.components.generated.resources.components_failure_other
import academicnet.components.generated.resources.components_failure_timeout
import academicnet.components.generated.resources.components_failure_unavailable
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.result.RemoteFailure

/**
 * What to tell the user about a call to the server that failed, in the app language: a line for the
 * error text of a screen or a snackbar. It is chosen by the kind of [cause] (see [RemoteFailure]):
 *
 * | [cause] | русский | English |
 * |---|---|---|
 * | [RemoteFailure.NoConnection] | Нет связи с сервером | No connection to the server |
 * | [RemoteFailure.Timeout] | Сервер не ответил | The server didn't answer |
 * | [RemoteFailure.Http], status 500 or more | Сервис временно недоступен | The service is temporarily unavailable |
 * | anything else ¹ | Что-то пошло не так | Something went wrong |
 *
 * ¹ another status of [RemoteFailure.Http], [RemoteFailure.Malformed], another exception, `null`.
 *
 * The text that the server sent with an error (`RemoteFailure.Http.serverMessage`) is never shown: it is
 * written for a developer, in one language, and may say more than the user should read. A client error
 * that has a meaning for a screen (a 409 on a form, a 404 on a profile) is told by the data layer of the
 * feature and the screen picks its own words for it; this function is for what is left.
 *
 * @param cause what the call failed with, or `null` when only the fact that it failed is known
 */
@Composable
fun failureText(cause: Throwable?): String = stringResource(
    when (failureKind(cause)) {
        FailureKind.NoConnection -> Res.string.components_failure_no_connection
        FailureKind.Timeout -> Res.string.components_failure_timeout
        FailureKind.Unavailable -> Res.string.components_failure_unavailable
        FailureKind.Other -> Res.string.components_failure_other
    },
)

/** The failures that [failureText] tells apart. */
internal enum class FailureKind {
    /** The server could not be reached. */
    NoConnection,

    /** The server was reached and did not answer in time. */
    Timeout,

    /** The server is there but is not working: a 5xx. */
    Unavailable,

    /** Everything else: the user can only be told that something went wrong. */
    Other,
}

/** The first status of the 5xx family, the errors of the server itself. */
private const val ServerErrorStatus = 500

/** Sorts [cause] into one of the [FailureKind]s; see [failureText] for the rules. */
internal fun failureKind(cause: Throwable?): FailureKind = when (cause) {
    is RemoteFailure.NoConnection -> FailureKind.NoConnection
    is RemoteFailure.Timeout -> FailureKind.Timeout
    is RemoteFailure.Http -> if (cause.status >= ServerErrorStatus) FailureKind.Unavailable else FailureKind.Other
    else -> FailureKind.Other
}
