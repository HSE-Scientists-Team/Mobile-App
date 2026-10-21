package org.matchlab.academic_net.securestore

/**
 * [SecureStore] made of a plain [KeyValueStorage] and a [StringCipher]: the storage only ever sees
 * ciphertext.
 *
 * A stored value that is lost for good (the cipher reports [CorruptedSecretException]: the app's
 * data was restored from a backup onto another device, the keystore key is gone) is removed and
 * read as `null`, so the caller treats it like "nothing was saved" instead of failing on every
 * launch. Any other failure of the cipher is taken for a temporary one: the value is read as
 * `null` this time but stays in the storage, and the next read tries it again.
 */
class CipherSecureStore(
    private val storage: KeyValueStorage,
    private val cipher: StringCipher,
) : SecureStore {
    override fun read(key: String): String? {
        val encoded = storage.get(key) ?: return null
        return try {
            cipher.decrypt(encoded)
        } catch (lost: CorruptedSecretException) {
            // Drop the value; if even that fails, the next read just finds it corrupted again.
            try {
                storage.remove(key)
            } catch (ignored: Exception) {
            }
            null
        } catch (temporary: Exception) {
            null
        }
    }

    override fun write(key: String, value: String) {
        storage.put(key, cipher.encrypt(value))
    }

    override fun remove(key: String) {
        storage.remove(key)
    }

    /** Destroys the cipher's key: the ciphertext that is left in the storage can no longer be read. */
    override fun wipe() {
        cipher.destroyKey()
    }
}
