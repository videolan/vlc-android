plugins {
    id("vlc.android.library")
    kotlin("kapt")
    alias(libs.plugins.ksp)
}

fun getMoviepediaUrl(): String {
    return providers.gradleProperty("moviepedia_api_url").orNull ?: "https://localhost/"
}

val versionCodeVal = rootProject.extra["versionCode"] as Int

android {
    namespace = "org.videolan.moviepedia"

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
        buildConfigField("int", "VLC_VERSION_CODE", "$versionCodeVal")
        buildConfigField("String", "MOVIEPEDIA_API_URL", "\"${getMoviepediaUrl()}\"")
        multiDexEnabled = true
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
        buildConfig = true
    }
}

dependencies {
    implementation(project(":application:tools"))
    implementation(project(":application:vlc-android"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(libs.androidx.room.runtime)
    ksp(libs.sqlite.jdbc)
    ksp(libs.androidx.room.compiler)

    implementation("com.squareup.retrofit2:retrofit:${libs.versions.retrofit.get()}")
    implementation("com.squareup.retrofit2:converter-moshi:${libs.versions.retrofit.get()}")
    implementation("com.squareup.moshi:moshi-adapters:${libs.versions.moshi.get()}")
    implementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
