import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

plugins {
    id("vlc.android.library")
    kotlin("kapt")
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
}

val buildRevision = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.get().trim()

val vlcMajorVersionVal = rootProject.extra["vlcMajorVersion"] as Int
val appIdVal = rootProject.extra["appId"].toString()
val versionCodeVal = rootProject.extra["versionCode"] as Int
val versionNameVal = rootProject.extra["versionName"].toString()
val libvlcVersionVal = rootProject.extra["libvlcVersion"].toString()
val medialibraryVersionVal = rootProject.extra["medialibraryVersion"].toString()
val toolchainNdkVersionVal = rootProject.extra["toolchainNdkVersion"].toString()
val toolchainNdkPathVal = rootProject.extra["tooclchainNdkPath"] as String?

fun buildTime(): String {
    if (providers.gradleProperty("forceDeterministic").isPresent) {
        val code = providers.exec {
            commandLine("git", "show", "--no-patch", "--format=%cd", "--date=format-local:%Y-%m-%d")
        }.standardOutput.asText.get().trim()
        return code
    }
    val sdf = SimpleDateFormat("yyyy-MM-dd")
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date())
}

fun hostName(): String {
    if (providers.gradleProperty("forceDeterministic").isPresent) {
        return providers.gradleProperty("forcedHostName").orNull ?: "Unknown"
    }
    return "${System.getProperty("user.name")}@${InetAddress.getLocalHost().hostName}"
}

fun dav1dVersion(): String {
    var code = "0.0.0"
    val dav1dContribFile = file("../../libvlcjni/vlc/contrib/src/dav1d/rules.mak")
    if (dav1dContribFile.exists()) {
        dav1dContribFile.useLines { lines ->
            for (line in lines) {
                if (line.contains("DAV1D_VERSION := ")) {
                    code = line.replace("DAV1D_VERSION := ", "").trim()
                    break
                }
            }
        }
    }
    return code
}

fun changelog(): String {
    val newsFile = file("NEWS")
    var output = ""
    var started = false
    if (newsFile.exists()) {
        newsFile.useLines { lines ->
            for (line in lines) {
                if (started && line.trim().isEmpty()) break
                if (started) {
                    if (output.isNotEmpty()) output += "\\n"
                    output += line.trim().replace(Regex("""(@|'|"|\\|\(|\)|=|\?)"""), "\\\\$0")
                }
                if (line.contains("---")) started = true
            }
        }
    }
    return output
}

fun isBeta(): String {
    val versionNameLower = versionNameVal.lowercase()
    val beta = versionNameLower.contains("beta") || versionNameLower.contains("rc") || versionNameLower.contains("alpha") || versionNameLower.contains("dev")
    return beta.toString()
}

fun generateTranslation(): String {
    val foundLocales = StringBuilder()
    foundLocales.append("new String[]{\"")
    val emptyList = mutableListOf<String>()
    fileTree("../resources/src/main/res").visit {
        if (file.path.endsWith("strings.xml")) {
            var languageCode = file.parentFile.name.replace("values-", "").replace("-r", "-")
            languageCode = if (languageCode == "values") "en" else languageCode
            emptyList.add(languageCode)
        }
    }
    emptyList.sort()
    foundLocales.append(emptyList.joinToString("\",\""))
    foundLocales.append("\"}")
    return foundLocales.toString()
}

