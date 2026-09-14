plugins {
    id("vlc.android.library")
}

fun getOSApiKey(): String {
    return System.getenv("VLC_OPEN_SUBTITLES_API_KEY")
        ?: providers.gradleProperty("vlc_open_subtitles_api_key").orNull
        ?: ""
}

val vlcMajorVersionVal = rootProject.extra["vlcMajorVersion"] as Int
val appIdVal = rootProject.extra["appId"].toString()

android {
    namespace = "org.videolan.resources"

    packaging {
        jniLibs {
            pickFirsts += listOf("**/*.so")
        }
    }

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "APP_ID", "\"$appIdVal\"")
        buildConfigField("String", "VLC_OPEN_SUBTITLES_API_KEY", "\"${getOSApiKey()}\"")
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            buildConfigField("String", "APP_ID", "\"$appIdVal.debug\"")
        }
        getByName("dev") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
        }
        getByName("vlcBundle")
    }

    sourceSets {
        getByName("main") {
            java.srcDirs(if (vlcMajorVersionVal == 4) listOf("src", "vlc4/src") else listOf("src", "vlc3/src"))
        }
        getByName("debug") {
            res.srcDirs("flavors/debug/res")
        }
        getByName("dev") {
            res.srcDirs("flavors/debug/res")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(libs.kotlin.stdlib)
    api(libs.androidx.multidex)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.android)

    api(project(":application:tools"))
    add("devApi", project(":libvlcjni:libvlc"))
    add("devApi", project(":medialibrary"))
    add("debugApi", "org.videolan.android:libvlc-all:${rootProject.extra["libvlcVersion"]}")
    add("debugApi", "org.videolan.android:medialibrary-all:${rootProject.extra["medialibraryVersion"]}")
    add("releaseApi", "org.videolan.android:libvlc-all:${rootProject.extra["libvlcVersion"]}")
    add("releaseApi", "org.videolan.android:medialibrary-all:${rootProject.extra["medialibraryVersion"]}")
    add("vlcBundleApi", "org.videolan.android:libvlc-all:${rootProject.extra["libvlcVersion"]}")
    add("vlcBundleApi", "org.videolan.android:medialibrary-all:${rootProject.extra["medialibraryVersion"]}")

    api(libs.material)
    api(libs.androidx.preference.ktx)
    api(libs.androidx.leanback)
    api(libs.androidx.leanback.preference)

    api("com.squareup.okhttp3:okhttp:4.9.3")
    api("com.squareup.okhttp3:logging-interceptor:4.9.3")
    api("com.github.mrmike:ok2curl:0.8.0")
    api("com.squareup.retrofit2:retrofit:${libs.versions.retrofit.get()}")
    api("com.squareup.retrofit2:converter-moshi:${libs.versions.retrofit.get()}")
    api("com.squareup.moshi:moshi-adapters:${libs.versions.moshi.get()}")

    api(libs.androidx.core.ktx)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
