package org.matchlab.academic_net.securestore

import android.content.Context
import java.io.IOException

/**
 * [KeyValueStorage] on the app's private [android.content.SharedPreferences] file [name].
 *
 * Writes use `commit()` and fail loudly: a secret that was reported as saved must have reached the
 * disk before the process can be killed. Callers are on an IO dispatcher.
 */
class SharedPreferencesStorage(context: Context, name: String = DEFAULT_NAME) : KeyValueStorage {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(key: String, value: String) {
        if (!preferences.edit().putString(key, value).commit()) throw IOException("Could not save the value")
    }

    override fun remove(key: String) {
        if (!preferences.edit().remove(key).commit()) throw IOException("Could not remove the value")
    }

    companion object {
        const val DEFAULT_NAME = "matchlab_secure"
    }
}
