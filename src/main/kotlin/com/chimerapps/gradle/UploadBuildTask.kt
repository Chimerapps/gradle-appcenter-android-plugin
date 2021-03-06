package com.chimerapps.gradle

import com.chimerapps.gradle.api.*
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.gradle.api.DefaultTask
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.TaskAction
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.IOException
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class UploadTaskConfiguration(
    val apkFile: File,
    val mappingFileProvider: () -> File?,
    val buildNumber: Long,
    val buildVersion: String,
    val appCenterOwner: String,
    val appCenterAppName: String,
    val apiToken: String,
    val distributionTargets: List<String>,
    val notifyTesters: Boolean,
    val flavorName: String,
    val changeLog: String?,
    val maxRetries: Int,
    val assembleTaskName: String
)

open class UploadBuildTask @Inject constructor(
    private val configuration: UploadTaskConfiguration
) : DefaultTask() {

    private companion object {
        private const val TIMEOUT_DURATION_SECONDS = 60L
    }

    private val moshi = Moshi.Builder().add(MoshiFactory()).build()

    init {
        group = "AppCenter"
        description = "Upload ${configuration.flavorName} to app center"

        dependsOn += project.tasks.findByName("assemble${configuration.assembleTaskName.capitalize()}")
        timeout.set(Duration.ofDays(1))
    }

    @TaskAction
    fun uploadBuild() {

        val builder = OkHttpClient.Builder()
            .writeTimeout(TIMEOUT_DURATION_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_DURATION_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(TIMEOUT_DURATION_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor(maxRetries = configuration.maxRetries, logger = project.logger))

        if (project.logger.isEnabled(LogLevel.INFO)) {
            val logger = HttpLoggingInterceptor { message ->
                project.logger.info("[AppCenter] - (${Date()}) - $message")
            }
            if (project.logger.isEnabled(LogLevel.DEBUG))
                logger.level = HttpLoggingInterceptor.Level.BODY
            else
                logger.level = HttpLoggingInterceptor.Level.BASIC
            logger.redactHeader("X-API-Token")
            builder.addInterceptor(logger)
        }

        val client = builder.build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.appcenter.ms/v0.1/apps/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(client)
            .build()

        project.logger.info("[AppCenter] - (${Date()}) - Starting upload")
        uploadBuildUsingApi(retrofit.create(AppCenterMiniApi::class.java))
        project.logger.info("[AppCenter] - (${Date()}) - Upload finished")
    }

    private fun uploadBuildUsingApi(api: AppCenterMiniApi) {
        uploadRelease(api)
        val mappingFile = configuration.mappingFileProvider()
        if (mappingFile?.exists() == true)
            uploadMappingFile(api, mappingFile)
    }

    private fun uploadRelease(api: AppCenterMiniApi) {
        val distributionIds = configuration.distributionTargets.map {
            checkResponse(
                api.getDistributionGroup(
                    apiToken = configuration.apiToken,
                    owner = configuration.appCenterOwner,
                    appName = configuration.appCenterAppName,
                    name = it
                ).execute()
            ).id
        }

        val prepareUploadResponse = checkResponse(
            api.prepareUpload(
                apiToken = configuration.apiToken,
                appName = configuration.appCenterAppName,
                owner = configuration.appCenterOwner
            ).execute()
        )

        val prepareBinaryUploadResponse = checkResponse(api.prepareBinaryUpload(
            createPrepareBinaryUrl(prepareUploadResponse, configuration.apkFile)
        ).execute())

        checkResponse(api.uploadBinaryChunked(prepareUploadResponse, configuration.apkFile, prepareBinaryUploadResponse))

        checkResponse(api.finalizeBinaryUpload(prepareFinishUploadUrl(prepareUploadResponse)).execute())

        checkResponse(
            api.commitBinaryUploadStatus(
                apiToken = configuration.apiToken,
                owner = configuration.appCenterOwner,
                appName = configuration.appCenterAppName,
                uploadId = prepareUploadResponse.id,
            ).execute()
        )

        val releaseId = api.pollForReleaseId(
            apiToken = configuration.apiToken,
            owner = configuration.appCenterOwner,
            appName = configuration.appCenterAppName,
            uploadId = prepareUploadResponse.id,
            logger = project.logger,
        )

        if (!configuration.changeLog.isNullOrBlank()) {
            checkResponse(
                api.updateReleaseNotes(
                    apiToken = configuration.apiToken,
                    owner = configuration.appCenterOwner,
                    appName = configuration.appCenterAppName,
                    releaseId = releaseId,
                    body = UpdateReleaseRequest(configuration.changeLog)
                ).execute()
            )
        }

        distributionIds.forEach { groupId ->
            checkResponse(
                api.addDistributionGroup(
                    apiToken = configuration.apiToken,
                    owner = configuration.appCenterOwner,
                    appName = configuration.appCenterAppName,
                    releaseId = releaseId,
                    body = AddDistributionGroupRequest(id = groupId,
                        notifyTesters = configuration.notifyTesters,
                        mandatoryUpdate = false,
                    )
                ).execute()
            )
        }
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

        val uploadResult = api.uploadSymbolFile(response.uploadUrl, mappingFile.asRequestBody(null)).execute()
        if (!uploadResult.isSuccessful) {
            uploadResult.errorBody()?.let {
                project.logger.error("Failed to upload mapping file. Code: ${uploadResult.code()} - ${uploadResult.message()}. Body:\n${it.string()}")
            }
            throw IOException("Failed to upload mapping file. Code: ${uploadResult.code()}")
        }

        val commitResponse = api.commitSymbolUpload(
            apiToken = configuration.apiToken,
            appName = configuration.appCenterAppName,
            owner = configuration.appCenterOwner,
            uploadId = response.uploadId
        ).execute()
        if (!commitResponse.isSuccessful) {
            commitResponse.errorBody()?.let {
                project.logger.error("Failed to commit mapping file. Code: ${commitResponse.code()} - ${commitResponse.message()}. Body:\n${it.string()}")
            }
            throw IOException("Failed to commit mapping file. Code: ${commitResponse.code()} - ${commitResponse.message()}")
        }
    }

    private fun <T> checkResponse(response: Response<T>): T {
        if (!response.isSuccessful) {
            response.errorBody()?.let {
                project.logger.error("Failed to communicate with AppCenter. Status code: ${response.code()} - ${response.message()}. Body:\n${it.string()}")
            }
            throw IOException("Failed to communicate with AppCenter. Status code: ${response.code()} - ${response.message()} for ${response.raw().request.url}")
        }
        return response.body() ?: throw IOException("Failed to communicate with AppCenter. Body expected")
    }

}