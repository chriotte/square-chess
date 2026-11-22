#!/bin/sh
# Builds the Kindle Package Manager package, so that `;kpm install squarechess`
# on a jailbroken Kindle installs the plugin (pulling in KOReader if needed).
#
#   koreader/tools/make-kpm-package.sh <output-dir>
#
# A .kpkg is a gzipped tar of the package folder's contents, as defined by
# KindleModding's kpm-helper.py (manifest_version 2). It is written here with
# tar rather than that tool, so the build needs nothing but python3 and tar.
set -eu
here="$(cd "$(dirname "$0")/.." && pwd)"
out="${1:?usage: make-kpm-package.sh <output-dir>}"
mkdir -p "$out"
out="$(cd "$out" && pwd)"

for a in armhf aarch64 x86_64; do
    [ -x "$here/squarechess.koplugin/bin/fairy-$a" ] || "$here/tools/build-engine.sh" "$a"
done

build="$out/squarechess"
rm -rf "$build"
mkdir -p "$build"
# The app repository keeps the packaging files under release/kpm; the release
# repository has them in kpm/.
if [ -d "$here/kpm" ]; then kpm="$here/kpm"; else kpm="$here/release/kpm"; fi
cp "$kpm/manifest.json" "$build/manifest.json"
for s in install.sh uninstall.sh launch.sh; do
    cp "$kpm/$s" "$build/$s"
    chmod +x "$build/$s"
done
cp -r "$here/squarechess.koplugin" "$build/squarechess.koplugin"
if [ -d "$here/engine" ]; then engine="$here/engine"; else engine="$here/../native/fairy"; fi
if [ -f "$here/LICENSE" ]; then licence="$here/LICENSE"; else licence="$here/../LICENSE"; fi
cp "$licence" "$build/squarechess.koplugin/LICENSE"
cp "$engine/Copying.txt" "$build/squarechess.koplugin/ENGINE-LICENSE.txt"

version="$(python3 -c 'import json,sys; print(".".join(str(n) for n in json.load(open(sys.argv[1]))["version"]))' "$build/manifest.json")"
kpkg="$out/squarechess_${version}_kindleany.kpkg"
rm -f "$kpkg"
# Entries are named without a "./" prefix: KPM looks "manifest.json" up by that
# exact name, and "./manifest.json" would not match it.
(cd "$build" && find . -mindepth 1 -maxdepth 1 -printf '%P\n' | sort | tar -czf "$kpkg" -T -)
echo "$kpkg"
