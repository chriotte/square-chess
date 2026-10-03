import java.util.Properties
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}
kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }
// -Pdev=true builds the separate test app that fixture tests require.
val devBuild = providers.gradleProperty("dev").orNull == "true"
// Run black-box UI checks against optimized code in the isolated dev app.
val r8Test = providers.gradleProperty("r8Test").orNull == "true"
require(!r8Test || devBuild) { "R8 device tests require -Pdev=true" }
// Release signing key, kept outside the repository. The default is the Play upload
// key; the standalone (GitHub/F-Droid) release passes its own properties file with
// -PsigningProperties=<path> or the SQUARECHESS_SIGNING environment variable.
val signingFile = file(providers.gradleProperty("signingProperties").orNull
    ?: System.getenv("SQUARECHESS_SIGNING")
    ?: "${System.getProperty("user.home")}/SquareChessSigning/keystore.properties")
val signing = Properties().apply { if (signingFile.exists()) signingFile.inputStream().use { load(it) } }
android {
    namespace = "com.dataespresso.squarechess"
    testBuildType = if (r8Test) "release" else "debug"
    signingConfigs {
        if (signingFile.exists()) create("release") {
            storeFile = file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }
    compileSdk = 36
    ndkVersion = "28.2.13676358"
    defaultConfig {
        applicationId = "com.dataespresso.squarechess"
        applicationIdSuffix = if (devBuild) ".dev" else null
        manifestPlaceholders["appLabel"] = if (devBuild) "Square Chess Dev" else "Square Chess"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "1.4.1"
        testInstrumentationRunner = if (r8Test) "com.dataespresso.squarechess.smoke.R8SmokeRunner"
            else "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild { cmake { cppFlags += "-std=c++17" } }
    }
    buildTypes {
        getByName("debug") {
            // x86 runs the 32-bit engine on the Android 8 emulator profile.
            ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // armeabi-v7a: 32-bit Android 8 devices such as older e-ink tablets.
            // Symbol tables let Play Console show readable native crash reports.
            ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a"); debugSymbolLevel = "SYMBOL_TABLE" }
            signingConfig = signingConfigs.findByName("release")
            if (r8Test) {
                // Test APK and target must share a key. Never use the release key for fixtures.
                signingConfig = signingConfigs.getByName("debug")
                ndk { abiFilters += setOf("x86", "x86_64") }
            }
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    externalNativeBuild { cmake { path = file("../native/CMakeLists.txt"); version = "3.22.1" } }
    packaging { resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*") }
    // Settings → Language can pick any translation, so Play must install all of them, not only the phone's languages.
    bundle { language { enableSplit = false } }
    // The encrypted dependency list is for Google Play; F-Droid rejects the extra APK signing block.
    // The Play bundle keeps it.
    dependenciesInfo { includeInApk = false; includeInBundle = true }
    testOptions { unitTests.isReturnDefaultValues = true }
    if (r8Test) sourceSets.getByName("androidTest").java.setSrcDirs(listOf("src/r8Test/java"))
}
dependencies {
    implementation(project(":chesslib"))
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    kapt("androidx.room:room-compiler:2.7.2")
    testImplementation("junit:junit:4.13.2")
    if (!r8Test) {
        androidTestImplementation("androidx.test.ext:junit:1.2.1")
        androidTestImplementation("androidx.test:runner:1.6.2")
    }
}

// Release policy: Square Chess must work without Google Play Services and must not
// gain network or other permissions by accident (for example through a new library).
val forbiddenDependencyGroups = listOf(
    "com.google.android.gms", "com.google.firebase", "com.android.billingclient",
    "com.google.android.play", "com.crashlytics"
)
val allowedPermissions = setOf(
    // Added by AndroidX core for its own receivers; it grants nothing to other apps.
    "com.dataespresso.squarechess.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
)
tasks.register("verifyReleasePolicy") {
    group = "verification"
    description = "Fails on proprietary Google runtime libraries or unexpected release permissions."
    dependsOn("processReleaseManifest")
    val runtime = configurations.named("releaseRuntimeClasspath")
    val manifest = layout.buildDirectory.file("intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml")
    doLast {
        val modules = runtime.get().incoming.resolutionResult.allComponents
            .mapNotNull { (it.id as? ModuleComponentIdentifier)?.let { id -> "${id.group}:${id.module}:${id.version}" } }
        val forbidden = modules.filter { module -> forbiddenDependencyGroups.any { module.startsWith("$it:") || module.startsWith("$it.") } }
        if (forbidden.isNotEmpty()) throw GradleException("Forbidden proprietary dependencies:\n" + forbidden.joinToString("\n"))
        val permissions = Regex("<uses-permission[^>]*android:name=\"([^\"]+)\"")
            .findAll(manifest.get().asFile.readText()).map { it.groupValues[1] }.toSet()
        val unexpected = permissions - allowedPermissions
        if (unexpected.isNotEmpty()) throw GradleException("Unexpected permissions in the release manifest:\n" + unexpected.joinToString("\n"))
        println("Release policy OK: ${modules.size} runtime libraries, none proprietary; permissions: $permissions")
    }
}
tasks.named("check") { dependsOn("verifyReleasePolicy") }