android {
    namespace = "org.videolan.vlc"

    dataBinding {
        enable = true
    }

    packaging {
        jniLibs {
            pickFirsts += listOf("**/*.so")
        }
    }

    defaultConfig {
        resValue("string", "build_time", buildTime())
        resValue("string", "build_host", hostName())
        resValue("string", "build_revision", buildRevision)
        resValue("string", "changelog", changelog())
        resValue("string", "dav1d_version", dav1dVersion())
        resValue("string", "tv_provider_authority", "$appIdVal.tv")
        buildConfigField("String", "LIBVLC_VERSION", "\"$libvlcVersionVal\"")
        buildConfigField("String", "ML_VERSION", "\"$medialibraryVersionVal\"")
        buildConfigField("String", "APP_ID", "\"$appIdVal\"")
        buildConfigField("int", "VLC_VERSION_CODE", "$versionCodeVal")
        buildConfigField("String", "VLC_VERSION_NAME", "\"$versionNameVal\"")
        buildConfigField("String[]", "TRANSLATION_ARRAY", generateTranslation())
        buildConfigField("int", "VLC_MAJOR_VERSION", "$vlcMajorVersionVal")

        testInstrumentationRunner = "org.videolan.vlc.MultidexTestRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"

        vectorDrawables.useSupportLibrary = true
        ndkVersion = toolchainNdkVersionVal
        if (!toolchainNdkPathVal.isNullOrEmpty()) {
            ndkPath = toolchainNdkPathVal
        }
    }

    testOptions {
        animationsDisabled = true
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        unitTests {
            isIncludeAndroidResources = true
            all {
                (this as? org.gradle.api.tasks.testing.Test)?.apply {
                    jvmArgs("-noverify")
                    testLogging {
                        events("passed", "skipped", "failed", "standardOut", "standardError")
                        showStandardStreams = true
                    }
                }
            }
        }
    }

    buildTypes {
        getByName("release") {
            proguardFile("proguard.cfg")
            buildConfigField("boolean", "BETA", isBeta())
            resValue("string", "benchmark_package_name", "org.videolan.vlcbenchmark")
        }
        getByName("debug") {
            buildConfigField("boolean", "BETA", "false")
            resValue("string", "benchmark_package_name", "org.videolan.vlcbenchmark.debug")
            buildConfigField("String", "APP_ID", "\"$appIdVal.debug\"")
            resValue("string", "tv_provider_authority", "$appIdVal.debug.tv")
            buildConfigField("boolean", "NO_TV", "false")
            multiDexEnabled = true
        }
        getByName("vlcBundle")
        create("signedRelease") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
        }
        getByName("dev") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.srcDirs(if (vlcMajorVersionVal == 4) listOf("src", "vlc4/src") else listOf("src", "vlc3/src"))
            resources.srcDirs("src")
            aidl.srcDirs("src")
            renderscript.srcDirs("src")
            res.srcDirs("res")
            assets.srcDirs("assets")
        }
        getByName("debug") {
            res.srcDirs("flavors/debug/res")
            assets.srcDirs("flavors/debug/assets")
        }
        getByName("dev") {
            res.srcDirs("flavors/debug/res")
            assets.srcDirs("flavors/debug/assets")
        }
        getByName("test") {
            java.srcDirs("test", "test-common")
            assets.srcDirs("flavors/debug/assets")
        }
        getByName("androidTest") {
            java.srcDirs("androidTest", "test-common")
            assets.srcDirs("flavors/debug/assets")
            assets.srcDirs("$projectDir/assets/schemas")
        }
    }

    buildFeatures {
        renderScript = true
        aidl = true
        buildConfig = true
        resValues = true
    }
}

val libraryExt = extensions.getByType<com.android.build.api.dsl.LibraryExtension>()
tasks.register<Jar>("generateSources") {
    from(libraryExt.sourceSets.getByName("main").java.directories)
}

dependencies {
    add("devApi", project(":libvlcjni:libvlc"))
    add("devApi", project(":medialibrary"))
    add("debugApi", "org.videolan.android:libvlc-all:${rootProject.extra["libvlcVersion"]}")
    add("debugApi", "org.videolan.android:medialibrary-all:${rootProject.extra["medialibraryVersion"]}")
    add("releaseApi", "org.videolan.android:libvlc-all:${rootProject.extra["libvlcVersion"]}")
    add("releaseApi", "org.videolan.android:medialibrary-all:${rootProject.extra["medialibraryVersion"]}")
    add("signedReleaseApi", "org.videolan.android:libvlc-all:${rootProject.extra["libvlcVersion"]}")
    add("signedReleaseApi", "org.videolan.android:medialibrary-all:${rootProject.extra["medialibraryVersion"]}")

    api(project(":application:tools"))
    api(project(":application:resources"))
    api(project(":application:mediadb"))
    api(project(":application:live-plot-graph"))

    api(libs.androidx.activity.ktx)
    api(libs.androidx.fragment.ktx)
    api(libs.androidx.recyclerview)
    api(libs.material)
    api(libs.androidx.annotation)
    api(libs.androidx.constraintlayout)
    api(libs.androidx.viewpager2)
    api(libs.androidx.multidex)
    api(libs.androidx.lifecycle.process)
    api(libs.androidx.lifecycle.service)
    api(libs.androidx.lifecycle.viewmodel.ktx)
    api(libs.androidx.lifecycle.runtime.ktx)
    api(libs.androidx.lifecycle.livedata.ktx)
    api(libs.androidx.lifecycle.common.java8)
    api(libs.androidx.room.runtime)
    api(libs.androidx.window)
    api(libs.androidx.media)
    api(libs.androidx.gridlayout)
    api(libs.androidx.car.app)

    ksp(libs.sqlite.jdbc)
    ksp(libs.androidx.room.compiler)

    api(libs.androidx.paging.runtime.ktx)

    api(libs.androidx.leanback)
    api(libs.androidx.leanback.preference)
    api(libs.androidx.tvprovider)

    api(libs.kotlin.reflect)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.android)

    implementation(libs.konfetti.xml)
    implementation(libs.zxing.core)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.colorpicker)

    androidTestImplementation(libs.androidx.test.espresso.contrib)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestUtil(libs.androidx.test.orchestrator)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockk)
    testImplementation(libs.powermock.api.mockito2)
    testImplementation(libs.powermock.module.junit4)
    testImplementation(libs.livedata.testing.ktx)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.falcon)
    androidTestImplementation(libs.screengrab)

    if (providers.gradleProperty("leakCanaryEnabled").orNull?.toBoolean() == true) {
        add("debugImplementation", libs.leakcanary.android)
        add("devImplementation", libs.leakcanary.android)
    }
}
