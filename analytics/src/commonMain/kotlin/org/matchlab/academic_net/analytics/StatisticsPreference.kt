package org.matchlab.academic_net.analytics

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.matchlab.academic_net.securestore.SecureStore

/**
 * The choice "send usage statistics" of this device: whether the analytics may send anything.
 *
 * It is kept in the [store] under the key `analytics.statistics` ("1" for yes, "0" for no). The value is not a secret;
 * the secure store is used because it is the one persistent storage the app has, and it already holds the last e-mail
 * of the sign-in. The choice belongs to the device, not to the account: it survives a sign-out. It is lost only when
 * the store itself is lost (the app's data was cleared, the platform's key is gone, the store was wiped), and then the
 * person has made no choice again.
 *
 * [enabled] is `null` until there is a choice: before [load] has read the store, and after it when the store holds none
 * (the first start, or a value that cannot be read: the store does not tell the two apart). `null` means "not allowed
 * yet": the app decides what to do about it (see `AnalyticsController` in `:shared`).
 *
 * The store calls are blocking, so they run on [io]; a test passes its own dispatcher.
 */
class StatisticsPreference(
    private val store: SecureStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val state = MutableStateFlow<Boolean?>(null)

    /** The choice: `true` or `false` once there is one, `null` before that. */
    val enabled: StateFlow<Boolean?> = state.asStateFlow()

    /** Held by [load] while it reads and by [set] while it writes, so that a stale read never covers a newer write. */
    private val access = Mutex()
    private var loaded = false

    /**
     * Reads the stored choice into [enabled]. The store is read once: a call that comes while another is reading waits
     * for it, and a call after it returns at once. An entry that is missing, unreadable or not "1" / "0" is "no
     * choice" ([enabled] stays `null`); a store that fails on reading does not throw here.
     */
    suspend fun load() {
        access.withLock {
            if (loaded) return
            val stored = withContext(io) { runCatching { store.read(Key) }.getOrNull() }
            state.value = when (stored) {
                Yes -> true
                No -> false
                else -> null
            }
            loaded = true
        }
    }

    /**
     * Saves the choice [enabled] and publishes it to the [StatisticsPreference.enabled] flow. A "yes" is published only
     * after the store has accepted it, so the flow never shows a consent that would be lost at the next start; when the
     * store cannot write it, the exception reaches the caller, who decides what to do (the choice stays as it was).
     *
     * A "no" is the other way round: if the store cannot write it, it is **published all the same** and then the
     * exception reaches the caller, because the safe side is to send nothing. The refusal holds until the app is closed,
     * and an older "yes" in the store, if it can be read, is removed, if the store allows it, so that the next start has
     * none (the default applies again) and not a "yes" that the person has since taken back; an older "no", or an entry
     * that cannot be read, stays.
     *
     * A call that comes while [load] is reading waits for it, and after a call that published a value the store is not
     * read again: what was set is the choice. A caller that is cancelled while it waits for its turn writes nothing; one
     * that is cancelled after the write has begun still sees it finished and published.
     */
    suspend fun set(enabled: Boolean) {
        access.withLock {
            // Once the write has begun it is finished together with its publication, even if the caller is cancelled
            // (the settings screen is left right after the switch): a write that reached the store and was not published
            // would leave the flow with the old choice until the next start.
            withContext(io + NonCancellable) {
                try {
                    store.write(Key, if (enabled) Yes else No)
                } catch (failure: Throwable) {
                    // A refusal that could not be saved still holds for this run: the safe side is to send nothing.
                    if (!enabled) {
                        state.value = false
                        loaded = true
                        // An older "yes" that can be read must not come back at the next start; nothing else is touched:
                        // `null` is both "no entry" and "cannot be read for the moment", and a "no" that is only hidden for
                        // now must not be erased. Best effort, as the write itself has failed.
                        runCatching { if (store.read(Key) == Yes) store.remove(Key) }
                    }
                    throw failure
                }
                state.value = enabled
                loaded = true
            }
        }
    }

    private companion object {
        const val Key = "analytics.statistics"
        const val Yes = "1"
        const val No = "0"
    }
}
