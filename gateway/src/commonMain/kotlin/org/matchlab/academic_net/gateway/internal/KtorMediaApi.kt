package org.matchlab.academic_net.gateway.internal

import org.matchlab.academic_net.gateway.MediaApi
import org.matchlab.academic_net.gateway.dto.CreateMediaUploadDto
import org.matchlab.academic_net.gateway.dto.MediaDownloadDto
import org.matchlab.academic_net.gateway.dto.MediaFileDto
import org.matchlab.academic_net.gateway.dto.MediaFileResultDto
import org.matchlab.academic_net.gateway.dto.MediaUploadDto

/** [MediaApi] over the client of a session (the API) and [ExternalRequests] (the storage). */
internal class KtorMediaApi(
    private val requests: ApiRequests,
    private val external: ExternalRequests,
) : MediaApi {
    override suspend fun createUpload(input: CreateMediaUploadDto): MediaUploadDto =
        requests.post("media/files", input)

    override suspend fun putContent(upload: MediaUploadDto, bytes: ByteArray) {
        require(upload.uploadUrl.isNotBlank()) { "The upload has no address" }
        external.put(upload.uploadUrl, upload.headers, bytes)
    }

    override suspend fun complete(fileId: String): MediaFileDto =
        requests.postEmpty<MediaFileResultDto>("media/files/${pathSegment(fileId)}/complete").file

    override suspend fun downloadUrl(fileId: String): MediaDownloadDto =
        requests.get("media/files/${pathSegment(fileId)}/download-url")

    override suspend fun fetch(url: String): ByteArray = external.get(url)

    override suspend fun delete(fileId: String) {
        requests.delete("media/files/${pathSegment(fileId)}")
    }
}
