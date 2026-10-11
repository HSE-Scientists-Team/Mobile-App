package org.matchlab.academic_net.feature.auth.data

import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.securestore.SecureStore

/**
 * The saved [Session] in the [SecureStore]: three entries (user id, e-mail, token).
 *
 * The calls are blocking: use them from an IO dispatcher.
 */
class SessionStorage(private val store: SecureStore) {
    /**
     * The saved session, or `null` when there is none. A partial record (some entries missing or
     * blank, the app was killed in the middle of a write) is not a session: it is erased.
     */
    fun load(): Session? {
        val userId = store.read(UserIdKey)
        val email = store.read(EmailKey)
        val token = store.read(TokenKey)

        if (userId.isNullOrBlank() || email.isNullOrBlank() || token.isNullOrBlank()) {
            if (userId != null || email != null || token != null) clear()
            return null
        }
        return Session(UserId(userId), email, AccessToken(token))
    }

    /** Saves [session]; throws when the store could not write it. The token goes last, so a failed write is no session. */
    fun save(session: Session) {
        store.write(UserIdKey, session.userId.value)
        store.write(EmailKey, session.email)
        store.write(TokenKey, session.accessToken.value)
    }

    /** Erases the session. Every entry is tried even if one fails; the first failure is rethrown. */
    fun clear() {
        var failure: Throwable? = null
        for (key in Keys) {
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
     * The last resort of a sign-out when [clear] keeps failing.
     */
    fun wipe() {
        store.wipe()
    }

    private companion object {
        const val UserIdKey = "session.user_id"
        const val EmailKey = "session.email"
        const val TokenKey = "session.access_token"
        val Keys = listOf(UserIdKey, EmailKey, TokenKey)
    }
}
