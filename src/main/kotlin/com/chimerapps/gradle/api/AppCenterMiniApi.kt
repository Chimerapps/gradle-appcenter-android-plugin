package com.chimerapps.gradle.api

import com.chimerapps.moshigenerator.GenerateMoshi
import com.chimerapps.moshigenerator.GenerateMoshiFactory
import com.squareup.moshi.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.slf4j.Logger
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*
import java.io.File
import java.io.IOException

fun createPrepareBinaryUrl(
    prepareUrl: PrepareReleaseResponse,
    file: File,
): String {
    return prepareUrl.uploadDomain.toHttpUrl().newBuilder()
        .encodedPath("/upload/set_metadata/${prepareUrl.packageAssetId}")
        .addQueryParameter("file_name", file.name)
        .addQueryParameter("file_size", file.length().toString())
        .addQueryParameter("content_type", "application/vnd.android.package-archive")
        .addEncodedQueryParameter("token", prepareUrl.urlEncodedToken)
        .build()
        .toString()
}

fun prepareBinaryChunkUrl(
    prepareUrl: PrepareReleaseResponse,
    blockNumber: Int,
): String {
    return prepareUrl.uploadDomain.toHttpUrl().newBuilder()
        .encodedPath("/upload/upload_chunk/${prepareUrl.packageAssetId}")
        .addEncodedQueryParameter("token", prepareUrl.urlEncodedToken)
        .addEncodedQueryParameter("block_number", blockNumber.toString())
        .build()
        .toString()
}

fun prepareFinishUploadUrl(
    prepareUrl: PrepareReleaseResponse,
): String {
    return prepareUrl.uploadDomain.toHttpUrl().newBuilder()
        .encodedPath("/upload/finished/${prepareUrl.packageAssetId}")
        .addEncodedQueryParameter("token", prepareUrl.urlEncodedToken)
        .build()
        .toString()
}

fun AppCenterMiniApi.uploadBinaryChunked(
    prepareUrl: PrepareReleaseResponse,
    file: File,
    chunkInfo: PrepareBinaryUploadResponse,
): Response<ChunkUploadResponse> {
    lateinit var lastOkResponse: Response<ChunkUploadResponse>
    var stop = false
    var blockIndex = 1
    file.forEachBlock(chunkInfo.chunkSize.toInt()) { block, size ->
        if (stop) return@forEachBlock

        val url = prepareBinaryChunkUrl(prepareUrl, blockIndex++)
        val response = uploadBinaryChunk(
            url = url,
            requestBody = block.toRequestBody("application/octet-stream".toMediaTypeOrNull(), byteCount = size)
        ).execute()
        if (!response.isSuccessful || response.body()!!.error) {
            lastOkResponse = response
            stop = true
        } else {
            lastOkResponse = response
        }
    }
    return lastOkResponse
}

fun AppCenterMiniApi.pollForReleaseId(
    apiToken: String,
    owner: String,
    appName: String,
    uploadId: String,
    logger: Logger?
): Int {

    while (true) {
        val response = getReleaseStatus(
            apiToken = apiToken,
            owner = owner,
            appName = appName,
            uploadId = uploadId,
        ).execute()
        if (!response.isSuccessful) {
            response.errorBody()?.let {
                logger?.error("Failed to communicate with AppCenter. Status code: ${response.code()} - ${response.message()}. Body:\n${it.string()}")
            }
            throw IOException("Failed to communicate with AppCenter. Status code: ${response.code()} - ${response.message()} for ${response.raw().request.url}")
        }
        val body = response.body() ?: throw IOException("Failed to communicate with AppCenter. Body expected")
        when (body.uploadStatus) {
            CheckReleaseStatusResponse.UPLOAD_STATUS_READY_FOR_PUBLISH -> return body.releaseId!!
            CheckReleaseStatusResponse.UPLOAD_STATUS_ERROR -> throw IOException("Failed to communicate with AppCenter -> ${body.errorDetails}}")
            else -> Thread.sleep(1000L)
        } //Wait 1 second before trying again
    }
}

interface AppCenterMiniApi {

    @POST("{appCenterOwner}/{appCenterAppName}/uploads/releases")
    fun prepareUpload(
        @Header("X-API-Token") apiToken: String,
        @Path("appCenterOwner") owner: String, @Path("appCenterAppName") appName: String,
        @Body body: RequestBody = "{}".toRequestBody("application/json".toMediaTypeOrNull())
    ): Call<PrepareReleaseResponse>

    @POST
    fun prepareBinaryUpload(@Url url: String): Call<PrepareBinaryUploadResponse>

    @POST
    fun uploadBinaryChunk(@Url url: String, @Body requestBody: RequestBody): Call<ChunkUploadResponse>

    @POST
    fun finalizeBinaryUpload(@Url url: String): Call<ChunkUploadResponse>

    @PATCH("{appCenterOwner}/{appCenterAppName}/uploads/releases/{uploadId}")
    fun commitBinaryUploadStatus(
        @Header("X-API-Token") apiToken: String,
        @Path("appCenterOwner") owner: String,
        @Path("appCenterAppName") appName: String,
        @Path("uploadId") uploadId: String,
        @Body body: PatchUploadStatusRequest = PatchUploadStatusRequest("uploadFinished")
    ): Call<ResponseBody>

