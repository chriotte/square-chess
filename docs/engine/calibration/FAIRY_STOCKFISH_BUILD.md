# Reproducing the isolated Fairy build

Working checkout: ../square-chess-fairy, branch experiment/fairy-stockfish, based on f4711c36a2f6365e4f2083c84d148e00264f85f1. Production source is preserved in ../square-chess-v11-review and native/stockfish. See native/fairy/PROVENANCE.md for exact vendored provenance and the single engine restart fix.

Toolchain: JDK 17.0.20.1+1; Gradle 8.13; Android SDK 36; NDK 28.2.13676358; CMake 3.22.1. local.properties points to the installed SDK and is intentionally not distributed. Standard Gradle dependencies must be cached/downloaded at build time; the installed app works offline.

From the checkout with JAVA_HOME and SDK configured:

```powershell
gradle clean testDebugUnitTest testReleaseUnitTest assembleDebug assembleDebugAndroidTest assembleRelease
```

Default engine is Fairy. Debug packages ARM64 and x86_64; release packages ARM64. CMake uses O3, NNUE_EMBEDDING_OFF, one runtime search worker, and 16384 maximum ELF page alignment. The release APK is unsigned; the debug APK is the installable review artifact. Neither is a production Play release.

For the preserved Stockfish baseline, supply the exact verified nn-1a298aa575a0.nnue asset in app/src/stockfish/assets and build with -Pengine=stockfish. The baseline asset source set is excluded from Fairy. Do not switch variants and trust an old output APK; check its manifest/package and ZIP contents. A clean final package avoids stale incremental ZIP padding.

Install the app and test APK with adb install -r. On an isolated emulator run the full AndroidJUnitRunner suite. Fixture tests can clear experiment data, so never run the entire suite against the user's phone. The native FairyEngineDeviceTest methods do not clear saved games; select those explicitly for physical-device checks. Benchmark and self-play require engineBenchmark=true and engineSelfPlay=true respectively and otherwise skip. Raw logs distinguish skipped methods from executed tests.

The benchmark writes files/engine-experiment/results.csv, summary.txt, options.txt and full UCI traces within the experiment package. Each position resets hash and uses six repetitions. Do not confuse final search depth with the handicapped move-selection depth. Export via run-as without clearing user data. Preserve old results before the next run.

Use zipalign -c -P 16 -v 4 and inspect every packaged .so LOAD segment with llvm-readelf. These are static checks, not proof of runtime behavior on a 16 KB kernel. Actual kernel page size is recorded separately.

No source or binaries have been published. Before external binary distribution, prepare the exact matching corresponding-source delivery; do not distribute only an APK plus an upstream engine link.
