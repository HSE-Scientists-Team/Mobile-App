package org.matchlab.academic_net

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import org.matchlab.academic_net.securestore.InMemorySecureStore
import org.matchlab.academic_net.securestore.SecureStore

/**
 * A [SecureStore] behind a spy, for the tests of what the app does with its storage. It records every change that was asked
 * for (a write, a remove, a wipe; a failed write too), can hide what it holds (a value that cannot be read) and can fail
 * the write of chosen keys. What it is seeded with and what [peek] looks at are not changes of the app.
 *
 * The app reaches the store from its IO threads and the test reads it from its own, so the record is replaced as a whole.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class AuditedStore(seed: Map<String, String> = emptyMap()) : SecureStore {
    private val inner = InMemorySecureStore().also { store -> seed.forEach { (key, value) -> store.write(key, value) } }
    private val record = AtomicReference<List<String>>(emptyList())
    private val hiding = AtomicReference(false)
    private val failing = AtomicReference<Set<String>>(emptySet())

    /** Every change that was asked for, in order: `write <key>`, `remove <key>`, `wipe`. */
    val mutations: List<String> get() = record.load()

    /** While `true`, [read] finds nothing: the platform cannot read what is there (the keystore is locked). */
    var hidden: Boolean
        get() = hiding.load()
        set(value) = hiding.store(value)

    /** The keys whose write fails, as a locked keystore or a full disk does. */
    var failingKeys: Set<String>
        get() = failing.load()
        set(value) = failing.store(value)

    /** What is stored under [key] whatever [hidden] says. */
    fun peek(key: String): String? = inner.read(key)

    override fun read(key: String): String? = if (hidden) null else inner.read(key)

    override fun write(key: String, value: String) {
        note("write $key")
        if (key in failingKeys) throw IllegalStateException("the store cannot write $key")
        inner.write(key, value)
    }

    override fun remove(key: String) {
        note("remove $key")
        inner.remove(key)
    }

    override fun wipe() {
        note("wipe")
        inner.wipe()
    }

    private fun note(change: String) {
        while (true) {
            val current = record.load()
            if (record.compareAndSet(current, current + change)) return
        }
    }
}
