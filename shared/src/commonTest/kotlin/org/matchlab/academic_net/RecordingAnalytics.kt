package org.matchlab.academic_net

import org.matchlab.academic_net.analytics.Analytics
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * An [Analytics] that writes down what it was asked to do, to show what the app would have sent. It sends nothing and
 * honors no switch: every call is recorded, also one that a real implementation would drop because sending is off, so a
 * test sees the calls themselves and decides what they mean.
 *
 * The calls may come from several threads (the scope of the process runs on `Dispatchers.Default`, the test on its own),
 * so the record is one immutable value that is replaced atomically.
 *
 * [calls] keeps all the kinds of call in the order they came (`profile=user-1`, `profile=null`, `enabled=true`,
 * `track=project_click`), for what depends on the order; [profileId] and [enabled] are what an SDK would hold after the calls.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class RecordingAnalytics : Analytics {
    /** One call of [track]. */
    data class Event(val name: String, val params: Map<String, String>)

    private data class Record(
        val events: List<Event> = emptyList(),
        val profileIds: List<String?> = emptyList(),
        val enabledCalls: List<Boolean> = emptyList(),
        val calls: List<String> = emptyList(),
        val profileId: String? = null,
        val enabled: Boolean? = null,
    )

    private val record = AtomicReference(Record())

    /** Every call of [track], in order. */
    val events: List<Event> get() = record.load().events

    /** Every id given to [setUserProfileId], in order; `null` is "nobody". */
    val profileIds: List<String?> get() = record.load().profileIds

    /** Every value given to [setEnabled], in order. */
    val enabledCalls: List<Boolean> get() = record.load().enabledCalls

    /** Every call of every kind, in the order it came. */
    val calls: List<String> get() = record.load().calls

    /** The profile now: the last id given to [setUserProfileId]; `null` for "nobody" and before any call. */
    val profileId: String? get() = record.load().profileId

    /** Whether sending is on now: the last value given to [setEnabled]; `null` before any call (an SDK starts with it off). */
    val enabled: Boolean? get() = record.load().enabled

    override fun track(event: String, params: Map<String, String>) {
        change { it.copy(events = it.events + Event(event, params), calls = it.calls + "track=$event") }
    }

    override fun setUserProfileId(id: String?) {
        change { it.copy(profileIds = it.profileIds + id, calls = it.calls + "profile=$id", profileId = id) }
    }

    override fun setEnabled(enabled: Boolean) {
        change { it.copy(enabledCalls = it.enabledCalls + enabled, calls = it.calls + "enabled=$enabled", enabled = enabled) }
    }

    private fun change(transform: (Record) -> Record) {
        while (true) {
            val current = record.load()
            if (record.compareAndSet(current, transform(current))) return
        }
    }
}
