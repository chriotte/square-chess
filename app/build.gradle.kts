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
// Release signing key, kept outside the repository. The default is the Play upload
// key; the standalone (GitHub/F-Droid) release passes its own properties file with
// -PsigningProperties=<path> or the SQUARECHESS_SIGNING environment variable.
val signingFile = file(providers.gradleProperty("signingProperties").orNull
    ?: System.getenv("SQUARECHESS_SIGNING")
    ?: "${System.getProperty("user.home")}/SquareChessSigning/keystore.properties")
val signing = Properties().apply { if (signingFile.exists()) signingFile.inputStream().use { load(it) } }
android {
    namespace = "com.dataespresso.squarechess"
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
        minSdk = 29
        targetSdk = 36
        versionCode = 8
        versionName = "1.0.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild { cmake { cppFlags += "-std=c++17" } }
    }
    buildTypes {
        getByName("debug") {
            ndk { abiFilters += setOf("arm64-v8a", "x86_64") }
        }
        getByName("release") {
            // Symbol tables let Play Console show readable native crash reports.
            ndk { abiFilters += "arm64-v8a"; debugSymbolLevel = "SYMBOL_TABLE" }
            signingConfig = signingConfigs.findByName("release")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    externalNativeBuild { cmake { path = file("../native/CMakeLists.txt"); version = "3.22.1" } }
    packaging { resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*") }
    testOptions { unitTests.isReturnDefaultValues = true }
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
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
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
