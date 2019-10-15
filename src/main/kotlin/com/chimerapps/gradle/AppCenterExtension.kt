package com.chimerapps.gradle

import groovy.lang.Closure

open class AppCenterExtension {

    open var apiKey: String? = null
    open var appOwner: String? = null
    open var notifyTesters: Boolean = true
    open var testers: MutableList<String> = mutableListOf("collaborators")

    open var releaseNotes: String? = null

    open var maxRetries: Int = 3

    open var applicationIdToAppName: Closure<String?>? = null
    open var variantToAppName: Closure<String?>? = null
    open var flavorToAppName: Closure<String?>? = null

    open var applicationIdToTesters: Closure<List<String>?>? = null
    open var variantToTesters: Closure<List<String>?>? = null
    open var flavorToTesters: Closure<List<String>?>? = null

    open var applicationIdToReleaseNotes: Closure<String?>? = null
    open var variantToReleaseNotes: Closure<String?>? = null
    open var flavorToReleaseNotes: Closure<String?>? = null

    open var applicationIdToNotifyTesters: Closure<Boolean?>? = null
    open var variantToNotifyTesters: Closure<Boolean?>? = null
    open var flavorToNotifyTesters: Closure<Boolean?>? = null

    open var applicationIdToAppOwner : Closure<String?>? = null
    open var variantToAppOwner : Closure<String?>? = null
    open var flavorToAppOwner : Closure<String?>? = null

}