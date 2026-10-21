package org.matchlab.academic_net.securestore

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * [SecureStore] that keeps values in memory only and encrypts nothing: for tests, previews and
 * the "persistence unavailable" fallback, where a secret must not outlive the process anyway.
 *
 * Safe to call from several threads: the map is replaced as a whole.
 */
@OptIn(ExperimentalAtomicApi::class)
class InMemorySecureStore : SecureStore {
    private val values = AtomicReference<Map<String, String>>(emptyMap())

    override fun read(key: String): String? = values.load()[key]

    override fun write(key: String, value: String) {
        update { it + (key to value) }
    }

    override fun remove(key: String) {
        update { it - key }
    }

    override fun wipe() {
        update { emptyMap() }
    }

    private inline fun update(transform: (Map<String, String>) -> Map<String, String>) {
        while (true) {
            val current = values.load()
            if (values.compareAndSet(current, transform(current))) return
        }
    }
}
