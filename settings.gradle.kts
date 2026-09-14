pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("com.android.settings") version "9.4.1"
}

val vlcMajorVersion = if (providers.gradleProperty("forceVlc4").orNull?.toBoolean() == true) 4 else 3

extensions.configure<com.android.build.api.dsl.SettingsExtension> {
    minSdk = if (vlcMajorVersion == 3) 17 else 23
    targetSdk = 36
    compileSdk = 36
    buildToolsVersion = "36.0.0"
}

include(":libvlcjni:libvlc", ":medialibrary")
include(
    ":application:tools",
    ":application:resources",
    ":application:mediadb",
    ":application:app",
    ":application:live-plot-graph",
    ":application:television",
    ":application:donations",
    ":application:remote-access-server",
    ":application:vlc-android",
    ":application:moviepedia",
    ":application:remote-access-client"
)
