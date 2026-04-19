# Design Concepts — Awaiting Final Assets + Code Integration

⚠️ **The PNG files in this folder are approved design concepts / mockups, NOT production-ready textures.** They communicate layout, palette, and intent. Final production-grade assets (actual pixel art aligned to Minecraft's texture conventions) have not yet been delivered.

Do not copy these into `src/main/resources/assets/` without replacing them with the real versions first.

## squire_inventory.png (+ 4x preview)

**What it is:** A flat-shaded mockup of the heraldic-panel inventory GUI — Ink header with Seal corner mark, Vellum body, Brass hairlines, 6 equipment slots on the left, 5×4 inventory grid on the right, status bar at bottom.

**Status:** Concept approved. Waiting for:
1. Final pixel-art texture matching Minecraft GUI conventions (slot borders, anti-aliasing, alpha edges).
2. Final item icons (8 items — see the handoff briefing) so the whole visual release can ship together.

**When both land, code integration:** Rewrite `SquireScreen.renderBg()` to load the texture via `g.blit()`, align slot positions in `SquireMenu` to the PNG's grid. Target: v3.1.0.

## What's NOT in this folder but is coming

- 8 item icons: Halberd, Shield, Helmet, Chestplate, Leggings, Boots, Crest, Guidebook. Crest redesign is especially critical (current pixel art is too detailed for 16×16).
- Possibly entity skin revisions for squire_male / squire_female.

## Current production state (live in v3.0.2)

The in-game GUI uses a palette swap (Ink/Brass/Vellum/Claret/Moss) via code constants in `SquireScreen.java`. Items use auto-expanded versions of the legacy textures (see `docs/design/_legacy/items/` for before-state). This is the bridge until real assets arrive.
