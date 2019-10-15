# App center plugin for android gradle builds

[ ![Download](https://api.bintray.com/packages/nicolaverbeeck/maven/gradle-appcenter-android-plugin/images/download.svg) ](https://bintray.com/nicolaverbeeck/maven/gradle-appcenter-android-plugin/_latestVersion)

Generates tasks for uploading your android builds to appcenter


Usage: 
```groovy
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

    //Which testers or groups to distribute to. Defaults to 'collaborators'. Must contain at least 1 entry
    testers = ['collaborators', ...]

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
    
    //In case of transient http errors, how many times to retry (after a delay). Defaults to 3 times
    maxRetries = 3
}

```

####Advanced configuration
```groovy
//This shows more fine grained configuration for some common parameters. 
//These are always resolved in the following order: applicationIdToXXX -> variantToXXX -> flavorToXXX
//If any of these closures returns null, the next is considered, if all return null, the default, non specialized value is returned
appcenterAndroid {

//Testers
        applicationIdToTesters = { applicationId ->
            //Map application ids to appcenter testers
            return ['collaborators', 'internaltesters']
        }
        flavorToTesters = { flavorName ->
            //Map flavors to appcenter testers
            return ['collaborators', 'internaltesters']
        }
        variantToTesters = { variantName ->
            //Map variants to appcenter testers
            return ['collaborators', 'internaltesters']
        }

//Release notes
        applicationIdToReleaseNotes = { applicationId ->
            //Map application ids to appcenter release notes
            return '<release notes for this application id>'
        }
        flavorToReleaseNotes = { flavorName ->
            //Map flavors to appcenter release notes
            return '<release notes for this flavor>'
        }
        variantToReleaseNotes = { variantName ->
            //Map variants to appcenter testers
            return '<release notes for this variant>'
        }

//Notify testers
        applicationIdToNotifyTesters = { applicationId ->
            //Map application ids to appcenter notify testers
            return true
        }
        flavorToNotifyTesters = { flavorName ->
            //Map flavors to appcenter notify testers
            return false
        }
        variantToNotifyTesters = { variantName ->
            //Map variants to appcenter notify testers
            return true
        }

//App owner
        applicationIdToAppOwner = { applicationId ->
            //Map application ids to appcenter app owners
            return '<app owner for this application id>'
        }
        flavorToAppOwner = { flavorName ->
            //Map flavors to appcenter app owners
            return '<app owner for this flavor>'
        }
        variantToAppOwner = { variantName ->
            //Map variants to appcenter app owners
            return '<app owner for this variant>'
        }

}

```