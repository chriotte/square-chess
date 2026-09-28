import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}
kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }
// Fairy-Stockfish is the production engine. -Pengine=stockfish builds the preserved
// Stockfish 19 baseline; -Pdev=true builds the separate test app that fixture tests require.
val fairyEngine = providers.gradleProperty("engine").orNull != "stockfish"
val devBuild = providers.gradleProperty("dev").orNull == "true"
// Older checkouts kept the 98 MB Stockfish network in main assets, where it would be
// packaged into every Fairy APK. It belongs in src/stockfish/assets.
file("src/main/assets").listFiles { f -> f.name.endsWith(".nnue") }?.firstOrNull()?.let {
    throw GradleException("Move ${it.name} from app/src/main/assets to app/src/stockfish/assets")
}
// Play upload key: kept outside the repository. Override the location with
// -PsigningProperties=<path> or the SQUARECHESS_SIGNING environment variable.
val signingFile = file(providers.gradleProperty("signingProperties").orNull
    ?: System.getenv("SQUARECHESS_SIGNING")
    ?: "${System.getProperty("user.home")}/SquareChessSigning/keystore.properties")
val signing = Properties().apply { if (signingFile.exists()) signingFile.inputStream().use { load(it) } }
android {
    namespace = "com.dataespresso.squarechess"
    signingConfigs {
        if (signingFile.exists()) create("upload") {
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
        applicationIdSuffix = when { !fairyEngine -> ".stockfishbaseline"; devBuild -> ".dev"; else -> null }
        manifestPlaceholders["appLabel"] = when { !fairyEngine -> "Square Chess SF Baseline"; devBuild -> "Square Chess Dev"; else -> "Square Chess" }
        buildConfigField("boolean", "FAIRY_ENGINE", fairyEngine.toString())
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild { cmake {
            cppFlags += "-std=c++17"
            arguments += "-DFAIRY_ENGINE=${if(fairyEngine) "ON" else "OFF"}"
        } }
    }
    buildTypes {
        getByName("debug") {
            ndk { abiFilters += setOf("arm64-v8a", "x86_64") }
        }
        getByName("release") {
            // Symbol tables let Play Console show readable native crash reports.
            ndk { abiFilters += "arm64-v8a"; debugSymbolLevel = "SYMBOL_TABLE" }
            signingConfig = signingConfigs.findByName("upload")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    if(!fairyEngine) sourceSets.getByName("main").assets.srcDir("src/stockfish/assets")
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
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
