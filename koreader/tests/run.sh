#!/bin/sh
# Runs the plugin's pure-Lua tests. Uses LuaJIT from PATH, or KOREADER_DIR/luajit.
set -e
here="$(dirname "$0")"
lua="$(command -v luajit || true)"
[ -z "$lua" ] && [ -n "$KOREADER_DIR" ] && lua="$KOREADER_DIR/luajit"
[ -z "$lua" ] && { echo "Need luajit on PATH or KOREADER_DIR set to a KOReader install" >&2; exit 2; }
for t in "$here"/*_test.lua; do "$lua" "$t"; done
