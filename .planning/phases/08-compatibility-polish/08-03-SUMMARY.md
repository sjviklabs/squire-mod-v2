---
phase: 08-compatibility-polish
plan: "03"
subsystem: compat
tags: [neoforge, mods.toml, minecolonies, jade, curios, optional-deps]

requires:
  - phase: 08-02
    provides: JadeCompat @WailaPlugin, CuriosCompat slot accessor, builtin datapack JSON
  - phase: 08-01
    provides: MineColoniesCompat package-scan detection, CuriosCompat skeleton

provides:
  - neoforge.mods.toml optional dep declarations for jade, curios, minecolonies
  - SquireMod startup logging for MineColoniesCompat.isActive() and CuriosCompat.isActive()
  - Final wiring of all three compat classes into the mod entry point
  - Full test suite green with build clean — project complete

affects:
  - All future phases (this is the final plan of the entire project)

tech-stack:
  added: []
  patterns:
    - "Optional dep declaration in neoforge.mods.toml: type = optional with side = CLIENT for Jade, BOTH for server-safe mods"
    - "Compat startup logging pattern: guard isActive() check at mod init, log presence, skip auto-discovered plugins"

key-files:
  created: []
  modified:
    - src/main/resources/META-INF/neoforge.mods.toml
    - src/main/java/com/sjviklabs/squire/SquireMod.java

key-decisions:
  - "Jade declared as CLIENT-side optional dep only — Jade is a client-only HUD mod; declaring BOTH would be incorrect and misleading"
  - "ATM10 smoke test deferred to user testing — human checkpoint auto-approved per phase 08-03 plan instructions; compat classes are structurally correct and unit-tested"
  - "No MineColoniesCompat conditional registration needed — isActive() is self-guarding; startup logging is sufficient wiring for Phase 8 scope"

patterns-established:
  - "Optional dep ordering = NONE: NeoForge does not guarantee load order for optional deps; all compat code guards with isActive() at call sites rather than relying on ordering"

requirements-completed: [CMP-01, CMP-02, CMP-03, INV-05]

duration: 5min
completed: 2026-04-04
---

# Phase 8 Plan 03: Compatibility Polish — Wiring and Final Gate Summary

**Optional dep declarations in neoforge.mods.toml (jade CLIENT, curios/minecolonies BOTH) and SquireMod startup logging for all three compat providers, completing Phase 8 and the entire squire-mod-v2 project**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-04-04T03:39:00Z
- **Completed:** 2026-04-04T03:42:25Z
- **Tasks:** 1 of 2 (Task 2 is human verification — deferred to user ATM10 testing)
- **Files modified:** 2

## Accomplishments

- Appended three optional dependency blocks to neoforge.mods.toml (jade as CLIENT, curios and minecolonies as BOTH)
- Added MineColoniesCompat and CuriosCompat imports to SquireMod.java with startup logging in the constructor
- Confirmed zero `net.minecraft.client` references in compat/ — dedicated server safe
- `./gradlew test` BUILD SUCCESSFUL — all tests pass
- `./gradlew build` BUILD SUCCESSFUL — mod JAR produced cleanly

## Task Commits

1. **Task 1: Wire optional deps in mods.toml and SquireMod startup logging** - `1f23ab6` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `src/main/resources/META-INF/neoforge.mods.toml` — Added jade (CLIENT opt), curios (BOTH opt), minecolonies (BOTH opt) dependency blocks
- `src/main/java/com/sjviklabs/squire/SquireMod.java` — Added CuriosCompat/MineColoniesCompat imports and startup logging in constructor

## Decisions Made

- Jade declared as CLIENT-side optional dep. It is a client-only HUD mod — declaring it as BOTH would be wrong. The @WailaPlugin annotation handles server-side exclusion automatically.
- ATM10 smoke test (Task 2) deferred to user testing per the `<important>` block in the plan. The compat classes are structurally correct, unit-tested (MineColoniesCompatTest 5 tests + CuriosCompatTest 3 tests from prior plans), and the TOML declarations are in place. Human verification of Jade HUD / Curios slots in-game is the remaining step.
- No conditional MineColoniesCompat registration added to SquireMod — isActive() is self-guarding at every call site. CombatHandler friendly-fire integration is Phase 4 work already wired; this plan only confirms compat is reachable from the entry point.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None. Both files were clean edits. Build and tests passed on first run.

## User Setup Required

**ATM10 smoke test is deferred to user testing.** To verify the compat integrations in-game:

1. Build the JAR: `./gradlew build` (already done — `build/libs/squire-mod-v2-2.0.0.jar`)
2. Drop into ATM10 mods folder, start the server/client
3. Confirm no ClassNotFoundException on startup without optional mods
4. With Jade 15.x present: look at a squire — HUD should show name/tier/HP
5. With Curios 9.5.x present: open squire inventory — accessory slots should appear
6. With MineColonies present: "[Squire] MineColonies detected" should appear in log

## Next Phase Readiness

This is the FINAL plan of the entire squire-mod-v2 project. Phase 8 Plan 03 completes Phase 8 (Compatibility Polish) and closes the project.

All 8 phases are complete:
- Phase 1: Core Entity Foundation
- Phase 2: Brain FSM Follow
- Phase 3: Rendering
- Phase 4: Combat and Progression
- Phase 5: UI and Controls
- Phase 6: Work Behaviors
- Phase 7: Patrol and Mounting
- Phase 8: Compatibility Polish

---

## Self-Check

**Files exist:**
- `src/main/resources/META-INF/neoforge.mods.toml` — FOUND (modified)
- `src/main/java/com/sjviklabs/squire/SquireMod.java` — FOUND (modified)

**Commits exist:**
- `1f23ab6` — FOUND

## Self-Check: PASSED

---

_Phase: 08-compatibility-polish_
_Completed: 2026-04-04_
