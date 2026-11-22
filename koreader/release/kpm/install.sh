#!/bin/sh
# Copies the plugin into KOReader's plugins folder on the user's storage.
set -eu

plugins=/mnt/us/koreader/plugins

if [ ! -d /mnt/us/koreader ]; then
    echo "KOReader is not installed. Run: kpm install koreader"
    exit 1
fi

mkdir -p "$plugins"
rm -rf "$plugins/squarechess.koplugin"
cp -r ./squarechess.koplugin "$plugins/squarechess.koplugin"
chmod +x "$plugins/squarechess.koplugin/bin/"fairy-* 2>/dev/null || true

echo "Square Chess installed. Open KOReader, then Tools > Square Chess."
