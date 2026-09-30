# Building Square Chess

## Requirements

| Tool | Version |
|---|---|
| JDK | 21 (releases; F-Droid builds with 21) |
| Gradle | 8.13 (wrapper included; use `./gradlew`) |
| Android Gradle Plugin | 8.13.0 |
| Kotlin | 2.2.10 |
| Android SDK | platform 36, build-tools 36.0.0 |
| NDK | 28.2.13676358 |
| CMake | 3.22.1 |

Set `ANDROID_HOME` (or `sdk.dir` in an untracked `local.properties`) and `JAVA_HOME`. The build downloads Gradle dependencies from Google Maven and Maven Central only. The app itself needs no network.

## Common tasks

```sh
./gradlew testDebugUnitTest            # JVM unit tests
./gradlew assembleDebug                # debug APK: arm64-v8a + x86_64 (emulators)
./gradlew check                        # unit tests, lint and verifyReleasePolicy
./gradlew verifyReleasePolicy          # no proprietary Google libraries; allow-listed permissions only
./gradlew assembleRelease              # release APK, arm64-v8a
./gradlew bundleRelease                # release AAB for Google Play
```

The native engine (`native/`, Fairy-Stockfish) is compiled from source by CMake as part of every build. There are no prebuilt binaries in the repository.

## Build options

- `-Pdev=true` builds the separate test app `com.dataespresso.squarechess.dev` ("Square Chess Dev"). Device tests clear saved games and refuse to run in any other app, so run them only against this build.
- Release signing is optional. A release build is signed when a properties file exists (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); otherwise it is unsigned. The default is `~/SquareChessSigning/keystore.properties` (Play upload key); `-PsigningProperties=<path>` or `SQUARECHESS_SIGNING` selects another, such as the standalone release key. See `docs/releases/signing.md`.

## Device tests

```sh
./gradlew -Pdev=true assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.dataespresso.squarechess.dev.test/androidx.test.runner.AndroidJUnitRunner
```

Emulator profiles for the target devices: `docs/testing/test-plan.md`.
