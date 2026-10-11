package org.matchlab.academic_net.securestore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64

/**
 * [StringCipher] on an AES-256 key that lives in the Android Keystore under [alias] and never
 * leaves it (hardware-backed where the device has it).
 *
 * The mode is `AES/GCM/NoPadding`: every [encrypt] gets a fresh 12-byte IV from the cipher, the
 * authentication tag is 128 bits, so a modified ciphertext fails to decrypt. The encoded form is
 * `v1:` + Base64 of IV followed by ciphertext with the tag; the prefix leaves room to change the
 * format later. The key is created on the first [encrypt].
 *
 * [decrypt] reports [CorruptedSecretException] only for what cannot be repaired by trying again:
 * an unknown format or version, bad Base64, a failed authentication tag, a key that is missing or
 * permanently invalid. Everything else (the keystore daemon is busy, the provider failed) is
 * rethrown as it is: it is a temporary failure and the stored value may be fine.
 */
class AndroidKeystoreCipher(private val alias: String = DEFAULT_ALIAS) : StringCipher {
    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: createKey())
        val encrypted = cipher.doFinal(plain.encodeToByteArray())
        return VERSION_PREFIX + Base64.encode(cipher.iv + encrypted)
    }

    override fun decrypt(encoded: String): String {
        val bytes = parse(encoded)
        // A key that is gone cannot come back: do not create a new one just to fail the tag check.
        val key = existingKey() ?: throw CorruptedSecretException("The key is lost")

        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, bytes, 0, IV_SIZE_BYTES))
            return cipher.doFinal(bytes, IV_SIZE_BYTES, bytes.size - IV_SIZE_BYTES).decodeToString()
        } catch (failed: BadPaddingException) {
            // AEADBadTagException is a subclass: the ciphertext or the tag was changed.
            throw CorruptedSecretException("The ciphertext failed authentication", failed)
        } catch (failed: IllegalBlockSizeException) {
            throw CorruptedSecretException("The ciphertext has a wrong length", failed)
        } catch (failed: KeyPermanentlyInvalidatedException) {
            throw CorruptedSecretException("The key is permanently invalidated", failed)
        }
    }

    override fun destroyKey() {
        loadKeyStore().deleteEntry(alias)
    }

    /** The IV and the ciphertext with the tag of [encoded]; [CorruptedSecretException] when it is not ours. */
    private fun parse(encoded: String): ByteArray {
        if (!encoded.startsWith(VERSION_PREFIX)) throw CorruptedSecretException("Unknown ciphertext format")
        val bytes = try {
            Base64.decode(encoded.removePrefix(VERSION_PREFIX))
        } catch (bad: IllegalArgumentException) {
            throw CorruptedSecretException("The ciphertext is not valid Base64", bad)
        }
        if (bytes.size <= IV_SIZE_BYTES) throw CorruptedSecretException("The ciphertext is too short")
        return bytes
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /**
     * The keystore key, or `null` when there is none or the stored one is permanently unusable
     * (its entry is then deleted, the data under it is gone anyway). A keystore that only failed
     * for now throws instead: the key must not be deleted because of a busy daemon.
     */
    private fun existingKey(): SecretKey? {
        val keyStore = loadKeyStore()
        return try {
            keyStore.getKey(alias, null) as? SecretKey
        } catch (unusable: UnrecoverableKeyException) {
            if (unusable.isTemporaryKeystoreFailure()) throw unusable
            keyStore.deleteEntry(alias)
            null
        }
    }

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    /**
     * Whether the keystore itself marked the failure behind this exception as transient (API 33+
     * tells it; older versions give no way to know, and the key is taken for lost).
     */
    private fun Throwable.isTemporaryKeystoreFailure(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        var current: Throwable? = this
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current is android.security.KeyStoreException && current.isTransientFailure) return true
            current = current.cause
            depth++
        }
        return false
    }

    companion object {
        const val DEFAULT_ALIAS = "matchlab_session_key"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val VERSION_PREFIX = "v1:"
        private const val KEY_SIZE_BITS = 256
        private const val IV_SIZE_BYTES = 12
        private const val TAG_SIZE_BITS = 128
        private const val MAX_CAUSE_DEPTH = 5
    }
}
