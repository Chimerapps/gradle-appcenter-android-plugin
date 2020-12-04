package com.chimerapps.gradle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.api.ApplicationVariant
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

                    val notifyTesters = extension.applicationIdToNotifyTesters?.call(variant.applicationId)
                        ?: extension.variantToNotifyTesters?.call(variant.name)
                        ?: extension.flavorToNotifyTesters?.call(variant.flavorName)
                        ?: extension.notifyTesters

                    val releaseNotes = extension.applicationIdToReleaseNotes?.call(variant.applicationId)
                        ?: extension.variantToReleaseNotes?.call(variant.name)
                        ?: extension.flavorToReleaseNotes?.call(variant.flavorName)
                        ?: extension.releaseNotes

                    val testers = extension.applicationIdToTesters?.call(variant.applicationId)
                        ?: extension.variantToTesters?.call(variant.name)
                        ?: extension.flavorToTesters?.call(variant.flavorName)
                        ?: extension.testers

                    val appOwner = extension.applicationIdToAppOwner?.call(variant.applicationId)
                        ?: extension.variantToAppOwner?.call(variant.name)
                        ?: extension.flavorToAppOwner?.call(variant.flavorName)
                        ?: required(extension.appOwner, "appOwner")

                    if (appName.isNullOrBlank()) {
                        target.logger.debug("AppCenter - Skipping variant ${variant.name}, no app name mapping")
                        return@forEach
                    }

                    if (extension.testers.isEmpty())
                        throw IllegalStateException("AppCenter - At least one tester or group must be provided for variant: ${variant.name}")

                    if (extension.testers.isEmpty())
                        throw IllegalStateException("AppCenter - No app owner defined for variant: ${variant.name}")

                    val flavorName = variant.name

                    val taskName = "upload${flavorName.capitalize()}ToAppCenter"

                    val configuration = UploadTaskConfiguration(
                        apkFile = output.outputFile,
                        buildNumber = variant.versionCode.toLong(),
                        buildVersion = variant.versionName,
                        mappingFileProvider = { getMappingFile(target, variant) },
                        distributionTargets = testers,
                        notifyTesters = notifyTesters,
                        appCenterAppName = appName,
                        apiToken = apiKey,
                        appCenterOwner = appOwner,
                        flavorName = variant.flavorName,
                        changeLog = releaseNotes,
                        maxRetries = extension.maxRetries,
                        assembleTaskName = flavorName
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

    @Suppress("UnstableApiUsage", "DEPRECATION")
    private fun getMappingFile(project: Project, variant: ApplicationVariant): File? {
        return try {
            variant.mappingFileProvider.get().singleFile
        } catch (e: Throwable) {
            project.logger.info("Failed to get file from mapping file provider:", e)
            variant.mappingFile
        }
    }

}