#!/bin/sh
# Builds koreader/dist/squarechess.koplugin-<version>.zip, ready to unzip into
# KOReader's plugins folder. Builds the engine binaries first if they are missing.
# The licence texts the GPL requires alongside the binaries are copied in for the
# zip and removed again afterwards, so they are not duplicated in the repository.
set -eu
here="$(cd "$(dirname "$0")/.." && pwd)"
plugin="$here/squarechess.koplugin"
for a in armhf aarch64 x86_64; do
    [ -x "$plugin/bin/fairy-$a" ] || "$here/tools/build-engine.sh" "$a"
done
# A release repository keeps the engine source next to the plugin; the app
# repository keeps it in native/fairy.
if [ -d "$here/engine" ]; then engine="$here/engine"; else engine="$here/../native/fairy"; fi
if [ -f "$here/LICENSE" ]; then licence="$here/LICENSE"; else licence="$here/../LICENSE"; fi
cp "$licence" "$plugin/LICENSE"
cp "$engine/Copying.txt" "$plugin/ENGINE-LICENSE.txt"
version="${1:-$(git -C "$here" describe --tags --always --dirty 2>/dev/null || echo dev)}"
mkdir -p "$here/dist"
zip_path="$here/dist/squarechess.koplugin-$version.zip"
rm -f "$zip_path"
(cd "$here" && zip -qr -X "$zip_path" squarechess.koplugin -x '*.DS_Store')
rm -f "$plugin/LICENSE" "$plugin/ENGINE-LICENSE.txt"
echo "$zip_path"
