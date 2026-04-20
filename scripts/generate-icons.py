#!/usr/bin/env python3
"""
Generate Squire Mod v2 default item icons.

Two sources, two different mechanisms:

1. **Crest (32×32)** — downscaled from src/main/resources/logo.png (253×253 canonical
   Heraldic Seal art, used unchanged for the mod-menu logo). Lanczos resample keeps
   the detail legible at hotbar scale; MC's item renderer then nearest-neighbors it at
   draw time, which is fine — the downscale is the only lossy step.

2. **Guidebook (16×16)** — hand-pixeled in Pillow using the Squire brand palette (see
   docs/design/handoff/BRAND.md). The guidebook is a book, not a seal, so the logo
   motif doesn't apply.

Design rules honored (BRAND.md "draw rules"):
- Monochromatic — brass on ink, ink on brass. No gradients.
- No rounded corners above 2px.
- Stroke width ≥ 1px at icon scale.
- No green (SJVIK territory).

Output:
  src/main/resources/assets/squire/textures/item/squire_crest.png     (32×32)
  src/main/resources/assets/squire/textures/item/squire_guidebook.png (16×16)

Re-run any time — overwrites in place. If logo.png changes, the crest auto-refreshes.
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

REPO = Path(__file__).resolve().parents[1]
LOGO_SRC = REPO / "src/main/resources/logo.png"
ASSETS = REPO / "src/main/resources/assets/squire/textures/item"


# ---------------------------------------------------------------------------
# Squire's Crest — 32×32
#
# Derived from the canonical Heraldic Seal PNG (logo.png, 253×253) via Lanczos
# downscale. Keeping a single source of truth for the brand mark means the item
# icon, mod-menu logo, and website hero all stay in visual sync — changing the
# logo file refreshes every derivative with one script run.
#
# Alternative considered: hand-pixel a 32×32 version independently. Rejected
# because the two would drift as the brand art evolves; the Lanczos result is
# legible at hotbar scale and that's what the item icon needs.
# ---------------------------------------------------------------------------
def draw_crest() -> Image.Image:
    src = Image.open(LOGO_SRC).convert("RGBA")
    return src.resize((32, 32), Image.LANCZOS)


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
