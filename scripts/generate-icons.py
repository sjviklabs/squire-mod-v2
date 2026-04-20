#!/usr/bin/env python3
"""
Generate Squire Mod v2 default item icons from scratch.

Pixel-accurate rendering in the Squire brand palette (see docs/design/handoff/BRAND.md):
- Ink    #0F0E0B — dark background
- Brass  #B89558 — primary accent
- Vellum #E8DFC9 — parchment highlight
- Claret #7A2A2A — alert (unused here, reserved)

Design rules honored:
- Monochromatic — brass on ink, ink on brass. No gradients.
- No rounded corners above 2px (hand-pixeled means no anti-aliasing, no false-AA rounds).
- Stroke width ≥ 1px at icon scale.
- No green (sjvik territory).

Output:
  src/main/resources/assets/squire/textures/item/squire_crest.png     (32×32)
  src/main/resources/assets/squire/textures/item/squire_guidebook.png (16×16)

Re-run any time — overwrites in place. The script is the source of truth; the PNGs
are regeneratable artifacts.
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

# ---------------------------------------------------------------------------
# Palette — single source of truth, matches docs/design/handoff/BRAND.md
# ---------------------------------------------------------------------------
INK = (15, 14, 11, 255)
SLATE = (37, 33, 25, 255)
VELLUM = (232, 223, 201, 255)
BRASS = (184, 149, 88, 255)
DEEP_BRASS = (110, 90, 56, 255)
CLEAR = (0, 0, 0, 0)

ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/squire/textures/item"


# ---------------------------------------------------------------------------
# Squire's Crest — 32×32
#
# Heraldic Seal motif from BRAND.md: circular seal with inner shield and crossed
# pickaxe + sword (Trades Mark). The crest is the primary summoning item, so it
# gets the full heraldic treatment rather than just the shield alone.
#
# At 32×32 we have enough pixels for: outer ring, inner field, heater shield,
# and the crossed tools. Stroke width 1-2px per BRAND rules.
# ---------------------------------------------------------------------------
def draw_crest() -> Image.Image:
    size = 32
    img = Image.new("RGBA", (size, size), CLEAR)
    d = ImageDraw.Draw(img)
    cx, cy = 15.5, 15.5

    # Dark ink field inside the outer ring — gives the icon weight against the
    # hotbar background. Ring at r=14.5 (ellipse bbox 1..30), fill at r=13.
    d.ellipse((1, 1, 30, 30), fill=INK, outline=BRASS, width=1)

    # Inner concentric ring — echo from the website's seal, scaled down.
    # Bbox 4..27 gives r≈11.5.
    d.ellipse((4, 4, 27, 27), outline=DEEP_BRASS, width=1)

    # Heater shield inside the seal. Bounds roughly x 8..23, y 8..26. Classic
    # heater shape: straight top, straight sides top half, curved-in to point.
    # Drawn with polygon for the pointed bottom, then top drawn as rectangle.
    shield_top = 8
    shield_bot = 24
    shield_l = 9
    shield_r = 22
    shield_points = [
        (shield_l, shield_top),
        (shield_r, shield_top),
        (shield_r, shield_top + 6),
        (cx, shield_bot),
        (shield_l, shield_top + 6),
    ]
    d.polygon(shield_points, outline=BRASS)

    # Trades Mark — crossed pickaxe + sword inside the shield. Two diagonal
    # strokes from inner shield corners, with brass dots on the handle ends
    # to echo the outer ring punctuation. Thin 1px strokes; at 32×32 this
    # reads cleanly without smudging into the shield outline.
    # First diagonal: top-left to bottom-right
    d.line((12, 12, 20, 20), fill=BRASS, width=1)
    # Second diagonal: top-right to bottom-left
    d.line((20, 12, 12, 20), fill=BRASS, width=1)
    # Handle-end dots (brass, 1px)
    for (x, y) in ((12, 12), (20, 12), (12, 20), (20, 20)):
        d.point((x, y), fill=BRASS)

    return img


# ---------------------------------------------------------------------------
# Squire's Guidebook — 16×16
#
# Book shape (vertical rectangle with spine accent), brass border, small
# diamond (Section Rule motif) at the center of the cover. Tight space so we
# skip the full Trades Mark and use the simpler Section Rule diamond.
# ---------------------------------------------------------------------------
def draw_guidebook() -> Image.Image:
    size = 16
    img = Image.new("RGBA", (size, size), CLEAR)
    d = ImageDraw.Draw(img)

    # Book body — slightly taller than wide, dark ink field with brass border.
    # Bbox x 2..13, y 1..14 (12×14 rectangle).
    d.rectangle((2, 1, 13, 14), fill=INK, outline=BRASS, width=1)

    # Spine accent — single-pixel brass column on the left edge inside the
    # border. Suggests bound pages without needing detail we don't have
    # pixels for.
    d.line((4, 2, 4, 13), fill=DEEP_BRASS, width=1)

    # Section-rule diamond at cover center. 2-pixel diamond made from a cross
    # of single pixels around (8,7) — the classic 4-point heraldic diamond at
    # minimum resolution.
    cx, cy = 8, 7
    for (dx, dy) in ((0, -1), (1, 0), (0, 1), (-1, 0)):
        d.point((cx + dx, cy + dy), fill=BRASS)
    d.point((cx, cy), fill=VELLUM)

    # Lower cover rule — a single horizontal brass line below the diamond,
    # evoking the section rule from BRAND.md without the full ornament.
    d.line((6, 10, 10, 10), fill=DEEP_BRASS, width=1)

    return img


def main() -> None:
    ASSETS.mkdir(parents=True, exist_ok=True)
    out_crest = ASSETS / "squire_crest.png"
    out_guide = ASSETS / "squire_guidebook.png"

    draw_crest().save(out_crest)
    draw_guidebook().save(out_guide)

    print(f"wrote {out_crest.relative_to(ASSETS.parents[3])}")
    print(f"wrote {out_guide.relative_to(ASSETS.parents[3])}")


if __name__ == "__main__":
    main()
