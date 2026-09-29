# Square Chess identity

Original geometric rook over two board squares, using the app's ink, sand,
and muted green palette. This identity is separate from the third-party
Chessnut pieces used on the playable board.

- `square-chess-icon.svg`: editable source, 108-unit adaptive-icon canvas.
- Store icon (512 x 512, opaque, no baked-in rounded corners or shadow),
  feature graphic and screenshots: `fastlane/metadata/android/en-US/images/`,
  used by Google Play and F-Droid.
- Android foreground/background layers and a separate monochrome rook are
  bundled for adaptive and themed icons. Legacy PNG densities are included.
- The compact vector mark is also used beside the landing-screen title.

Run `python scripts/render-brand.py` from the repository root to regenerate
the Android resources and PNG exports. Requires CairoSVG (tested with 2.9.1).

Physical verification: Android App info on the connected Titan 2 displays
the new application icon. Themed wallpaper tinting and alternative launcher
masks have not been device-tested in this pass.
