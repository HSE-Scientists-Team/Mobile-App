package org.matchlab.academic_net.gateway.contract

/**
 * The values of `action` of `POST /projects/{id}/transitions` that the app sends.
 *
 * **Not confirmed by the backend.** The spec names no values: only the summary of the method lists the actions, in this
 * order: "опубликовать, приостановить набор, начать, завершить или отменить". The app takes the codes of that order
 * (`docs/api-values.md`, decision of 2026-10-19); the server interprets `action` itself, so the codes are a guess until
 * it says otherwise. If it reads them differently, this object is the one place to change. "3" start, "4" finish and "5" cancel
 * are the lifecycle moves of the project management screen (07c).
 *
 * Public, unlike a table of values the server only stores, because the data layer of the projects builds the request.
 */
@UnverifiedContract
object ProjectActions {
    /** "Опубликовать". The app does not send it: a project is created already published. */
    const val PUBLISH = "1"

    /** "Приостановить набор": what the app calls "Снять с публикации". */
    const val PAUSE = "2"

    /** "Начать": the team starts working. */
    const val START = "3"

    /** "Завершить". */
    const val COMPLETE = "4"

    /** "Отменить". */
    const val CANCEL = "5"
}
