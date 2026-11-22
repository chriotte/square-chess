#!/bin/sh
# Removes the plugin from KOReader. Saved games and puzzle progress live in
# KOReader's own settings folder and are left alone.
set -eu

plugins=/mnt/us/koreader/plugins

if [ "${1:-}" = "upgrade" ]; then
    # The new version's install.sh replaces the folder.
    exit 0
fi

rm -rf "$plugins/squarechess.koplugin"
echo "Square Chess removed."
