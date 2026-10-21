package org.matchlab.academic_net.gateway.contract

/**
 * The values of `action` of `POST /projects/{id}/transitions`, as the backend lists them in its OpenAPI (`ProjectTransitionAction`,
 * matchlab-backend PR #16). An action the project cannot take from its status is a 409.
 *
 * Public, unlike a table of values the server only stores, because the data layer of the projects builds the request.
 */
object ProjectActions {
    /** `unpublished` to `recruiting`: "Опубликовать". The app does not send it yet: a project is created already published. */
    const val PUBLISH = "publish"

    /** `recruiting` to `unpublished`: "Снять с публикации". */
    const val UNPUBLISH = "unpublish"

    /** `recruiting` to `active`: "Начать", the team starts working. */
    const val START = "start"

    /** `active` to `completed`: "Завершить". */
    const val COMPLETE = "complete"

    /** `recruiting`, `unpublished` or `active` to `cancelled`: "Отменить". */
    const val CANCEL = "cancel"
}
