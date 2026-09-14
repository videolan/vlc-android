import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("vlc.android.library")
    id("vlc.android.publishing")
}

val abi = providers.gradleProperty("vlcLibVariant").orNull ?: "all"
val libVersion = rootProject.extra["medialibraryVersion"].toString()
extra["library_version"] = libVersion

val vlcMajorVersionVal = rootProject.extra["vlcMajorVersion"] as Int
val toolchainNdkVersionVal = rootProject.extra["toolchainNdkVersion"].toString()
val toolchainNdkPathVal = rootProject.extra["tooclchainNdkPath"] as String?

android {
    namespace = "org.videolan.medialibrary"

    defaultConfig {
        ndkVersion = toolchainNdkVersionVal
        if (!toolchainNdkPathVal.isNullOrEmpty()) {
            ndkPath = toolchainNdkPathVal
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("jni/libs")
            manifest.srcFile("AndroidManifest.xml")
            java.srcDirs(if (vlcMajorVersionVal == 4) listOf("src", "vlc4/src") else listOf("src", "vlc3/src"))
            resources.srcDirs("src")
            aidl.srcDirs("src")
            renderscript.srcDirs("src")
            res.srcDirs("res")
            assets.srcDirs("assets", "libcompat/libs/armeabi")
        }
        getByName("test") {
            java.srcDirs("test")
        }
        getByName("androidTest") {
            java.srcDirs("androidTest")
            assets.srcDirs("$projectDir/assets/schemas")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFile("proguard.cfg")
        }
        getByName("dev") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
        }
    }

    dataBinding {
        enable = true
    }
    buildFeatures {
        renderScript = true
        aidl = true
    }

    libraryVariants.all {
        outputs.all {
            val outputImpl = this as? BaseVariantOutputImpl
            if (outputImpl?.outputFileName?.endsWith(".aar") == true) {
                outputImpl.outputFileName = "medialibrary-$abi-$libVersion.aar"
            }
        }
    }
}

tasks.register<Delete>("cleanExtra") {
    delete("build", "jni/libs", "jni/obj")
}

dependencies {
    add("releaseImplementation", project(":libvlcjni:libvlc"))
    add("debugImplementation", "org.videolan.android:libvlc-all:${rootProject.extra["libvlcVersion"]}")
    add("devImplementation", project(":libvlcjni:libvlc"))
    api(libs.androidx.legacy.v4)
    api(libs.androidx.core)
    api(libs.androidx.fragment)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

if (file("../buildsystem/publish.gradle.kts").exists()) {
    apply(from = "../buildsystem/publish.gradle.kts")
}
