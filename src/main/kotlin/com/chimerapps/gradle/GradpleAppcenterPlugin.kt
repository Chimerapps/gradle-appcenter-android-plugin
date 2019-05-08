package com.chimerapps.gradle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

class AndroidGradleAppCenterPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        if (!target.plugins.hasPlugin(AppPlugin::class.java)) {
            target.logger.warn("This plugin is made for Android Application Projects. The Android Plugin needs to be applied before this plugin.")
            return
        }

        val extension = target.extensions.create("appcenterAndroid", AppCenterExtension::class.java)
        try {
            target.afterEvaluate {
                val apiKey = required(extension.apiKey, "apiKey")
                val appOwner = required(extension.appOwner, "appOwner")
                if (extension.testers.isEmpty())
                    throw IllegalStateException("AppCenter - At least one tester or group must be provided")

                val appExtension = target.extensions.findByType(AppExtension::class.java) ?: return@afterEvaluate
                appExtension.applicationVariants.forEach { variant ->
                    val output = variant.outputs.firstOrNull()
                    if (output == null) {
                        target.logger.debug("Ignoring variant ${variant.name}, it has no output")
                        return@forEach
                    }

                    val appName = extension.applicationIdToAppName?.call(variant.applicationId)
                        ?: extension.variantToAppName?.call(variant.name)
                        ?: extension.flavorToAppName?.call(variant.flavorName)

                    if (appName.isNullOrBlank()) {
                        target.logger.debug("AppCenter - Skipping variant ${variant.name}, no app name mapping")
                        return@forEach
                    }

                    val flavorName = variant.name
                    val mappingFile: File? = variant.mappingFile

                    val taskName = "upload${flavorName.capitalize()}ToAppCenter"

                    val configuration = UploadTaskConfiguration(
                        apkFile = output.outputFile,
                        buildNumber = variant.versionCode.toLong(),
                        buildVersion = variant.versionName,
                        mappingFile = mappingFile,
                        distributionTargets = extension.testers,
                        notifyTesters = extension.notifyTesters,
                        appCenterAppName = appName,
                        apiToken = apiKey,
                        appCenterOwner = appOwner,
                        flavorName = variant.flavorName,
                        changeLog = extension.releaseNotes
                    )

                    @Suppress("UnstableApiUsage")
                    target.tasks.register(taskName, UploadBuildTask::class.java, configuration)
                }
            }
        } catch (e: Throwable) {
            target.logger.error(e.message)
            return
        }
    }

    private fun <T> required(data: T?, name: String): T {
        if (data == null)
            throw IllegalStateException("AppCenter - Missing required field: $name")
        return data
    }

}