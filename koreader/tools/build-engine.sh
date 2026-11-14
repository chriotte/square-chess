#!/bin/sh
# Builds static Fairy-Stockfish UCI binaries for the KOReader plugin from the
# engine source the Android app uses (native/fairy), inside a Debian container.
#
#   koreader/tools/build-engine.sh            # all of armhf, aarch64, x86_64
#   koreader/tools/build-engine.sh armhf      # just one
#
# Output: koreader/squarechess.koplugin/bin/fairy-<arch>. The binaries are static,
# so they run on any Linux of that CPU type whatever its C library: Kindle (soft-
# or hard-float firmware), Kobo, PocketBook, reMarkable, and desktop Linux.
# Needs podman or docker.
set -eu
repo="$(cd "$(dirname "$0")/../.." && pwd)"
out="$repo/koreader/squarechess.koplugin/bin"
arches="${*:-armhf aarch64 x86_64}"
runtime="$(command -v podman || command -v docker)"
mkdir -p "$out"

"$runtime" run --rm \
    -v "$repo/native:/src/native:ro,Z" -v "$out:/out:Z" \
    -e ARCHES="$arches" docker.io/library/debian:bookworm sh -eu -c '
apt-get update -qq
pkgs="g++"
for a in $ARCHES; do
    case $a in
        armhf) pkgs="$pkgs g++-arm-linux-gnueabihf" ;;
        aarch64) pkgs="$pkgs g++-aarch64-linux-gnu" ;;
    esac
done
apt-get install -y -qq --no-install-recommends $pkgs >/dev/null
src=/src/native/fairy/src
files=$(ls $src/*.cpp $src/nnue/*.cpp $src/nnue/features/*.cpp $src/syzygy/*.cpp | grep -v -E "/(pyffish|ffishjs)\.cpp$")
common="-std=c++17 -O3 -DNNUE_EMBEDDING_OFF -DUSE_PTHREADS -DNDEBUG -I$src -I/src/native/fairy
    -ffile-prefix-map=/src=/src -static -pthread -s"
for a in $ARCHES; do
    case $a in
        # VFPv3 without NEON: the oldest jailbreakable Kindles and Kobos still run it.
        armhf) cxx=arm-linux-gnueabihf-g++; flags="-march=armv7-a -mfpu=vfpv3 -mfloat-abi=hard" ;;
        aarch64) cxx=aarch64-linux-gnu-g++; flags="-DIS_64BIT -DUSE_NEON -DUSE_POPCNT" ;;
        x86_64) cxx=g++; flags="-DIS_64BIT -DUSE_SSE2" ;;
        *) echo "unknown architecture $a" >&2; exit 2 ;;
    esac
    echo "building fairy-$a"
    $cxx $common $flags $files /src/native/fairy/sfio.cpp -o /out/fairy-$a 2>/dev/null
done
ls -la /out
'
