package com.chimerapps.gradle.api

import com.chimerapps.moshigenerator.GenerateMoshi
import com.chimerapps.moshigenerator.GenerateMoshiFactory
import com.squareup.moshi.Json
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

interface AppCenterMiniApi {

    @POST("{appCenterOwner}/{appCenterAppName}/release_uploads")
    fun prepareUpload(
        @Header("X-API-Token") apiToken: String,
        @Path("appCenterOwner") owner: String, @Path("appCenterAppName") appName: String,
        @Body body: RequestBody = "".toRequestBody("application/json".toMediaTypeOrNull())
    ): Call<PrepareReleaseResponse>

    @POST
    fun uploadFile(@Url url: String, @Body body: RequestBody): Call<ResponseBody>

    @PATCH("{appCenterOwner}/{appCenterAppName}/release_uploads/{uploadId}")
    fun commitReleaseUpload(
        @Header("X-API-Token") apiToken: String,
        @Path("appCenterOwner") owner: String,
        @Path("appCenterAppName") appName: String,
        @Path("uploadId") uploadId: String,
        @Body body: CommitRequest = CommitRequest("committed")
    ): Call<CommitReleaseResponse>

    @PATCH("{appCenterOwner}/{appCenterAppName}/releases/{releaseId}")
    fun distributeRelease(
        @Header("X-API-Token") apiToken: String,
        @Path("appCenterOwner") owner: String,
        @Path("appCenterAppName") appName: String,
        @Path("releaseId") releaseId: String,
        @Body body: DistributeReleaseRequest
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
    @Json(name = "upload_url") val uploadUrl: String,
    @Json(name = "upload_id") val uploadId: String
)

@GenerateMoshi
data class CommitReleaseResponse(
    @Json(name = "release_id") val releaseId: String
)

@GenerateMoshi
data class CommitRequest(
    val status: String
)

@GenerateMoshi
data class DistributeReleaseRequest(
    val destinations: List<DistributeReleaseDestination>,
    @Json(name = "notify_testers") @field:Json(name = "notify_testers") val notifyTesters: Boolean,
    @Json(name = "release_notes") @field:Json(name = "release_notes") val releaseNotes: String?
)

@GenerateMoshi
data class DistributeReleaseDestination(
    val name: String
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

@GenerateMoshiFactory(
    PrepareReleaseResponse::class,
    CommitReleaseResponse::class,
    CommitRequest::class,
    DistributeReleaseRequest::class,
    DistributeReleaseDestination::class,
    PrepareSymbolUploadRequest::class,
    PrepareSymbolUploadResponse::class
)
interface ModelHolder