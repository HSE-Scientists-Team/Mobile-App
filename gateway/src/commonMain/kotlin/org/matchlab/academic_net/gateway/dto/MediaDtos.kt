package org.matchlab.academic_net.gateway.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The shapes of the files of the backend's OpenAPI v2.6.0 (schemas CreateMediaUpload, MediaUpload, MediaFile,
// MediaDownload): only what the app reads or sends.

/** `CreateMediaUpload`: what the file is, before its bytes are sent. [isPublic] files can be read by anyone (avatars). */
@Serializable
data class CreateMediaUploadDto(
    @SerialName("original_name") val originalName: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("is_public") val isPublic: Boolean = false,
)

/** `MediaFile`: [status] is `pending`, `ready`, `deleted` or `failed` (`MediaFileStatus`). */
@Serializable
data class MediaFileDto(
    val id: String = "",
    @SerialName("owner_user_id") val ownerUserId: String = "",
    @SerialName("original_name") val originalName: String = "",
    @SerialName("content_type") val contentType: String = "",
    @SerialName("size_bytes") @Serializable(with = FlexibleLongSerializer::class) val sizeBytes: Long = 0,
    val status: String = "",
    @SerialName("is_public") val isPublic: Boolean = false,
)

/** `MediaFileResult`: the answer of `complete`, the file is wrapped in `file` like in [MediaUploadDto]. */
@Serializable
data class MediaFileResultDto(val file: MediaFileDto = MediaFileDto())

/** `MediaUpload`: the new (pending) [file] and the signed request that takes its bytes: [method] (`PUT`) [uploadUrl] with [headers]. */
@Serializable
data class MediaUploadDto(
    val file: MediaFileDto = MediaFileDto(),
    @SerialName("upload_url") val uploadUrl: String = "",
    val method: String = "PUT",
    val headers: Map<String, String> = emptyMap(),
    @SerialName("expires_at_unix") @Serializable(with = FlexibleLongSerializer::class) val expiresAtUnix: Long = 0,
)

/** `MediaDownload`: a signed link to the content of a ready file, valid until [expiresAtUnix]. */
@Serializable
data class MediaDownloadDto(
    @SerialName("download_url") val downloadUrl: String = "",
    @SerialName("expires_at_unix") @Serializable(with = FlexibleLongSerializer::class) val expiresAtUnix: Long = 0,
)
