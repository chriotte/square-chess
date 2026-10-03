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
./gradlew assembleDebug                # debug APK: arm64-v8a, armeabi-v7a, x86_64, x86 (emulators)
./gradlew check                        # unit tests, lint and verifyReleasePolicy
./gradlew verifyReleasePolicy          # no proprietary Google libraries; allow-listed permissions only
./gradlew assembleRelease              # release APK, arm64-v8a + armeabi-v7a
./gradlew bundleRelease                # release AAB for Google Play
```

The native engine (`native/`, Fairy-Stockfish) is compiled from source by CMake as part of every build. There are no prebuilt binaries in the repository.

## Build options

- `-Pdev=true` builds the separate test app `com.dataespresso.squarechess.dev` ("Square Chess Dev"). Device tests clear saved games and refuse to run in any other app, so run them only against this build.
- Release builds use R8 code optimization and resource shrinking. JNI names are kept by `app/proguard-rules.pro`; Room supplies its own consumer rules. The mapping file is `app/build/outputs/mapping/release/mapping.txt`. Keep the mapping from each published build to decode crash traces.
- `-Pdev=true -Pr8Test=true` selects the optimized release build for black-box device checks, uses the debug signing key, and adds x86 and x86_64 for emulators. It does not make the target app debuggable or add keep rules. These APKs are for tests only. Normal releases keep only the two ARM ABIs.
- Release signing is optional. A release build is signed when a properties file exists (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); otherwise it is unsigned. The default is `~/SquareChessSigning/keystore.properties` (Play upload key); `-PsigningProperties=<path>` or `SQUARECHESS_SIGNING` selects another, such as the standalone release key. See `docs/releases/signing.md`.

## Device tests

```sh
./gradlew -Pdev=true assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.dataespresso.squarechess.dev.test/androidx.test.runner.AndroidJUnitRunner
```

Emulator profiles for the target devices: `docs/testing/test-plan.md`.

Run the black-box checks against R8 before a release, as well as the full debug device suite above. The platform-only runner has no references to app classes: it does not need to preserve internal APIs that R8 can remove or change.

```sh
./gradlew -Pdev=true -Pr8Test=true assembleRelease assembleReleaseAndroidTest
adb install -r app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
adb shell pm clear com.dataespresso.squarechess.dev
adb shell am instrument -w com.dataespresso.squarechess.dev.test/com.dataespresso.squarechess.smoke.R8SmokeRunner
```

Run `check` separately without `-Pdev=true`; the release permission policy checks the production package name.
The R8 checks cover startup, migration of a version 1 database, legal moves, saving and reopening a game, history and the export dialog, a native engine reply, puzzles and hints, the standalone clock, and language switching. The Dev app must use English at the start. `pm clear` above clears only its test data.
