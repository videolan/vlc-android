import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion

plugins {
    id("com.android.library")
}

pluginManager.apply("org.jetbrains.kotlin.android")

val vlcMajorVersion = if (providers.gradleProperty("forceVlc4").orNull?.toBoolean() == true) 4 else 3

extensions.configure<LibraryExtension> {
    compileSdk = 36

    defaultConfig {
        minSdk = if (vlcMajorVersion == 3) 17 else 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    packaging {
        jniLibs {
            pickFirsts += listOf("**/*.so")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("dev") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
        }
        create("vlcBundle") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
        }
    }
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        optIn.add("kotlin.RequiresOptIn")
    }
}
