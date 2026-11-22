#!/bin/sh
# Assembles the standalone repository that KOReader users install from:
#
#   koreader/tools/make-release-repo.sh <target-dir>
#
# The app repository stays the place the plugin is developed. The release
# repository is this folder's contents, flattened, with the engine source
# beside the plugin so it can be built from a single checkout. Run it again
# for each release and commit the result in the target.
set -eu
here="$(cd "$(dirname "$0")/.." && pwd)"
repo="$(cd "$here/.." && pwd)"
target="${1:?usage: make-release-repo.sh <target-dir>}"
mkdir -p "$target"

rm -rf "$target/squarechess.koplugin" "$target/engine" "$target/tools" "$target/tests"
cp -r "$here/squarechess.koplugin" "$target/squarechess.koplugin"
rm -rf "$target/squarechess.koplugin/bin"
cp -r "$repo/native/fairy" "$target/engine"
cp -r "$here/tools" "$target/tools"
rm -f "$target/tools/make-release-repo.sh"
cp -r "$here/tests" "$target/tests"
cp "$repo/LICENSE" "$target/LICENSE"
cp "$here/release/README.md" "$target/README.md"
cp "$here/release/CHANGELOG.md" "$target/CHANGELOG.md"
cp "$here/release/gitignore" "$target/.gitignore"
mkdir -p "$target/docs"
cp "$here/release/docs/"*.png "$target/docs/" 2>/dev/null || true

echo "assembled $target"
