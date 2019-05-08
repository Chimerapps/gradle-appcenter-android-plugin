# App center plugin for android gradle builds

Usage: 
```
//Add the following to your root build.gradle
buildscript {
    repositories {
        jcenter()
    }
    
    dependencies {
        classpath 'com.chimerapps.gradle:gradle-appcenter-android-plugin:<latest version>'
    }
}

//Add the following to your project build.gradle
apply plugin: 'com.chimerapps.gradle.gradle-appcenter-android-plugin'

//Configure the plugin
appcenterAndroid {
    apiKey = '<your api key here>'
    appOwner = '<your appcenter app owner name here>'
    notifyTesters = false
    releaseNotes = '<your release notes here, markdown is supported by appcenter>'

    //Provide one of variantToAppName, applicationIdToAppName, flavorToAppName.
    //These methods can return null if this variant/app name does not support uploading to appcenter
    //NOTE: If the appname could not be found, no corresponding task will be generated
    //The app name is resolverd in the following order: applicationIdToAppName -> variantToAppName -> flavorToAppName
    applicationIdToAppName = { applicationId ->
        //Map application ids to appcenter app name
        return '<appcenter app name for this application id>'
    }
    flavorToAppName = { flavorName ->
        //Map flavors to appcenter app name
        return '<appcenter app name for this flavor>'
    }
    variantToAppName = { variantName ->
        //Map variants to appcenter app name
        return '<appcenter app name for this variant>'
    }
    
    //Which testers or groups to distribute to. Defaults to 'collaborators'. Must contain at least 1 entry
    testers = ['collaborators', ...]
}

```