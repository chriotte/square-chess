cmake_minimum_required(VERSION 3.22.1)
project(squarechess)
# Fairy-Stockfish (native/fairy, see PROVENANCE.md) with classical evaluation.
# The earlier Stockfish 19 build is documented in docs/engine/stockfish19-baseline.md.
file(GLOB_RECURSE FAIRY_SOURCES fairy/src/*.cpp)
list(FILTER FAIRY_SOURCES EXCLUDE REGEX "/(main|pyffish|ffishjs).cpp$")
add_library(squarefish SHARED fairy_bridge.cpp fairy/stockfish_variant.cpp fairy/sfio.cpp ${FAIRY_SOURCES})
target_include_directories(squarefish PRIVATE fairy fairy/src)
target_compile_features(squarefish PRIVATE cxx_std_17)
target_compile_definitions(squarefish PRIVATE NNUE_EMBEDDING_OFF USE_PTHREADS NDEBUG)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_definitions(squarefish PRIVATE IS_64BIT USE_NEON USE_POPCNT)
elseif(ANDROID_ABI STREQUAL "x86_64")
    target_compile_definitions(squarefish PRIVATE IS_64BIT USE_SSE2)
elseif(ANDROID_ABI STREQUAL "armeabi-v7a" OR ANDROID_ABI STREQUAL "x86")
    # 32-bit devices (Android 8 e-ink tablets; x86 only for the emulator): portable code paths.
else()
    message(FATAL_ERROR "Unsupported Android ABI: ${ANDROID_ABI}")
endif()
target_compile_options(squarefish PRIVATE -O3 -fexceptions -frtti)
# Reproducible builds (F-Droid): the unstripped library holds file paths, and the
# linker's build ID is a hash of it. Map the source, build and NDK folders to fixed
# names so the library, and its build ID, are the same in any checkout folder.
target_compile_options(squarefish PRIVATE
    "-ffile-prefix-map=${CMAKE_SOURCE_DIR}=/src"
    "-ffile-prefix-map=${CMAKE_BINARY_DIR}=/build"
    "-ffile-prefix-map=${ANDROID_NDK}=/ndk")
target_link_options(squarefish PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(squarefish android log)
