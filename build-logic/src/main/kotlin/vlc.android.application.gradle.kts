import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion

plugins {
    id("com.android.application")
}

pluginManager.apply("org.jetbrains.kotlin.android")

val vlcMajorVersion = if (providers.gradleProperty("forceVlc4").orNull?.toBoolean() == true) 4 else 3

extensions.configure<ApplicationExtension> {
    compileSdk = 36

    defaultConfig {
        minSdk = if (vlcMajorVersion == 3) 17 else 23
        targetSdk = 36
        vectorDrawables.useSupportLibrary = true
        multiDexEnabled = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }

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

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("signedRelease") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
        }
        create("vlcBundle") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
        }
        create("vlcBundleAmazon") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isJniDebuggable = true
        }
        create("dev") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
        }
    }
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        optIn.add("kotlin.RequiresOptIn")
    }
}
