plugins {
    `kotlin-dsl`
}

group = "org.videolan.vlc.buildlogic"

dependencies {
    implementation(libs.plugins.android.application.get().pluginId + ":" + libs.plugins.android.application.get().pluginId + ".gradle.plugin:" + libs.versions.agp.get())
    implementation(libs.plugins.android.library.get().pluginId + ":" + libs.plugins.android.library.get().pluginId + ".gradle.plugin:" + libs.versions.agp.get())
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:" + libs.versions.kotlin.get())
    implementation(libs.plugins.ksp.get().pluginId + ":" + libs.plugins.ksp.get().pluginId + ".gradle.plugin:" + libs.versions.ksp.get())
    implementation("com.vanniktech:gradle-maven-publish-plugin:" + libs.versions.vanniktech.publish.get())
}
