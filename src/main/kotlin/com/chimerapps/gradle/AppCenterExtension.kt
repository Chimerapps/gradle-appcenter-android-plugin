package com.chimerapps.gradle

import groovy.lang.Closure

open class AppCenterExtension {

    open var apiKey: String? = null
    open var appOwner: String? = null
    open var notifyTesters: Boolean = true
    open var testers: MutableList<String> = mutableListOf("collaborators")

    open var releaseNotes: String? = null

    open var maxRetries: Int = 3

    open var applicationIdToAppName: Closure<String>? = null
    open var variantToAppName: Closure<String>? = null
    open var flavorToAppName: Closure<String>? = null

}