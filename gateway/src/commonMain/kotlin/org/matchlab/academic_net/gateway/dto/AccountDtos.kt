package org.matchlab.academic_net.gateway.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The shapes of the account calls of docs/openapi.yaml (schemas Credentials, RegistrationResult, ConfirmEmailRequest,
// LoginResult, UserId). Fields are named as the server names them, in snake_case. Whatever holds a secret prints
// itself masked, so that a log line or a crash report never carries a password or a token.

/** Mail and password of `POST /auth/register` and `POST /auth/login`. Prints as `CredentialsDto(***)`. */
@Serializable
data class CredentialsDto(val email: String, val password: String) {
    override fun toString(): String = "CredentialsDto(***)"
}

/** Answer of `POST /auth/register`: `status` is `pending` until the mail is confirmed. */
@Serializable
data class RegistrationResultDto(val status: String = "")

/** Body of `POST /auth/email/confirm`: the one-time token of the letter. Prints without the token. */
@Serializable
data class ConfirmEmailRequestDto(val token: String) {
    override fun toString(): String = "ConfirmEmailRequestDto(token=***)"
}

/** Answer of `POST /auth/login`: the user and the token of the new session. Prints without the token. */
@Serializable
data class LoginResultDto(
    @SerialName("user_id") val userId: String,
    @SerialName("access_token") val accessToken: String,
) {
    override fun toString(): String = "LoginResultDto(userId=$userId, accessToken=***)"
}

/** Answer of `GET /sessions/current`: whose session the token belongs to. */
@Serializable
data class UserIdDto(@SerialName("user_id") val userId: String)
