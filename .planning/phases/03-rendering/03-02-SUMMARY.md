---
phase: 03-rendering
plan: "02"
subsystem: rendering
tags: [geckolib, geo-json, animation, textures, assets, blockbench]
dependency_graph:
  requires: [03-01-geckolib-wiring]
  provides: [squire_male.geo.json, squire_female.geo.json, squire.animation.json, placeholder-textures]
  affects: [03-03-armor-backpack-layers]
tech_stack:
  added: []
  patterns:
    - "Geckolib geo.json format_version 1.21.0 (geometry files only — animation files use 1.8.0)"
    - "Bedrock bone hierarchy: root > body > head/arms/backpacks; root > legs > feet"
    - "slim arm: 3px wide (female) vs 4px wide (male), origin shifts accordingly"
    - "Placeholder PNG: raw RGBA bytes + zlib IDAT chunk — no image editor required"
key_files:
  created:
    - src/main/resources/assets/squire/geo/squire_male.geo.json
    - src/main/resources/assets/squire/geo/squire_female.geo.json
    - src/main/resources/assets/squire/animations/squire.animation.json
    - src/main/resources/assets/squire/textures/entity/squire_male.png
    - src/main/resources/assets/squire/textures/entity/squire_female.png
    - src/main/resources/assets/squire/textures/models/armor/squire_layer_1_t0.png
    - src/main/resources/assets/squire/textures/models/armor/squire_layer_1_t1.png
    - src/main/resources/assets/squire/textures/models/armor/squire_layer_1_t2.png
    - src/main/resources/assets/squire/textures/models/armor/squire_layer_1_t3.png
    - src/main/resources/assets/squire/textures/models/armor/squire_layer_2_t0.png
    - src/main/resources/assets/squire/textures/models/armor/squire_layer_2_t1.png
    - src/main/resources/assets/squire/textures/models/armor/squire_layer_2_t2.png
    - src/main/resources/assets/squire/textures/models/armor/squire_layer_2_t3.png
  modified: []
decisions:
  - "format_version 1.21.0 for geo.json, 1.8.0 for animation.json — these are different Bedrock format versions and must not be swapped"
  - "Female slim arms: origin x shifts from -8 to -7 (right) and 4 to 5 (left) when arm width drops 4->3 — keeps arm flush with body edge"
  - "Placeholder PNGs are 64x64 RGBA solid-color — valid PNG structure with correct IHDR/IDAT/IEND chunks, no palette tricks"
metrics:
  duration_minutes: 6
  completed_date: "2026-04-03"
  tasks_completed: 2
  files_created: 13
  files_modified: 0
requirements_met: [RND-01, RND-02, RND-05, RND-06, INV-04]
---

# Phase 3 Plan 2: Geckolib Asset Files Summary

Geckolib geometry, animation, and placeholder texture assets authored — bone naming contract frozen for 03-03 SquireArmorLayer consumption.

## What Was Built

**squire_male.geo.json** and **squire_female.geo.json** — Geckolib 1.21.0 format humanoid placeholder models:
- 11 bones total: root, body, head, right_arm, left_arm, right_leg, left_leg, right_foot, left_foot, backpack_small, backpack_large
- All 6 armor-slot bones (head, body, right_leg, left_leg, right_foot, left_foot) have at least one cube — required for ItemArmorGeoLayer to render
- Female variant uses 3px arm width vs 4px (male), arm origins adjusted to stay flush with body
- `texture_width: 64, texture_height: 64` on both

**squire.animation.json** — 4 animations wired to the RawAnimation statics defined in SquireEntity (03-01):
- `animation.squire.idle` — 2.0s loop, subtle head bob
- `animation.squire.walk` — 1.0s loop, leg/arm swing at ±30 degrees
- `animation.squire.sprint` — 0.5s loop, exaggerated ±45 degree swing, body tilted forward -10 degrees
- `animation.squire.attack` — 0.5s one-shot (loop: false), right arm wind-up to -90 then follow-through

**Placeholder textures** — 10 valid 64x64 PNG files, solid-color per tier:
- `textures/entity/squire_male.png` (blue-gray), `squire_female.png` (tan)
- `textures/models/armor/squire_layer_1_t0..t3.png` — outer armor tiers: gray/bronze/silver/gold
- `textures/models/armor/squire_layer_2_t0..t3.png` — inner armor tiers: darker versions of above

## Bone Naming Contract (FROZEN — 03-03 builds against these)

| Bone name      | Armor slot   | Parent    | Has Cubes |
|----------------|--------------|-----------|-----------|
| root           | —            | (none)    | no        |
| body           | CHEST        | root      | yes       |
| head           | HEAD         | body      | yes       |
| right_arm      | —            | body      | yes       |
| left_arm       | —            | body      | yes       |
| right_leg      | LEGS         | root      | yes       |
| left_leg       | LEGS         | root      | yes       |
| right_foot     | FEET         | right_leg | yes       |
| left_foot      | FEET         | left_leg  | yes       |
| backpack_small | — (visual)   | body      | yes       |
| backpack_large | — (visual)   | body      | yes       |

**Do not rename these bones.** 03-03 SquireArmorLayer references them by exact string match.

## Animation Names (must match SquireEntity RawAnimation statics)

| Animation name          | loop  | Length | Bones animated          |
|-------------------------|-------|--------|-------------------------|
| animation.squire.idle   | true  | 2.0s   | head                    |
| animation.squire.walk   | true  | 1.0s   | right_leg, left_leg, right_arm, left_arm |
| animation.squire.sprint | true  | 0.5s   | right_leg, left_leg, right_arm, left_arm, body |
| animation.squire.attack | false | 0.5s   | right_arm               |

## Texture Paths (exact — must match SquireModel ResourceLocation declarations)

```
assets/squire/geo/squire_male.geo.json
assets/squire/geo/squire_female.geo.json
assets/squire/animations/squire.animation.json
assets/squire/textures/entity/squire_male.png
assets/squire/textures/entity/squire_female.png
assets/squire/textures/models/armor/squire_layer_1_t0.png
assets/squire/textures/models/armor/squire_layer_1_t1.png
assets/squire/textures/models/armor/squire_layer_1_t2.png
assets/squire/textures/models/armor/squire_layer_1_t3.png
assets/squire/textures/models/armor/squire_layer_2_t0.png
assets/squire/textures/models/armor/squire_layer_2_t1.png
assets/squire/textures/models/armor/squire_layer_2_t2.png
assets/squire/textures/models/armor/squire_layer_2_t3.png
```

## Deviations from Plan

None — plan executed exactly as written.

## Commits

| Task | Commit  | Description |
|------|---------|-------------|
| 1    | 9595ed1 | squire_male and squire_female geo.json models |
| 2    | 7c8459c | animation JSON and placeholder texture PNGs |

## Self-Check: PASSED
