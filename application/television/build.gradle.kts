plugins {
    id("vlc.android.library")
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
}

android {
    namespace = "org.videolan.television"

    packaging {
        jniLibs {
            pickFirsts += listOf("**/*.so")
        }
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("dev") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
        }
        getByName("vlcBundle")
    }
}

dependencies {
    implementation(project(":application:vlc-android"))
    api(project(":application:moviepedia"))
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    implementation(libs.colorpicker)
}
