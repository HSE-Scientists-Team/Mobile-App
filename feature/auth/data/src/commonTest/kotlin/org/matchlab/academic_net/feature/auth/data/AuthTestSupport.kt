package org.matchlab.academic_net.feature.auth.data

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Credentials
import org.matchlab.academic_net.feature.auth.domain.Password
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.securestore.SecureStore

/** A session as the tests of the storage and of the restoring keep it. */
internal val SavedSession = Session(UserId("user-me"), "student@hse.ru", AccessToken("secret"))

/** The password of the tests; no store, state or text may ever hold it. */
internal const val HunterPassword = "hunter2-hunter2"

internal const val HunterAddress = "student@hse.ru"

/** About eleven days of the test clock: "never" for a call that the code under test waits a few seconds for. */
internal const val NeverMillis = 1_000_000_000L

internal val Hunter = Credentials(HunterAddress, Password(HunterPassword))

/** The keys of the previous format of the saved session, three entries. */
internal fun SecureStore.writePreviousFormat(session: Session) {
    write("session.user_id", session.userId.value)
    write("session.email", session.email)
    write("session.access_token", session.accessToken.value)
}

/** Runs every block on [delegate] except the first one, which waits for [release]. */
internal class HoldFirstDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
    private var held: Runnable? = null
    private var firstSeen = false

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (firstSeen) {
            delegate.dispatch(context, block)
        } else {
            firstSeen = true
            held = block
        }
    }

    fun release() {
        held?.let { delegate.dispatch(EmptyCoroutineContext, it) }
        held = null
    }
}

/** A store whose [remove] fails [failingRemoves] times, then works; counts the calls. */
internal class FlakyStore(
    private val inner: SecureStore,
    var failingRemoves: Int = Int.MAX_VALUE,
    var failingWipe: Boolean = false,
) : SecureStore by inner {
    var wipes = 0
    var reads = 0
    var removes = 0

    override fun read(key: String): String? {
        reads++
        return inner.read(key)
    }

    override fun remove(key: String) {
        removes++
        if (failingRemoves > 0) {
            failingRemoves--
            error("cannot remove")
        }
        inner.remove(key)
    }

    override fun wipe() {
        wipes++
        if (failingWipe) error("cannot wipe")
        inner.wipe()
    }
}

/**
 * A store that says `null` for its first [unreadableReads] reads, as a keystore does that is locked
 * or busy for the moment (the contract of [SecureStore.read] cannot tell it from "nothing there"),
 * and then works. Counts the removals.
 */
internal class UnreadableStore(
    private val inner: SecureStore,
    var unreadableReads: Int = Int.MAX_VALUE,
) : SecureStore by inner {
    var removes = 0
    var wipes = 0

    override fun read(key: String): String? {
        if (unreadableReads > 0) {
            unreadableReads--
            return null
        }
        return inner.read(key)
    }

    override fun remove(key: String) {
        removes++
        inner.remove(key)
    }

    override fun wipe() {
        wipes++
        inner.wipe()
    }
}

/** A store that remembers every write, to show what reached the disk. */
internal class RecordingStore(private val inner: SecureStore) : SecureStore by inner {
    val writes = mutableListOf<Pair<String, String>>()

    override fun write(key: String, value: String) {
        writes += key to value
        inner.write(key, value)
    }
}

/** A store that says `null` for exactly one read, the [nth] (counting from 1), and works otherwise. */
internal class OneUnreadableReadStore(
    private val inner: SecureStore,
    private val nth: Int,
) : SecureStore by inner {
    private var reads = 0
    var removes = 0

    override fun read(key: String): String? {
        reads++
        return if (reads == nth) null else inner.read(key)
    }

    override fun remove(key: String) {
        removes++
        inner.remove(key)
    }
}
