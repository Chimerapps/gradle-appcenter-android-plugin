package com.chimerapps.gradle

import com.chimerapps.gradle.api.*
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

class AppCenterApiTest {

    private lateinit var api: AppCenterMiniApi
    private lateinit var apiToken: String
    private lateinit var appOwner: String
    private lateinit var appName: String
    private lateinit var apkFile: String

    private val moshi = Moshi.Builder().add(MoshiFactory()).build()

    @Before
    fun setup() {
        apiToken = System.getenv("APPCENTER_TEST_API_TOKEN")
        appOwner = System.getenv("APPCENTER_TEST_OWNER")
        appName = System.getenv("APPCENTER_TEST_APP_NAME")
        apkFile = System.getenv("APPCENTER_TEST_APK_FILE")

        val builder = OkHttpClient.Builder()
            .writeTimeout(60L, TimeUnit.SECONDS)
            .readTimeout(60L, TimeUnit.SECONDS)
            .connectTimeout(60L, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor(maxRetries = 3, logger = null))

        val logger = HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
            override fun log(message: String) {
                println("[AppCenter] - (${Date()}) - $message")
            }
        })
        logger.level = HttpLoggingInterceptor.Level.BODY
        logger.redactHeader("X-API-Token")
        builder.addInterceptor(logger)

        val client = builder.build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.appcenter.ms/v0.1/apps/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(client)
            .build()

        api = retrofit.create(AppCenterMiniApi::class.java)
    }

    @Test
    fun prepareUploadTest() {
        val res = checkResponse(
            api.prepareUpload(
                apiToken = apiToken,
                owner = appOwner,
                appName = appName
            ).execute()
        )

        val file = File(apkFile)

        val url = createPrepareBinaryUrl(res, file)
        val prepareBinaryRes = checkResponse(api.prepareBinaryUpload(url).execute())

        checkResponse(api.uploadBinaryChunked(res, file, prepareBinaryRes))

        checkResponse(api.finalizeBinaryUpload(prepareFinishUploadUrl(res)).execute())

        checkResponse(
            api.commitBinaryUploadStatus(
                apiToken = apiToken,
                owner = appOwner,
                appName = appName,
                uploadId = res.id,
            ).execute()
        )

        val releaseId = api.pollForReleaseId(
            apiToken = apiToken,
            owner = appOwner,
            appName = appName,
            uploadId = res.id,
            logger = null,
        )

        checkResponse(
            api.updateReleaseNotes(
                apiToken = apiToken,
                owner = appOwner,
                appName = appName,
                releaseId = releaseId,
                body = UpdateReleaseRequest("Example release notes")
            ).execute()
        )

        val group = checkResponse(
            api.getDistributionGroup(
                apiToken = apiToken,
                owner = appOwner,
                appName = appName,
                name = "collaborators"
            ).execute()
        )

        checkResponse(
            api.addDistributionGroup(
                apiToken = apiToken,
                owner = appOwner,
                appName = appName,
                releaseId = releaseId,
                body = AddDistributionGroupRequest(id = group.id, notifyTesters = false, mandatoryUpdate = false)
            ).execute()
        )
    }

    private fun <T> checkResponse(response: Response<T>): T {
        if (!response.isSuccessful) {
            response.errorBody()?.let {
                System.err.println("Failed to communicate with AppCenter. Status code: ${response.code()} - ${response.message()}. Body:\n${it.string()}")
            }
            throw IOException("Failed to communicate with AppCenter. Status code: ${response.code()} - ${response.message()} for ${response.raw().request.url}")
        }
        val body = response.body() ?: throw IOException("Failed to communicate with AppCenter. Body expected")

        if (body is ChunkUploadResponse && body.error)
            throw IOException("Failed to communicate with AppCenter. Api returned error for ${response.raw().request.url}")

        return body
    }

}