plugins {
    id("vlc.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.videolan.vlc.mediadb"

    packaging {
        jniLibs {
            pickFirsts += listOf("**/*.so")
        }
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
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)

    implementation(libs.androidx.room.ktx)
    ksp(libs.sqlite.jdbc)
    ksp(libs.androidx.room.compiler)

    implementation(project(":application:tools"))
    implementation(project(":application:resources"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}
