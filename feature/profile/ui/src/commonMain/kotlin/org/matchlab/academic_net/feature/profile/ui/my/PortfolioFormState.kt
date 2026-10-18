package org.matchlab.academic_net.feature.profile.ui.my

/**
 * What the form of a work or a document is doing right now. While one of these runs, the form takes no other action: a
 * second tap on any button, or a tap on another one, does nothing. After the action succeeded the form stays in it until
 * the screen is gone (see `isDone` of the states), so a late tap cannot repeat it.
 */
enum class FormAction {
    /** Adding the record, or saving the changes of the one that is edited. */
    Saving,

    /** Saving what was changed and sending the record for verification ("Отправить на проверку"). */
    Sending,

    /** Deleting the record. */
    Deleting,
}

/** What went wrong with the last action of the form; the screen tells it in words. */
enum class FormProblem {
    /** Adding or saving failed (no network, a server error): nothing was changed, the user may try again. */
    SaveFailed,

    /** The changes were saved, but the record was not sent for verification: the user may send it again. */
    SendFailed,

    /** Deleting failed: the record is still there. */
    DeleteFailed,

    /**
     * The record was changed by someone else since the form was opened (HTTP 409): nothing was saved, and the form
     * shows the version the server has now. The user makes the changes again.
     */
    Conflict,
}
