package org.matchlab.academic_net.securestore

import android.content.Context

/**
 * The Android [SecureStore]: values are encrypted with an Android Keystore key and kept in
 * SharedPreferences. A value restored from a backup onto another device cannot be decrypted (the
 * key does not travel), and is read as `null`. [wipe] deletes the key from the Keystore, which
 * makes whatever stays in SharedPreferences unreadable.
 */
class AndroidSecureStore(context: Context) : SecureStore by CipherSecureStore(
    storage = SharedPreferencesStorage(context),
    cipher = AndroidKeystoreCipher(),
)
