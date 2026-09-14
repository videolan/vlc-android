plugins {
    id("vlc.android.library")
}

fun getPublicApiKey(): String {
    return System.getenv("K8S_SECRET_VLC_PLAYSTORE_PUBLIC_API_KEY")
        ?: providers.gradleProperty("vlc_playstore_public_api_key").orNull
        ?: ""
}

android {
    namespace = "org.videolan.vlc.donations"

    dataBinding {
        enable = true
    }

    packaging {
        jniLibs {
            pickFirsts += listOf("**/*.so")
        }
    }

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "PUBLIC_API_KEY", "\"${getPublicApiKey()}\"")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            isJniDebuggable = true
        }
        getByName("dev") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
        }
        getByName("vlcBundle")
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":application:tools"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