    @GET("{appCenterOwner}/{appCenterAppName}/uploads/releases/{uploadId}")
    fun getReleaseStatus(
        @Header("X-API-Token") apiToken: String,
        @Path("appCenterOwner") owner: String,
        @Path("appCenterAppName") appName: String,
        @Path("uploadId") uploadId: String,
    ): Call<CheckReleaseStatusResponse>

    @PUT("{appCenterOwner}/{appCenterAppName}/releases/{releaseId}")
    fun updateReleaseNotes(
        @Header("X-API-Token") apiToken: String,
        @Path("appCenterOwner") owner: String,
        @Path("appCenterAppName") appName: String,
        @Path("releaseId") releaseId: Int,
        @Body body: UpdateReleaseRequest
    ): Call<ResponseBody>

    @GET("{appCenterOwner}/{appCenterAppName}/distribution_groups/{name}")
    fun getDistributionGroup(
        @Header("X-API-Token") apiToken: String,
        @Path("appCenterOwner") owner: String,
        @Path("appCenterAppName") appName: String,
        @Path("name") name: String,
    ): Call<DistributionGroupResponse>

    @POST("{appCenterOwner}/{appCenterAppName}/releases/{releaseId}/groups")
    fun addDistributionGroup(
        @Header("X-API-Token") apiToken: String,
        @Path("appCenterOwner") owner: String,
        @Path("appCenterAppName") appName: String,
        @Path("releaseId") releaseId: Int,
        @Body body: AddDistributionGroupRequest,
    ): Call<ResponseBody>

    @POST("{appCenterOwner}/{appCenterAppName}/symbol_uploads")
    fun prepareSymbolUpload(
        @Header("X-API-Token") apiToken: String,
        @Path("appCenterOwner") owner: String, @Path("appCenterAppName") appName: String,
        @Body body: PrepareSymbolUploadRequest
    ): Call<PrepareSymbolUploadResponse>

    @PUT
    @Headers("x-ms-blob-type: BlockBlob")
    fun uploadSymbolFile(@Url url: String, @Body body: RequestBody): Call<ResponseBody>

    @PATCH("{appCenterOwner}/{appCenterAppName}/symbol_uploads/{uploadId}")
    fun commitSymbolUpload(
        @Header("X-API-Token") apiToken: String,
        @Path("appCenterOwner") owner: String,
        @Path("appCenterAppName") appName: String,
        @Path("uploadId") uploadId: String,
        @Body body: CommitRequest = CommitRequest("committed")
    ): Call<ResponseBody>

}

@GenerateMoshi
data class PrepareReleaseResponse(
    val id: String,
    @Json(name = "upload_domain") val uploadDomain: String,
    @Json(name = "token") val token: String,
    @Json(name = "url_encoded_token") val urlEncodedToken: String,
    @Json(name = "package_asset_id") val packageAssetId: String,
)

@GenerateMoshi
data class PrepareBinaryUploadResponse(
    @Json(name = "chunk_size") val chunkSize: Long,
)

@GenerateMoshi
data class CommitRequest(
    val status: String
)

@GenerateMoshi
data class PrepareSymbolUploadRequest(
    @Json(name = "symbol_type") @field:Json(name = "symbol_type") val symbolType: String,
    @Json(name = "build") @field:Json(name = "build") val build: String,
    @Json(name = "version") @field:Json(name = "version") val version: String,
    @Json(name = "file_name") @field:Json(name = "file_name") val fileName: String
)

@GenerateMoshi
data class PrepareSymbolUploadResponse(
    @Json(name = "upload_url") val uploadUrl: String,
    @Json(name = "symbol_upload_id") val uploadId: String
)

@GenerateMoshi
data class ChunkUploadResponse(
    val error: Boolean,
)

@GenerateMoshi
data class PatchUploadStatusRequest(
    @field:Json(name = "upload_status") @Json(name = "upload_status") val upload_status: String
)


@GenerateMoshi
data class CheckReleaseStatusResponse(
    val id: String,
    @Json(name = "upload_status") val uploadStatus: String,
    @Json(name = "error_details") val errorDetails: String?,
    @Json(name = "release_distinct_id") val releaseId: Int?,
) {
    companion object {
        const val UPLOAD_STATUS_READY_FOR_PUBLISH = "readyToBePublished"
        const val UPLOAD_STATUS_ERROR = "error"
    }
}

@GenerateMoshi
data class UpdateReleaseRequest(
    @field:Json(name = "release_notes") @Json(name = "release_notes") val releaseNotes: String
)

@GenerateMoshi
data class DistributionGroupResponse(
    val id: String
)

@GenerateMoshi
data class AddDistributionGroupRequest(
    val id: String,
    @field:Json(name="mandatory_update") @Json(name="mandatory_update")  val mandatoryUpdate: Boolean,
    @field:Json(name="notify_testers") @Json(name="notify_testers") val notifyTesters: Boolean,
)

@GenerateMoshiFactory(
    PrepareReleaseResponse::class,
    CommitRequest::class,
    PrepareSymbolUploadRequest::class,
    PrepareSymbolUploadResponse::class,
    PrepareBinaryUploadResponse::class,
    ChunkUploadResponse::class,
    CheckReleaseStatusResponse::class,
    PatchUploadStatusRequest::class,
    UpdateReleaseRequest::class,
    DistributionGroupResponse::class,
    AddDistributionGroupRequest::class,
)
interface ModelHolder