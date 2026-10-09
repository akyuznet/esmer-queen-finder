"""Render the Play Store graphics from the launcher icon's vector drawables.

    python store/render_assets.py

Writes store/icon_512.png and store/feature_graphic_1024x500.png.
The Android vector XML is translated to SVG and rendered with Skia's SVG module.
"""
from __future__ import annotations

import math
import re
from pathlib import Path

import skia

ROOT = Path(__file__).resolve().parent
RES = ROOT.parent / "app" / "src" / "main" / "res" / "drawable"

HONEY = skia.Color(0xF2, 0xB7, 0x05)
INK = skia.Color(0x2B, 0x2B, 0x2B)


def rgba(v: str | None) -> tuple[str, float] | None:
    """#AARRGGBB or #RRGGBB -> ('#RRGGBB', opacity)."""
    if not v:
        return None
    v = v.lstrip("#")
    if len(v) == 8:
        return "#" + v[2:], int(v[:2], 16) / 255
    return "#" + v, 1.0


def vector_to_svg(xml: str) -> str:
    """Translate the subset of VectorDrawable used by the icon into SVG."""
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" height="108">']
    clip_id = 0
    open_groups: list[int] = []  # how many </g> each <group> must close
    for m in re.finditer(r"<(path|clip-path|group|/group)\b([^>]*?)/?>", xml, flags=re.S):
        tag, body = m.group(1), m.group(2)
        a = dict(re.findall(r'android:(\w+)="([^"]*)"', body))
        if tag == "group":
            out.append("<g>")
            open_groups.append(1)
            continue
        if tag == "/group":
            if open_groups:
                out.extend("</g>" for _ in range(open_groups.pop()))
            continue
        if tag == "clip-path":
            clip_id += 1
            out.append(f'<clipPath id="c{clip_id}"><path d="{a["pathData"]}"/></clipPath>')
            out.append(f'<g clip-path="url(#c{clip_id})">')
            if open_groups:
                open_groups[-1] += 1
            else:
                open_groups.append(1)
            continue
        attrs = [f'd="{a["pathData"]}"']
        fill = rgba(a.get("fillColor"))
        if fill and fill[1] > 0:
            attrs.append(f'fill="{fill[0]}" fill-opacity="{fill[1]:.3f}"')
        else:
            attrs.append('fill="none"')
        stroke = rgba(a.get("strokeColor"))
        if stroke:
            attrs.append(f'stroke="{stroke[0]}" stroke-opacity="{stroke[1]:.3f}" stroke-width="{a.get("strokeWidth", "1")}"')
            if a.get("strokeLineCap") == "round":
                attrs.append('stroke-linecap="round"')
            if a.get("strokeLineJoin") == "round":
                attrs.append('stroke-linejoin="round"')
        out.append("<path " + " ".join(attrs) + "/>")
    while open_groups:
        out.extend("</g>" for _ in range(open_groups.pop()))
    out.append("</svg>")
    return "\n".join(out)


def draw_svg(canvas: skia.Canvas, svg: str) -> None:
    stream = skia.MemoryStream(svg.encode("utf-8"), True)
    dom = skia.SVGDOM.MakeFromStream(stream)
    dom.setContainerSize(skia.Size(108, 108))
    dom.render(canvas)


def render_icon(size: int, rounded: bool = True) -> skia.Image:
    bg = vector_to_svg((RES / "ic_launcher_background.xml").read_text())
    fg = vector_to_svg((RES / "ic_launcher_foreground.xml").read_text())
    surface = skia.Surface(size, size)
    c = surface.getCanvas()
    c.clear(skia.ColorTRANSPARENT)
    if rounded:
        rr = skia.RRect.MakeRectXY(skia.Rect.MakeWH(size, size), size * 0.18, size * 0.18)
        c.clipRRect(rr, doAntiAlias=True)
    # Adaptive icons show the central 72 of the 108 dp canvas; scale that to the full image.
    c.scale(size / 72, size / 72)
    c.translate(-18, -18)
    draw_svg(c, bg)
    draw_svg(c, fg)
    return surface.makeImageSnapshot()


def render_feature(width: int = 1024, height: int = 500) -> skia.Image:
    surface = skia.Surface(width, height)
    c = surface.getCanvas()
    c.clear(HONEY)
    pen = skia.Paint(AntiAlias=True, Color=skia.Color(0xD9, 0x9E, 0x00), Style=skia.Paint.kStroke_Style, StrokeWidth=2)
    hexpath = skia.Path()
    r = 44
    for row in range(-1, 9):
        for col in range(-1, 16):
            cx = col * r * 1.75 + (r * 0.875 if row % 2 else 0)
            cy = row * r * 1.52
            pts = [(cx + r * math.cos(math.pi / 3 * k + math.pi / 6), cy + r * math.sin(math.pi / 3 * k + math.pi / 6)) for k in range(6)]
            hexpath.moveTo(*pts[0])
            for p in pts[1:]:
                hexpath.lineTo(*p)
            hexpath.close()
    c.drawPath(hexpath, pen)
    icon = render_icon(360)
    c.drawImage(icon, 70, 70)
    bold = skia.Font(skia.Typeface("Arial", skia.FontStyle.Bold()), 74)
    regular = skia.Font(skia.Typeface("Arial"), 34)
    ink = skia.Paint(AntiAlias=True, Color=INK)
    c.drawString("ESMER", 480, 200, bold, ink)
    c.drawString("Queen Finder", 480, 285, bold, ink)
    c.drawString("Spot the queen live. Free, offline.", 482, 350, regular, ink)
    return surface.makeImageSnapshot()


if __name__ == "__main__":
    render_icon(512).save(str(ROOT / "icon_512.png"), skia.kPNG)
    render_feature().save(str(ROOT / "feature_graphic_1024x500.png"), skia.kPNG)
    print("wrote", ROOT / "icon_512.png", "and", ROOT / "feature_graphic_1024x500.png")
