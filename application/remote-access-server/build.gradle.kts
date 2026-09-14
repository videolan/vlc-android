plugins {
    id("vlc.android.library")
}

val remoteAccessDebugProp = providers.gradleProperty("vlc_remote_access_debug").orNull ?: "false"

android {
    namespace = "org.videolan.vlc.remoteaccessserver"

    dataBinding {
        enable = true
    }

    defaultConfig {
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("Boolean", "VLC_REMOTE_ACCESS_DEBUG", remoteAccessDebugProp)
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

    sourceSets {
        getByName("main") {
            assets.srcDirs("assets")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":application:remote-access-client"))

    implementation(project(":application:vlc-android"))

    val ktorVer = libs.versions.ktor.get()
    implementation("io.ktor:ktor-server-core:$ktorVer")
    implementation("io.ktor:ktor-server-netty:$ktorVer")
    implementation("io.ktor:ktor-server-websockets:$ktorVer")
    implementation("io.ktor:ktor-server-caching-headers:$ktorVer")
    implementation("io.ktor:ktor-server-compression:$ktorVer")
    implementation("io.ktor:ktor-server-cors:$ktorVer")
    implementation("io.ktor:ktor-server-auth:$ktorVer")
    implementation("io.ktor:ktor-server-partial-content:$ktorVer")
    implementation("io.ktor:ktor-server-auto-head-response:$ktorVer")
    implementation("io.ktor:ktor-network-tls-certificates:$ktorVer")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.79")
    implementation("io.ktor:ktor-server-call-logging:$ktorVer")
    implementation("io.ktor:ktor-server-sessions:$ktorVer")
    add("debugImplementation", "org.slf4j:slf4j-android:1.7.36")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
