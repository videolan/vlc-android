import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
}

val vlcMajorVersionVal = if (providers.gradleProperty("forceVlc4").orNull?.toBoolean() == true) 4 else 3
val versionCodeVal = 3070200

// Remote Access release tags, shared with buildsystem/compile-remoteaccess.sh
val remoteAccessVersions = Properties().apply {
    file("buildsystem/remoteaccess.properties").inputStream().use { load(it) }
}

extra["appId"] = "org.videolan.vlc"
extra["versionCode"] = versionCodeVal
extra["vlcMajorVersion"] = vlcMajorVersionVal
extra["versionName"] = if (vlcMajorVersionVal == 3) "3.7.2" else "4.0.0-preview - $versionCodeVal"
extra["remoteAccessVersion"] = remoteAccessVersions.getProperty(if (vlcMajorVersionVal == 3) "remoteAccessVersionVlc3" else "remoteAccessVersionVlc4")
extra["libvlcVersion"] = if (vlcMajorVersionVal == 3) "3.7.8" else "4.0.0-eap29"
extra["medialibraryVersion"] = if (vlcMajorVersionVal == 3) "0.13.25" else "0.13.22-vlc4"

extra["targetJVM"] = JavaVersion.VERSION_1_8
extra["targetJVMKotlin"] = "1.8"

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

extra["toolchainNdkVersion"] = localProperties.getProperty("android.ndkFullVersion")
    ?: if (vlcMajorVersionVal == 3) "21.4.7075529" else "28.2.13676358"
extra["tooclchainNdkPath"] = localProperties.getProperty("android.ndkPath")
extra["targetSdkVersion"] = 36
extra["compileSdkVersion"] = 36

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun ver(name: String): String = versionCatalog.findVersion(name).get().requiredVersion

extra["android_plugin_version"] = ver("agp")
extra["kotlin_version"] = ver("kotlin")
extra["kotlinx_version"] = ver("kotlinx-coroutines")

extra["desugarLib"] = ver("desugar-jdk-libs")
extra["androidxLegacyVersion"] = ver("androidx-legacy")
extra["androidxCoreVersion"] = ver("androidx-core")
extra["appCompatVersion"] = ver("androidx-appcompat")
extra["lifecycleVersion"] = ver("lifecycle")
extra["androidxPreferencesVersion"] = ver("androidx-preference")
extra["androidxActivityVersion"] = ver("androidx-activity")
extra["androidxFragmentVersion"] = ver("androidx-fragment")
extra["androidxAnnotationVersion"] = ver("androidx-annotation")
extra["androidxRecyclerviewVersion"] = ver("androidx-recyclerview")
extra["androidxLeanbackVersion"] = ver("androidx-leanback")
extra["androidxMaterialVersion"] = ver("material")
extra["constraintLayoutVersion"] = ver("androidx-constraintlayout")
extra["windowVersion"] = ver("androidx-window")
extra["mediaVersion"] = ver("androidx-media")
extra["carVersion"] = ver("androidx-car-app")
extra["jdbcVersion"] = ver("sqlite-jdbc")
extra["viewPager2Version"] = ver("androidx-viewpager2")
extra["archVersion"] = ver("arch-core-testing")
extra["roomVersion"] = ver("room")
extra["pagingVersion"] = ver("paging")
extra["junitVersion"] = ver("junit")
extra["junitExtVersion"] = ver("junit-ext")
extra["mockito"] = ver("mockito")
extra["retrofit"] = ver("retrofit")
extra["moshi"] = ver("moshi")
extra["ktorVersion"] = ver("ktor")
extra["powerMock"] = ver("powermock")
extra["espressoVersion"] = ver("espresso")
extra["livedataTest"] = ver("livedata-testing")
extra["robolectric"] = ver("robolectric")
extra["mockk"] = ver("mockk")
extra["testRunner"] = ver("test-runner")
extra["orchestrator"] = ver("orchestrator")
extra["testCore"] = ver("test-core")

extra["publishScriptPath"] = "../../buildsystem/publish.gradle.kts"

allprojects {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
    tasks.withType<Javadoc>().configureEach {
        val options = options as StandardJavadocDocletOptions
        options.addStringOption("Xdoclint:none", "-quiet")
        options.addStringOption("encoding", "UTF-8")
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
            optIn.add("kotlin.RequiresOptIn")
        }
    }
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = rootProject.extra["targetJVM"].toString()
        targetCompatibility = rootProject.extra["targetJVM"].toString()
    }
    afterEvaluate {
        val androidExt = extensions.findByName("android")
        if (androidExt != null) {
            val getCompileOptions = androidExt.javaClass.getMethod("getCompileOptions")
            val compileOptions = getCompileOptions.invoke(androidExt)
            compileOptions.javaClass.getMethod("setSourceCompatibility", JavaVersion::class.java).invoke(compileOptions, JavaVersion.VERSION_1_8)
            compileOptions.javaClass.getMethod("setTargetCompatibility", JavaVersion::class.java).invoke(compileOptions, JavaVersion.VERSION_1_8)
        }
    }
}
