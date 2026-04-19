# Squire — Listing Copy

**Paste-ready for Modrinth and CurseForge.** Both platforms support Markdown.

---

## Short description (Modrinth summary / CurseForge summary)

> A loyal NPC companion that walks, fights, mines, farms, fishes, and patrols — no teleporting, no cheating. The henchman mod for NeoForge 1.21.1.

_160 chars. Stays under both platforms' limits._

---

## Tags / Categories

**Modrinth:** Adventure, Mobs, Utility, Automation  
**CurseForge:** Adventure, Mobs, Server Utility, Automation, Quality of Life  
**Loader:** NeoForge  **Version:** 1.21.1  **Environment:** Client & Server required

---

## Long description — paste below this line

```markdown
# Squire

> *A loyal hand. Do things the hard way.*

You don't want a pet. You want a hand — someone who keeps working when you log off the shaft and pop up for a snack, someone who can be trusted with a sword and a patrol route, someone who remembers the way home.

**Squire** adds a single, serious NPC companion: a squire that plays by the same rules you do. It walks, swims, eats, and sleeps. It fights with the weapon you hand it. It mines in shafts, farms in rows, fishes where the water is deep enough. No teleport pings. No free labor. You hire it, you equip it, and it earns its keep.

---

## What it does

**Plays like a player.** Walks, swims, eats, rests. No teleporting to you, no shortcuts through terrain. If the route is broken, it finds another — or it stops and waits.

**Autonomous work.** Sweep-mine or shaft-mine on command. Farm a row, harvest, replant, repeat. Fish at any body of water deep enough to cast into.

**Roles, not macros.** `/squire role MINER | FARMER | FISHER` switches posture — the AI adjusts tactics, targets, and daily rhythm.

**Home chest aware.** Designate a home chest; the squire deposits hauls, retrieves tools, and manages its own inventory between tasks.

**Mounted patrols.** Put it on a horse with `/squire mount`. It rides a patrol line, engages threats with the weapon you gave it, returns when clear.

**The Squire's Manual.** A full Patchouli guidebook in-game. Every command, every role, every quirk — written in character, read at your pace.

**Levels up.** Your squire gains experience from work and combat. It keeps the gear you give it and remembers what it's done.

---

## Commands

```
/squire info                 status, level, gear
/squire name "Jorah"         give them a name
/squire role MINER | FARMER | FISHER

/squire mine                 sweep pattern from current spot
/squire shaft                descend-and-branch mining
/squire farm                 patrol-row harvest/replant
/squire fish                 auto-find nearest water

/squire mount / dismount     horseback patrols
/squire homechest            designate deposit target

/squire mode | appearance | place | list | clear | kill
```

---

## Requirements

- Minecraft **1.21.1**
- **NeoForge** (tested on the current ATM10 build)
- Client and server: both required

Plays standalone, in small packs, or alongside **All the Mods 10**. No known hard conflicts at v3.0.1.

---

## FAQ

**Does it work with ATM10?**  
Yes. Squire is a NeoForge 1.21.1 mod and has been tested alongside All the Mods 10.

**Can it teleport to me?**  
No — by design. The squire walks the world the way you do. If you outpace it, it catches up on its own terms.

**Does it duplicate items?**  
No. Every item it deposits was mined, harvested, or fished by its own hand. No spawned loot.

**Can I have more than one?**  
One active squire per player is the current design intent. Retinues are tracked on the roadmap.

**How do I report bugs?**  
File an issue on the GitHub source repo. Include your modpack list, NeoForge version, and a reproduction seed if you have one.

---

## Changelog — v3.0.1 (2026-04-16)

See `docs/audits/2026-04-16.md` in the source repo for the full audit + fix log.

---

## Source & license

Source on GitHub: https://github.com/sjviklabs/squire-mod-v2  
License: MIT

*Paid in coin or in kind.*
```

---

## Platform-specific notes

### Modrinth
- The screenshot captioned **HERO · Squire patrolling at dusk** goes in the **Gallery** as the first image and is used as the page banner.
- Set the **icon** to the 512×512 PNG of the Seal mark on Ink background (see `LOGO_EXPORTS.md`).
- Source URL, Issue tracker URL, Wiki URL (point at the Patchouli book docs) all go in the Links section.

### CurseForge
- Icon is rendered at 80×80 in most views — use the 256×256 PNG; the Seal mark must still be legible at that size (it is; size test passed in the design file).
- CurseForge's description supports BBCode fallbacks but Markdown above works. If anything breaks, wrap `<pre>` around the command block.
- First screenshot in "Additional images" becomes the hero on CurseForge's listing row.
