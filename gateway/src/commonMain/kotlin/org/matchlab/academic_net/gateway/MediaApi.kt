package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.CreateMediaUploadDto
import org.matchlab.academic_net.gateway.dto.MediaDownloadDto
import org.matchlab.academic_net.gateway.dto.MediaFileDto
import org.matchlab.academic_net.gateway.dto.MediaUploadDto

/**
 * The files of the backend (`/api/v1/media/files…`, OpenAPI v2.6.0). A file is made in four steps: [createUpload]
 * (a pending record and a signed request), [putContent] (the bytes go straight to the storage, not through the
 * Gateway, and without the token), [complete] (the server checks the object and the file becomes `ready`). A ready
 * file is read with [downloadUrl] and [fetch]. [uploadFile] does the three steps in a row.
 *
 * Every call throws `RemoteFailure` when it fails and lets a `CancellationException` through, like [AccountApi].
 * Ids go into paths escaped.
 */
interface MediaApi {
    /** `POST /media/files` → 201: a pending file and the signed request for its bytes. */
    suspend fun createUpload(input: CreateMediaUploadDto): MediaUploadDto

    /**
     * Sends [bytes] with the signed request of [upload] (its method, its headers). The call goes to the storage: the
     * token of the session is **not** sent there. Any 2xx answer is a success.
     */
    suspend fun putContent(upload: MediaUploadDto, bytes: ByteArray)

    /** `POST /media/files/{id}/complete` → 200: the file after the check; a repeat gives the same ready file. */
    suspend fun complete(fileId: String): MediaFileDto

    /** `GET /media/files/{id}/download-url` → 200: a temporary link to the content of a ready file (public files are open to anyone). */
    suspend fun downloadUrl(fileId: String): MediaDownloadDto

    /** The content behind [url] (a link of [downloadUrl]), read from the storage without the token of the session. */
    suspend fun fetch(url: String): ByteArray

    /** `DELETE /media/files/{id}` → 204: the file of the viewer is removed (a repeat is 204 too). */
    suspend fun delete(fileId: String)
}

/**
 * Uploads [bytes] as a new file of the viewer and returns it `ready`: [MediaApi.createUpload] with the size of [bytes],
 * [MediaApi.putContent], [MediaApi.complete]. A failure of any step is thrown as it is; the pending record of a
 * failed upload is cleaned up by the server (24 hours); an answer of `complete` with no file id is `RemoteFailure.Malformed`.
 *
 * @param isPublic public files (avatars) can be read by anyone; the rest only by their owner (proof of an achievement)
 */
suspend fun MediaApi.uploadFile(name: String, contentType: String, bytes: ByteArray, isPublic: Boolean): MediaFileDto {
    val upload = createUpload(CreateMediaUploadDto(name, contentType, bytes.size.toLong(), isPublic))
    putContent(upload, bytes)
    // An answer without an id is not a file: it would be written into a profile as an empty id.
    return complete(upload.file.id).takeIf { it.id.isNotBlank() } ?: throw RemoteFailure.Malformed()
}
