package org.matchlab.academic_net.securestore

/**
 * Key-value storage for secrets: what is written is not readable from the device's files without
 * the platform's key.
 *
 * The calls are blocking (the platform keystores are), so callers switch to an IO dispatcher.
 * [write], [remove] and [wipe] throw when the platform could not save the change; [read] returns
 * `null` both for a key that was never written and for a value that cannot be read (now, or any
 * more).
 */
interface SecureStore {
    /** The value written under [key], or `null` when there is none or it is unreadable. */
    fun read(key: String): String?

    /** Replaces the value under [key]. Throws when the value could not be stored. */
    fun write(key: String, value: String)

    /** Removes the value under [key]; no-op when there is none. Throws when it could not be removed. */
    fun remove(key: String)

    /**
     * Makes everything this store holds unreadable, without knowing the keys: the last resort when
     * [remove] keeps failing (a secret must not stay readable after sign-out). A store that is
     * wiped works again afterwards, it just starts empty. Throws when the data may still be
     * readable.
     */
    fun wipe()
}

/**
 * Reversible text encryption with a key that the platform keeps non-exportable (Android
 * Keystore). [encrypt] returns text safe to put in any [KeyValueStorage].
 *
 * [decrypt] distinguishes two kinds of failure, because the caller reacts to them differently:
 * [CorruptedSecretException] means this text can never be decrypted (damaged, written by
 * something else, the key is gone), and any other exception means a failure that may pass (the
 * keystore is busy or locked), after which the same text may still be readable.
 */
interface StringCipher {
    fun encrypt(plain: String): String

    /** The text behind [encoded]; throws [CorruptedSecretException] when it is lost for good. */
    fun decrypt(encoded: String): String

    /** Destroys the key, so everything encrypted so far is unreadable; [encrypt] makes a new one. */
    fun destroyKey()
}

/** Plain persistent key-value storage; [CipherSecureStore] puts only ciphertext into it. */
interface KeyValueStorage {
    fun get(key: String): String?

    fun put(key: String, value: String)

    fun remove(key: String)
}

/**
 * A stored secret that is lost for good: the ciphertext is damaged or has an unknown format or
 * version, its authentication failed, or the key that encrypted it no longer exists.
 *
 * [StringCipher.decrypt] throws it only for these cases, so that [CipherSecureStore] may delete
 * the value. A temporary failure of the keystore must not be reported with it: deleting would
 * sign the user out for good.
 */
class CorruptedSecretException(message: String, cause: Throwable? = null) : Exception(message, cause)
