# Squire — Logo Export Checklist

**Primary mark:** Seal (A). Brass (`#B89558`) on Ink (`#0F0E0B`).

The source SVG lives inside `Squire Design.html` — open the Logo tab, inspect the "A · Seal" card, copy the inner `<svg>`. I can produce a clean standalone export on request.

## Exports needed

### Icon (for Modrinth, CurseForge, favicons)

| Size | Format | Background | Use |
|---|---|---|---|
| 512×512 | PNG | Ink `#0F0E0B` | Modrinth icon, CurseForge project avatar |
| 256×256 | PNG | Ink | Fallback / smaller hosts |
| 128×128 | PNG | Ink | GitHub social preview thumbnail cell |
| 64×64 | PNG | Ink | UI chrome, Discord role icons |
| 32×32 | PNG | Ink | Favicon (standard) |
| 16×16 | PNG | Ink | Favicon (small) — **simplify: drop inner shield ornament, keep outer shield + chevron only** |
| — | SVG | Transparent | Master source; scales infinitely |

### Wordmark (for banners, READMEs, patron pages)

| Size | Format | Background | Use |
|---|---|---|---|
| 2400×600 | PNG | Ink with radial brass wash | GitHub social preview, Modrinth banner, Twitter header |
| 1200×630 | PNG | Ink | OpenGraph / link previews |
| — | SVG horizontal lockup | Transparent | Master source |
| — | SVG stacked lockup (mark on top, wordmark below) | Transparent | Square-ish placements |

### Monochrome variants

Every export above should have three color variants as separate files:

- **Brass on Ink** (primary)
- **Vellum on Ink** (when brass reads too warm next to other warm content)
- **Ink on Vellum** (light backgrounds, print, README badges on white)

## Naming convention

```
squire-mark-512.png
squire-mark-256.png
squire-mark-16-simplified.png
squire-mark.svg

squire-lockup-horizontal-2400.png
squire-lockup-horizontal.svg
squire-lockup-stacked.svg

squire-mark-vellum-512.png     ← variant
squire-mark-ink-512.png        ← variant (for light bg)
```

## Cleanup notes

- The SVG in the design file uses `stroke-width="1.2"`. Before export, set **stroke** to `none` and **fill** to brass on the shield path — strokes render unpredictably at very small sizes. The simplified 16×16 version is fill-only.
- Flatten the inner shield + chevron into a single compound path before exporting to PNG. Some raster engines anti-alias gaps between adjacent strokes differently.
- Round the outer shield corners at 0.5px sub-pixel before PNG export to prevent aliasing artifacts on retina displays.

## If you want me to produce these

Reply "produce the logo exports" and I'll generate a zipped folder of all 20+ files with the correct backgrounds and naming. I can't ship true vector SVG → PNG rasterization perfectly in-browser, but I can get you press-ready SVGs and a full set of PNGs that'll hold up everywhere Modrinth and CurseForge ask for them.
