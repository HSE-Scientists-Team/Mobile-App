package org.matchlab.academic_net.gateway.contract

/**
 * The values of `action` of `POST /projects/{id}/transitions` that the app sends.
 *
 * **Not confirmed by the backend.** The spec names no values: only the summary of the method lists the actions, in this
 * order: "опубликовать, приостановить набор, начать, завершить или отменить". The app sends them as text
 * (`docs/api-values.md`, decision of 2026-10-19); the server interprets `action` itself, so the texts are a guess until
 * it says otherwise. If it reads them differently, this object is the one place to change. "start", "complete" and "cancel"
 * are the lifecycle moves of the project management screen (07c).
 *
 * Public, unlike a table of values the server only stores, because the data layer of the projects builds the request.
 */
@UnverifiedContract
object ProjectActions {
    /** "Опубликовать". The app does not send it: a project is created already published. */
    const val PUBLISH = "publish"

    /** "Приостановить набор": what the app calls "Снять с публикации". */
    const val PAUSE = "pause"

    /** "Начать": the team starts working. */
    const val START = "start"

    /** "Завершить". */
    const val COMPLETE = "complete"

    /** "Отменить". */
    const val CANCEL = "cancel"
}
