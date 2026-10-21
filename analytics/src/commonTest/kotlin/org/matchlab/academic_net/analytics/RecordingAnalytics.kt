package org.matchlab.academic_net.analytics

/**
 * An [Analytics] that writes down what it was asked to do and sends nothing, to show what the code under test passed
 * on. It honors no switch: every call is recorded, also one that a real implementation would drop because sending is off.
 */
internal class RecordingAnalytics : Analytics {
    /** One call of [track]. */
    data class Event(val name: String, val params: Map<String, String>)

    /** Every call of [track], in order. */
    val events = mutableListOf<Event>()

    /** Every id given to [setUserProfileId], in order; `null` is "nobody". */
    val profileIds = mutableListOf<String?>()

    /** Every value given to [setEnabled], in order. */
    val enabledCalls = mutableListOf<Boolean>()

    override fun track(event: String, params: Map<String, String>) {
        events += Event(event, params)
    }

    override fun setUserProfileId(id: String?) {
        profileIds += id
    }

    override fun setEnabled(enabled: Boolean) {
        enabledCalls += enabled
    }
}
