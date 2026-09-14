plugins {
    id("vlc.android.library")
}

val remoteAccessVersion = rootProject.extra["remoteAccessVersion"].toString()

val remoteclientSourceDir = "$projectDir/remoteaccess"
// The pinned tag identifies the downloaded release. A local checkout is described
// instead, as it may be on another revision. Only describe remoteaccess/ when it is its
// own git checkout: otherwise git walks up to the vlc-android repository and would report
// one of its tags as the revision
var remoteAccessRevision = remoteAccessVersion
if (file("$remoteclientSourceDir/.git").exists()) {
    try {
        val describe = providers.exec {
            workingDir(remoteclientSourceDir)
            commandLine("git", "describe", "--tags", "--always")
            isIgnoreExitValue = true
        }
        if (describe.result.get().exitValue == 0) {
            remoteAccessRevision = describe.standardOutput.asText.get().trim()
        } else {
            logger.warn("Remote access: git describe failed: ${describe.standardError.asText.get().trim()}")
        }
    } catch (e: Exception) {
        logger.warn("Remote access: unable to run git describe: ${e.message}")
    }
}

android {
    namespace = "org.videolan.vlc.remoteaccessclient"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        resValue("string", "build_remote_access_revision", remoteAccessRevision)
        resValue("string", "remote_access_version", remoteAccessVersion)
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("assets")
        }
    }

    buildFeatures {
        resValues = true
    }
}

val webCopy = tasks.register<Copy>("webCopy") {
    from("remoteaccess/dist")
    into("assets/dist")
}

tasks.named("preBuild") {
    dependsOn(webCopy)
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
