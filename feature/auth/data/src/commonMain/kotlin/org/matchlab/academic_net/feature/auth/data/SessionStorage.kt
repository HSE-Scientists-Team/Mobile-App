package org.matchlab.academic_net.feature.auth.data

import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.securestore.SecureStore

/**
 * The saved [Session] and the address the user last used, in the [SecureStore].
 *
 * **The session is one entry** (`session.v1`), written by a single call, so that a process killed
 * in the middle of a write leaves either the old session or the new one, never a half. The value
 * is `v1`, the user id, the address and the token, each on a line of its own; the token comes last
 * and may contain anything. The password is never saved, here or anywhere.
 *
 * **Reading is careful not to lose the session.** [SecureStore.read] gives `null` both for a key
 * that was never written and for a value that cannot be read for the moment (the keystore is busy
 * or locked), so a `null` is never a reason to erase anything: the session is "not there", and the
 * next launch may find it. An entry that was read and is not a session of this version (another
 * version, a missing or blank field) will never become one, and only that is erased.
 *
 * **The previous format** kept three entries (`session.user_id`, `session.email`,
 * `session.access_token`). [load] still reads it when there is no new entry, and moves a complete
 * record to the new entry (write the new one, then erase the old ones).
 *
 * **The last address** (`session.last_email`) is a separate entry: it is not a secret, and a
 * sign-out leaves it, so that the sign-in screen can fill it in. [clear] does not touch it.
 *
 * The calls are blocking: use them from an IO dispatcher.
 */
class SessionStorage(private val store: SecureStore) {
    /**
     * The saved session, or `null` when there is none or it cannot be read now. A broken entry that
     * was read is erased; an entry that is missing, or only partly there in the old format (nothing
     * says whether it is broken or just not readable now), is left as it is.
     */
    fun load(): Session? {
        store.read(SessionKey)?.let { record ->
            decode(record)?.let { return it }
            // Read, and not a session: nothing can make it one. A failure to erase it is not the end
            // of the world; the old format may still hold a session.
            runCatching { store.remove(SessionKey) }
        }
        return loadPreviousFormat()
    }

    /**
     * Saves [session] with one write; throws when the store could not write it, or when the user id
     * or the address contains a line break (they never do; the token may).
     */
    fun save(session: Session) {
        store.write(SessionKey, encode(session))
    }

    /**
     * Erases the session, in the new format and in the old one. Every entry is tried even if one
     * fails; the first failure is rethrown. The last address stays.
     */
    fun clear() {
        var failure: Throwable? = null
        for (key in SessionKeys) {
            try {
                store.remove(key)
            } catch (error: Exception) {
                if (failure == null) failure = error
            }
        }
        failure?.let { throw it }
    }

    /**
     * Makes the whole store unreadable (see [SecureStore.wipe]); throws when it could not.
     * The last resort of a sign-out when [clear] keeps failing. The last address goes too.
     */
    fun wipe() {
        store.wipe()
    }

    /**
     * The address the user last registered or signed in with, or `null` when there is none or it cannot
     * be read now.
     */
    fun loadLastEmail(): String? = store.read(LastEmailKey)?.takeIf { it.isNotBlank() }

    /** Remembers [email] as the address the user last used; throws when the store could not write it. */
    fun saveLastEmail(email: String) {
        store.write(LastEmailKey, email)
    }

    /** Reads the three entries of the previous version, and moves a complete record to the new entry. */
    private fun loadPreviousFormat(): Session? {
        val userId = store.read(PreviousUserIdKey)
        val email = store.read(PreviousEmailKey)
        val token = store.read(PreviousTokenKey)

        // A missing entry may be one that cannot be read now: without all three there is no session to
        // trust, and nothing is erased for that.
        if (userId == null || email == null || token == null) return null

        if (userId.isBlank() || email.isBlank() || token.isBlank()) {
            // All three were read, and one is blank: broken for good.
            runCatching { clear() }
            return null
        }

        val session = Session(UserId(userId), email, AccessToken(token))
        // The old entries go only after the new one is in place; if anything fails the old record stays
        // good, and the move is tried again at the next launch.
        runCatching {
            save(session)
            for (key in PreviousKeys) store.remove(key)
        }
        return session
    }

    private fun encode(session: Session): String {
        require('\n' !in session.userId.value && '\n' !in session.email) {
            "The user id and the address of a session cannot contain a line break"
        }
        return listOf(Version, session.userId.value, session.email, session.accessToken.value)
            .joinToString(Separator.toString())
    }

    /** The session in [record], or `null` if it is not a complete record of this version. */
    private fun decode(record: String): Session? {
        val parts = record.split(Separator, limit = 4)
        if (parts.size != 4 || parts[0] != Version) return null
        val (_, userId, email, token) = parts
        if (userId.isBlank() || email.isBlank() || token.isBlank()) return null
        return Session(UserId(userId), email, AccessToken(token))
    }

    private companion object {
        const val SessionKey = "session.v1"
        const val LastEmailKey = "session.last_email"
        const val PreviousUserIdKey = "session.user_id"
        const val PreviousEmailKey = "session.email"
        const val PreviousTokenKey = "session.access_token"
        val PreviousKeys = listOf(PreviousUserIdKey, PreviousEmailKey, PreviousTokenKey)
        val SessionKeys = listOf(SessionKey) + PreviousKeys

        const val Version = "v1"
        const val Separator = '\n'
    }
}
