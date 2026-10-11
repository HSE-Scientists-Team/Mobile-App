package org.matchlab.academic_net.feature.auth.data

import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.CodeRequestOutcome
import org.matchlab.academic_net.feature.auth.domain.CodeVerificationOutcome

/**
 * The sign-in server: the backend (not there yet) or the mock that plays it. The addresses are
 * normalized (trimmed, lower case) by the caller. A call throws when it fails (no network).
 */
interface AuthDataSource {
    /** Sends a new code to [email] and cancels the previous one. */
    suspend fun requestCode(email: String): CodeRequestOutcome

    /** Checks [code] for [email]; the account is created if the address has none. */
    suspend fun verifyCode(email: String, code: String): CodeVerificationOutcome

    /** Revokes [token]: the device is signed out on the server. */
    suspend fun revoke(token: AccessToken)
}
