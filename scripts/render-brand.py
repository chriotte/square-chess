"""Render original Square Chess SVG into Android vectors and launcher/store PNGs.

Requires Python and CairoSVG (tested with 2.9.1). The editable source lives in
design/square-chess-icon.svg; no external artwork is used for this app identity.
"""
from pathlib import Path
import xml.etree.ElementTree as ET
from xml.sax.saxutils import quoteattr

import cairosvg
from PIL import Image

root = Path(__file__).resolve().parents[1]
res = root / "app/src/main/res"
source = root / "design/square-chess-icon.svg"
svg = ET.parse(source).getroot()
paths = svg.findall("{http://www.w3.org/2000/svg}path")

def write_vector(name, selected, compact=False):
    size = 72 if compact else 108
    lines = [f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{size}dp" android:height="{size}dp" android:viewportWidth="{size}" android:viewportHeight="{size}">']
    if compact:
        lines.append('  <group android:translateX="-18" android:translateY="-18">')
    for path in selected:
        attrs = {"pathData": path.get("d"), "fillColor": path.get("fill", "#000000")}
        if attrs["fillColor"] == "none":
            attrs["fillColor"] = "#00000000"
        for a, b in [("stroke", "strokeColor"), ("stroke-width", "strokeWidth"), ("stroke-linejoin", "strokeLineJoin")]:
            if path.get(a):
                attrs[b] = path.get(a)
        lines.append("    <path " + " ".join(f"android:{a}={quoteattr(v)}" for a, v in attrs.items()) + "/>")
    if compact:
        lines.append("  </group>")
    lines.append("</vector>")
    out = res / "drawable" / f"{name}.xml"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")

write_vector("ic_square_chess", paths, compact=True)
write_vector("ic_launcher_foreground", [p for p in paths if p.get("id") != "background"])

# Solid rook with transparent feature lines, for Android's themed icons.
mono = ET.Element("path", {
    "fill": "#FFFFFF",
    "d": "M34 31H43V39H50V31H58V39H65V31H74V47L68 52V67L75 72V78H33V72L40 67V52L34 47Z M40 48H68V51H40Z M41 66H67V69H41Z",
})
write_vector("ic_launcher_monochrome", [mono])
mono_file = res / "drawable/ic_launcher_monochrome.xml"
mono_file.write_text(mono_file.read_text().replace("<path ", '<path android:fillType="evenOdd" '), encoding="utf-8")

# Launcher fallback and store art use a tighter framing than adaptive layers.
svg.set("viewBox", "18 18 72 72")
compact_svg = ET.tostring(svg)
for density, pixels in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    dest = res / f"mipmap-{density}/ic_launcher.png"
    dest.parent.mkdir(parents=True, exist_ok=True)
    cairosvg.svg2png(bytestring=compact_svg, write_to=str(dest), output_width=pixels, output_height=pixels)
cairosvg.svg2png(bytestring=compact_svg, write_to=str(root / "design/play-store-icon.png"), output_width=512, output_height=512)
# CairoSVG can optimize fully opaque exports to RGB; keep a 32-bit RGBA
# store asset while retaining an entirely opaque background.
store_icon = root / "design/play-store-icon.png"
with Image.open(store_icon) as rendered:
    rendered.convert("RGBA").save(store_icon)
