# Engine size and performance comparison

28 September 2026. Decimal MB (1,000,000 bytes). Fresh APK contents inspected with Python ZIP analysis; all final Fairy native libraries inspected with llvm-readelf and zipalign. APK size is not a Play download estimate.

| Item | Stockfish 19 | Fairy round two |
|---|---:|---:|
| Debug APK, ARM64 + x86_64 | 113,596,069 B | 33,433,732 B |
| Release unsigned APK, ARM64 | 104,432,227 B | 25,587,290 B |
| ARM64 engine library, debug | 1,640,760 B | 1,852,200 B |
| ARM64 engine library, release | 1,631,432 B | 1,844,632 B |
| NNUE asset, uncompressed | 98,511,183 B | 0 |
| NNUE compressed in debug APK | 80,631,410 B | 0 |
| Prior Titan installed app bytes | 113,703,424 B | 33,450,496 B |
| Prior Titan private data bytes | 98,750,464 B | 8,060,928 B |
| Prior Titan cache bytes | 0 B | 81,920 B |

Debug APK reduction: 70.6%; unsigned release reduction: 75.5%. The debug baseline is the other AI's isolated Stockfish build; release baseline is the preserved usability checkpoint archive. Neither is an R8-optimized Play bundle. The engine library itself is slightly larger with Fairy: eliminating NNUE explains the saving. The prior installed measurements are from the first calibration build, not a claim about a fresh empty install of round two. Benchmark traces and user games contribute to private data; do not attribute all Fairy data to the engine.

Stockfish's network is excluded from native embedding, compressed in the APK assets, and extracted once into private files for loading. Thus the APK copy and private extracted copy consume space. Fairy has no network in either location and performs no network extraction. No mandatory download is introduced. Optimizing the retained Stockfish loading mechanism is a separate review; it was not removed here.

## Response time and resource evidence

The prior Titan Fairy benchmark averaged about 408 ms across 150 searches, versus 212.8 ms across 30 Stockfish Level 2 searches. Fairy budgets 500 ms versus Stockfish Level 2's 260 ms, so these are product-configuration comparisons, not equal-budget engine-speed claims. The simple mate position finishes early and pulls the average down. See DIFFICULTY_RESULTS_SUMMARY.md and raw CSV for profile p95 results.

Round-two Titan: startup 197 ms; 180 legal searches; 137,202 ms benchmark wall time; 153,284 ms whole-process CPU; peak sampled process PSS 265,499 KiB; process VmHWM 430,072 KiB. These include Android, instrumentation, managed allocations and trace capture. They are not isolated engine memory or normal-game battery measurements. The CPU/wall ratio is approximately 1.12 cores across the instrumented process; the engine's search worker remains one. A follow-up Titan Stockfish Level 2 run recorded startup 575 ms, 30 legal searches, 7,245 ms benchmark wall time, 7,001 ms whole-process CPU, peak sampled PSS 210,192 KiB and VmHWM 451,040 KiB. Startup includes verifying an already extracted network; it is not a fresh-install extraction timing. The unequal sample counts and managed allocation/GC effects prevent treating these peaks or total CPU times as a controlled engine-only comparison. No memory or battery improvement is claimed.

No pondering is enabled; normal searches are bounded and lifecycle cancellation is preserved. A half-second search can use more energy per move than the 260 ms baseline despite a much smaller download. A phone energy/temperature experiment with controlled brightness, workload and charging state is still needed before claiming battery-life gains or extended thermal safety. Emulator thermal readings are not physical-device evidence.

## Packaging and 16 KB

Final Fairy debug and release APKs contain no .nnue entry. Both pass zipalign -c -P 16 -v 4. Every packaged .so has LOAD alignment at least 0x4000, including AndroidX dependencies. Evidence: alignment-16kb.txt. Both tested kernels report 4096-byte pages; 16 KB runtime compatibility remains unverified. There is no new universal-APK Play delivery claim or AAB size estimate in this prototype.
