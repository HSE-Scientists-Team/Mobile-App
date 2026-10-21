package org.matchlab.academic_net.analytics

import org.matchlab.academic_net.securestore.InMemorySecureStore
import org.matchlab.academic_net.securestore.SecureStore

/**
 * A [SecureStore] behind a spy: it counts the reads, records the writes and the removals and can be told to fail, to show what the code
 * under test does with its storage. What it is seeded with ([seed], [put]) and what the test looks at ([peek]) are not
 * calls of the code under test and are not counted.
 */
internal class ScriptedStore(seed: Map<String, String> = emptyMap()) : SecureStore {
    private val inner = InMemorySecureStore().also { store -> seed.forEach { (key, value) -> store.write(key, value) } }

    /** How many times the code under test read the store. */
    var reads = 0
        private set

    /** Every write that was asked for, in order, also the ones that failed. */
    val attempts = mutableListOf<Pair<String, String>>()

    /** The writes that reached the store, in order. */
    val writes = mutableListOf<Pair<String, String>>()

    /** While set, a read fails with it. */
    var readFailure: Throwable? = null

    /** While `true`, a read finds nothing, as the platform does for a value that cannot be read: the store is unreadable. */
    var hidden = false

    /** While set, a write fails with it, and nothing is stored. */
    var writeFailure: Throwable? = null

    /** The keys the code under test asked to remove, in order, also when the removal failed. */
    val removals = mutableListOf<String>()

    /** While set, a removal fails with it, and nothing is removed. */
    var removeFailure: Throwable? = null

    /** How many times the code under test wiped the store. */
    var wipes = 0
        private set

    /** Called at the start of every read, with the key. */
    var onRead: ((String) -> Unit)? = null

    /** Called at the start of every write, before it is stored (or fails), with the key and the value. */
    var onWrite: ((String, String) -> Unit)? = null

    /** Stores [value] behind the back of the code under test, as a past run of the app did. */
    fun put(key: String, value: String) = inner.write(key, value)

    /** What is stored under [key], without counting as a read. */
    fun peek(key: String): String? = inner.read(key)

    override fun read(key: String): String? {
        reads++
        onRead?.invoke(key)
        readFailure?.let { throw it }
        return if (hidden) null else inner.read(key)
    }

    override fun write(key: String, value: String) {
        attempts += key to value
        onWrite?.invoke(key, value)
        writeFailure?.let { throw it }
        inner.write(key, value)
        writes += key to value
    }

    override fun remove(key: String) {
        removals += key
        removeFailure?.let { throw it }
        inner.remove(key)
    }

    override fun wipe() {
        wipes++
        inner.wipe()
    }
}
