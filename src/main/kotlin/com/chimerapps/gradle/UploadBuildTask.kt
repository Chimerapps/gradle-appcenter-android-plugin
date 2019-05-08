package com.chimerapps.gradle

import com.chimerapps.gradle.api.*
import com.squareup.moshi.Moshi
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.IOException
import java.time.Duration
import javax.inject.Inject

data class UploadTaskConfiguration(
    val apkFile: File,
    val mappingFile: File?,
    val buildNumber: Long,
    val buildVersion: String,
    val appCenterOwner: String,
    val appCenterAppName: String,
    val apiToken: String,
    val distributionTargets: List<String>,
    val notifyTesters: Boolean,
    val flavorName: String,
    val changeLog: String?
)

open class UploadBuildTask @Inject constructor(
    private val configuration: UploadTaskConfiguration
) : DefaultTask() {

    private companion object {
        private const val TIMEOUT_DURATION_SECONDS = 30L
    }

    private val moshi = Moshi.Builder().add(MoshiFactory()).build()

    init {
        group = "AppCenter"
        description = "Upload ${configuration.flavorName} to app center"

        dependsOn += project.tasks.findByName("assemble${configuration.flavorName.capitalize()}")
    }

    @TaskAction
    fun uploadBuild() {
//        val logging = HttpLoggingInterceptor(HttpLoggingInterceptor.Logger { message -> project.logger.warn(message) })
//
//        logging.level = HttpLoggingInterceptor.Level.BODY

        val client = OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(TIMEOUT_DURATION_SECONDS))
            .writeTimeout(Duration.ofSeconds(TIMEOUT_DURATION_SECONDS))
            .readTimeout(Duration.ofSeconds(TIMEOUT_DURATION_SECONDS))
            .connectTimeout(Duration.ofSeconds(TIMEOUT_DURATION_SECONDS))
            //.addInterceptor(logging)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.appcenter.ms/v0.1/apps/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(client)
            .build()

        try {
            uploadBuildUsingApi(retrofit.create(AppCenterMiniApi::class.java))
        } finally {
            client.dispatcher().executorService().shutdown()
            client.connectionPool().evictAll()
        }
    }

    private fun uploadBuildUsingApi(api: AppCenterMiniApi) {
        uploadRelease(api)
        if (configuration.mappingFile?.exists() == true)
            uploadMappingFile(api, configuration.mappingFile)
    }

    private fun uploadRelease(api: AppCenterMiniApi) {
        val prepareUploadResponse = checkResponse(
            api.prepareUpload(
                apiToken = configuration.apiToken,
                appName = configuration.appCenterAppName,
                owner = configuration.appCenterOwner
            ).execute()
        )

        val uploadResponse = api.uploadFile(
            prepareUploadResponse.uploadUrl, MultipartBody.Builder()
                .addFormDataPart("ipa", configuration.apkFile.name, RequestBody.create(null, configuration.apkFile))
                .build()
        ).execute()
        if (!uploadResponse.isSuccessful)
            throw IOException("Failed to upload release file. Code: ${uploadResponse.code()}")

        val commitReleaseResponse = checkResponse(
            api.commitReleaseUpload(
                apiToken = configuration.apiToken,
                appName = configuration.appCenterAppName,
                owner = configuration.appCenterOwner,
                uploadId = prepareUploadResponse.uploadId
            ).execute()
        )

        val distributeResponse = api.distributeRelease(
            apiToken = configuration.apiToken,
            appName = configuration.appCenterAppName,
            owner = configuration.appCenterOwner,
            releaseId = commitReleaseResponse.releaseId,
            body = DistributeReleaseRequest(
                configuration.distributionTargets.map { DistributeReleaseDestination(it) },
                notifyTesters = configuration.notifyTesters,
                releaseNotes = configuration.changeLog
            )
        ).execute()
        if (!distributeResponse.isSuccessful)
            throw IOException("Failed to distribute. Code: ${distributeResponse.code()}")
    }

    private fun uploadMappingFile(api: AppCenterMiniApi, mappingFile: File) {
        val response = checkResponse(
            api.prepareSymbolUpload(
                apiToken = configuration.apiToken,
                appName = configuration.appCenterAppName,
                owner = configuration.appCenterOwner,
                body = PrepareSymbolUploadRequest(
                    symbolType = "AndroidProguard",
                    build = configuration.buildNumber.toString(),
                    version = configuration.buildVersion,
                    fileName = mappingFile.name
                )
            ).execute()
        )

        val uploadResult = api.uploadSymbolFile(response.uploadUrl, RequestBody.create(null, mappingFile)).execute()
        if (!uploadResult.isSuccessful)
            throw IOException("Failed to upload mapping file. Code: ${uploadResult.code()}")

        val commitResponse = api.commitSymbolUpload(
            apiToken = configuration.apiToken,
            appName = configuration.appCenterAppName,
            owner = configuration.appCenterOwner,
            uploadId = response.uploadId
        ).execute()
        if (!commitResponse.isSuccessful)
            throw IOException("Failed to upload mapping file. Code: ${commitResponse.code()}")
    }

    private fun <T> checkResponse(response: Response<T>): T {
        if (!response.isSuccessful)
            throw IOException("Failed to communicate with AppCenter. Status code: ${response.code()} for ${response.raw().request().url()}")
        return response.body() ?: throw IOException("Failed to communicate with AppCenter. Body expected")
    }

}