import com.android.build.gradle.internal.api.ApkVariantOutputImpl

plugins {
    id("vlc.android.application")
}

val appIdVal = rootProject.extra["appId"].toString()
val versionCodeVal = rootProject.extra["versionCode"] as Int
val versionNameVal = rootProject.extra["versionName"].toString()

android {
    namespace = "org.videolan.mobile.app"

    packaging {
        jniLibs {
            pickFirsts += listOf(
                "lib/armeabi-v7a/libc++_shared.so",
                "lib/armeabi/libc++_shared.so",
                "lib/arm64-v8a/libc++_shared.so",
                "lib/x86/libc++_shared.so",
                "lib/x86_64/libc++_shared.so"
            )
        }
        resources {
            excludes += listOf("META-INF/*", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }

    dataBinding {
        enable = true
    }

    flavorDimensions += "abi"

    defaultConfig {
        applicationId = appIdVal
        versionCode = versionCodeVal
        versionName = versionNameVal
        vectorDrawables.useSupportLibrary = true
        multiDexEnabled = true

        testInstrumentationRunner = "org.videolan.vlc.MultidexTestRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    signingConfigs {
        create("release") {
            if (providers.gradleProperty("keyStoreFile").isPresent) {
                storeFile = file(providers.gradleProperty("keyStoreFile").get())
                keyAlias = providers.gradleProperty("storealias").orNull
                val envPwd = System.getenv("PASSWORD_KEYSTORE")
                if (!envPwd.isNullOrEmpty()) {
                    storePassword = envPwd
                    keyPassword = envPwd
                } else {
                    storePassword = providers.gradleProperty("storepwd").orNull
                    keyPassword = providers.gradleProperty("storepwd").orNull
                }
            }
        }
    }

    androidComponents {
        beforeVariants(selector().withBuildType("vlcBundle")) { variantBuilder ->
            variantBuilder.minSdk = 30
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("signedRelease") {
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("vlcBundle")
        getByName("vlcBundleAmazon")
        getByName("debug")
        getByName("dev")
    }

    sourceSets {
        getByName("release") {
            manifest.srcFile("flavors/release/AndroidManifest.xml")
        }
        getByName("signedRelease") {
            manifest.srcFile("flavors/release/AndroidManifest.xml")
        }
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    splits {
        abi {
            val taskNames = gradle.startParameter.taskNames
            val isReleaseBuild = taskNames.any { it.lowercase().contains(Regex("assemble.*release")) }
            isEnable = isReleaseBuild
            reset()
            include("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
        }
    }

    lint {
        abortOnError = false
        disable += listOf("MissingTranslation", "ExtraTranslation")
    }

    val abiCodes = mapOf("armeabi-v7a" to 5, "arm64-v8a" to 6, "x86" to 7, "x86_64" to 8)

    applicationVariants.all {
        val variant = this
        outputs.all {
            val outputImpl = this as? ApkVariantOutputImpl
            val abiName = outputImpl?.filters?.find { it.filterType == com.android.build.OutputFile.ABI }?.identifier ?: "all"
            var abiCode = abiCodes[abiName] ?: 0
            if (variant.buildType.name == "vlcBundle") {
                abiCode = 9
            }

            outputImpl?.versionCodeOverride = 10000000 + (variant.versionCode) + abiCode
            var outputName = "VLC-Android-"
            outputName += variant.versionName.replace(" ", "-")
            if (variant.buildType.name == "debug") outputName += "-debug"
            outputName += "-$abiName.apk"
            outputImpl?.outputFileName = outputName
        }
    }
}

dependencies {
    implementation(project(":application:vlc-android"))
    implementation(project(":application:television"))
    implementation(project(":application:remote-access-server"))
    testImplementation(project(":application:television"))
    testImplementation(project(":application:remote-access-server"))

    androidTestImplementation(libs.androidx.test.espresso.contrib)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    androidTestUtil(libs.androidx.test.orchestrator)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockk)
    testImplementation(libs.powermock.api.mockito2)
    testImplementation(libs.powermock.module.junit4)
    testImplementation(libs.livedata.testing.ktx)
    testImplementation(libs.robolectric)
    androidTestImplementation("com.android.support:multidex:1.0.3")
    androidTestImplementation(libs.uiautomator)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.falcon)
    androidTestImplementation(libs.screengrab)
}
