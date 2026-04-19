# Squire — Screenshot Capture Guide

**Goal:** 1 hero shot + 4 feature beats, all at 1920×1080, matching the gallery layout in the design file.

## Before you start

- **Resolution:** Set Minecraft to 1920×1080 fullscreen. Don't upscale later.
- **Render distance:** 16+ chunks. Fog kills depth in hero shots.
- **GUI scale:** 2 or 3. F1 to hide HUD for cinematic shots; leave it ON for UI-proof shots.
- **Time of day:** Specified per shot below. Use `/time set <value>` to force it.
- **Weather:** `/weather clear` before every capture. Rain reads as noise on a listing page.
- **Shaders:** Optional. If you use them, pick a subtle one (BSL on medium, or vanilla+). No bloom-heavy packs.

## The five shots

### 1. HERO — Squire patrolling at dusk *(the big one)*

**Used as:** Modrinth banner + CurseForge hero + design file's big gallery cell.

- **Time:** `/time set 13000` (golden/blue-hour transition)
- **Location:** A mid-distance wide shot. Flat-ish terrain with a tree line or stone ridge behind the squire. Avoid built structures — keep it "on the road."
- **Camera:** Third-person (`F5`), over-the-shoulder of the squire at ~40° angle. Squire walking forward, player (invisible) trailing.
- **Composition:** Squire occupies left third. Horizon in lower third. Empty sky or distant silhouette in right two-thirds — room for the wordmark overlay if we need one later.
- **HUD:** OFF (`F1`).

### 2. Mining shaft

- **Time:** `/time set 6000` (surface day, makes the shaft entry glow pop)
- **Setup:** Capture the squire mid-descent in a branch-mine. Torches on walls. Pile of ore visible in the frame (not yet deposited).
- **Camera:** Third-person, looking down the shaft with the squire 6–8 blocks below you.
- **HUD:** OFF.

### 3. Farm row

- **Time:** `/time set 1000` (clean morning light)
- **Setup:** A 9×3 wheat row, half harvested. Squire in motion — breaking or replanting, not idle.
- **Camera:** Third-person, low angle along the row so you see depth.
- **HUD:** OFF.

### 4. Mounted patrol

- **Time:** `/time set 14000` (early night, torches helpful)
- **Setup:** Squire on horseback with a sword equipped, engaging or just past a skeleton. Torch or lantern in frame for warm light.
- **Camera:** Third-person, slight low angle for heroism.
- **HUD:** OFF.

### 5. The Squire's Manual *(UI-proof)*

- **Time:** Any.
- **Setup:** Patchouli book open to the **Roles** page (or whichever page looks best). Player holding the book.
- **Camera:** First-person, book centered.
- **HUD:** ON — we want to show this is real in-game UI.

## Post-capture

- **Crop:** Don't. Keep the full 1920×1080.
- **Compress:** Use a lossless PNG → then optionally a WebP copy at quality 85 for Modrinth (smaller uploads, same visual fidelity).
- **Naming:**
  ```
  01-hero-patrol-dusk.png
  02-mining-shaft.png
  03-farm-row.png
  04-mounted-patrol.png
  05-guidebook.png
  ```
- **Drop them into the project** (or reply with them) and I'll swap the gallery placeholders for real images.

## One more nice-to-have

A **15-second looping GIF or WebM** of the squire working autonomously — mining a shaft start-to-finish, or doing one full farm loop. Pin it as the second gallery slot on Modrinth; it converts vastly better than stills.
