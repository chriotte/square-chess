#!/bin/sh
# Builds koreader/dist/squarechess.koplugin-<version>.zip, ready to unzip into
# KOReader's plugins folder. Builds the engine binaries first if they are missing.
set -eu
here="$(cd "$(dirname "$0")/.." && pwd)"
plugin="$here/squarechess.koplugin"
for a in armhf aarch64 x86_64; do
    [ -x "$plugin/bin/fairy-$a" ] || "$here/tools/build-engine.sh" "$a"
done
cp "$here/../LICENSE" "$plugin/LICENSE"
version="$(git -C "$here" describe --tags --always --dirty 2>/dev/null || echo dev)"
mkdir -p "$here/dist"
zip_path="$here/dist/squarechess.koplugin-$version.zip"
rm -f "$zip_path"
(cd "$here" && zip -qr -X "$zip_path" squarechess.koplugin -x '*.DS_Store')
rm -f "$plugin/LICENSE"
echo "$zip_path"
