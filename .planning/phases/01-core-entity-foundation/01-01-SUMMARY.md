---
phase: 01-core-entity-foundation
plan: "01"
subsystem: infra
tags: [neoforge, moddevgradle, geckolib, gradle, java, mod-scaffold]

# Dependency graph
requires: []
provides:
  - ModDevGradle 2.0.141 build with NeoForge 21.1.221 pinned
  - SquireMod @Mod entry point (MODID constant, IEventBus constructor)
  - SquireRegistry single registration hub (4 DeferredRegister instances, explicit load order)
  - All Phase 1-8 soft-deps declared in build.gradle (Geckolib, Curios, Jade)
affects:
  - 01-02 (entity type registration goes into SquireRegistry)
  - 01-03 (inventory registration goes into SquireRegistry)
  - 01-04 (config wiring goes into SquireMod constructor)
  - All future phases (compile against this foundation)

# Tech tracking
tech-stack:
  added:
    - ModDevGradle 2.0.141
    - NeoForge 21.1.221
    - Parchment Mappings 2024.11.17-1.21.1
    - Geckolib 4.8.3 (geckolib-neoforge-1.21.1)
    - Curios 9.5.1+1.21.1 (compileOnly api + localRuntime)
    - Jade 15.10.4+neoforge (compileOnly)
    - JUnit 5.10.2
  patterns:
    - SquireRegistry as single DeferredRegister hub — no other class may call DeferredRegister.create()
    - Registration order: ENTITY_TYPES -> ITEMS -> ATTACHMENT_TYPES -> MENU_TYPES (load-order-sensitive)

key-files:
  created:
    - build.gradle
    - settings.gradle
    - gradle.properties
    - gradlew / gradlew.bat
    - gradle/wrapper/gradle-wrapper.properties
    - src/main/resources/META-INF/neoforge.mods.toml
    - src/main/resources/pack.mcmeta
    - src/main/java/com/sjviklabs/squire/SquireMod.java
    - src/main/java/com/sjviklabs/squire/SquireRegistry.java
    - .gitignore
  modified: []

key-decisions:
  - "Modrinth Maven URL is api.modrinth.com/maven — maven.modrinth.com DNS does not resolve in this environment"
  - "MineColonies compileOnly dep commented out — ldtteam Jfrog repo has no 1.21.1 artifact; coordinates need Phase 8 verification"
  - "Added .gitignore bootstrapped from v0.5.0 — prevents build/ and .gradle/ from being tracked"

patterns-established:
  - "Rule: Only SquireRegistry.java may contain DeferredRegister.create() calls"
  - "Rule: Registration order in SquireRegistry.register() is ENTITY_TYPES, ITEMS, ATTACHMENT_TYPES, MENU_TYPES"
  - "Rule: SquireMod constructor calls SquireRegistry.register(modEventBus) — no direct DeferredRegister calls in SquireMod"

requirements-completed: [ARC-05]

# Metrics
duration: 24min
completed: 2026-04-03
---

# Phase 1 Plan 01: Mod Scaffold Summary

**ModDevGradle 2.0.141 build targeting NeoForge 21.1.221, with SquireRegistry as the single DeferredRegister hub replacing v0.5.0's scattered ModItems/ModBlocks/ModEntities pattern**

## Performance

- **Duration:** 24 min
- **Started:** 2026-04-03T06:26:56Z
- **Completed:** 2026-04-03T06:51:06Z
- **Tasks:** 2
- **Files modified:** 10

## Accomplishments

- ModDevGradle build resolves NeoForge 21.1.221, Geckolib 4.8.3, Curios, and Jade — `./gradlew build` exits 0
- SquireMod entry point establishes MODID = "squire" and delegates all registration to SquireRegistry
- SquireRegistry owns all 4 DeferredRegister instances (ENTITY_TYPES, ITEMS, ATTACHMENT_TYPES, MENU_TYPES) with documented load-order rationale

## Task Commits

Each task was committed atomically:

1. **Task 1: Build configuration** - `deccc3c` (chore)
2. **Task 2: SquireMod entry point and SquireRegistry** - `f8fe7de` (feat)

**Plan metadata:** (to be created after SUMMARY)

## Files Created/Modified

- `build.gradle` - ModDevGradle 2.0.141 build; NeoForge 21.1.221; all Phase 1-8 soft-deps; corrected Modrinth Maven URL
- `settings.gradle` - Project name and plugin repositories
- `gradle.properties` - JVM args and mod_version
- `gradlew` / `gradlew.bat` - Gradle wrapper scripts
- `gradle/wrapper/gradle-wrapper.properties` - Gradle 8.14.2 distribution
- `src/main/resources/META-INF/neoforge.mods.toml` - Mod metadata with NeoForge + Geckolib dependencies
- `src/main/resources/pack.mcmeta` - Resource pack format 34
- `src/main/java/com/sjviklabs/squire/SquireMod.java` - @Mod entry point
- `src/main/java/com/sjviklabs/squire/SquireRegistry.java` - Single DeferredRegister hub
- `.gitignore` - Excludes build/, .gradle/, run/, and OS artifacts

## Decisions Made

- Used `api.modrinth.com/maven` instead of `maven.modrinth.com` — the latter does not resolve DNS in this lab environment (AdGuard filter)
- Commented out MineColonies `compileOnly` dep — ldtteam Jfrog only publishes 1.12.2-era artifacts; 1.21.1 coordinates need research before Phase 8
- Bootstrapped Gradle wrapper from v0.5.0 project (same Gradle 8.14.2 version, avoids download)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Modrinth Maven URL causing DNS resolution failure**

- **Found during:** Task 2 (compileJava after adding Java source)
- **Issue:** Plan spec used `maven.modrinth.com` for Jade dependency — this hostname does not resolve (AdGuard DNS filter in lab). Gradle reported "No such host is known" and BUILD FAILED.
- **Fix:** Updated repositories block to use `https://api.modrinth.com/maven` (official Modrinth Maven endpoint — verified HTTP 200 response). Jade 15.10.4+neoforge now resolves.
- **Files modified:** `build.gradle`
- **Verification:** `./gradlew dependencies --configuration compileClasspath` shows Jade resolved, BUILD SUCCESSFUL
- **Committed in:** `f8fe7de` (Task 2 commit)

**2. [Rule 1 - Bug] Commented out MineColonies dep with unresolvable coordinates**

- **Found during:** Task 2 (compileJava after adding Java source)
- **Issue:** `com.minecolonies:minecolonies:1.1.1231-1.21.1:api` not available at ldtteam Jfrog. Maven metadata shows repo only has 1.0.x and 1.12.2-era versions. The coordinates in the plan spec are incorrect or the artifact moved.
- **Fix:** Commented out the dependency with a TODO noting Phase 8 must verify correct coordinates before implementation. The dep is not imported in source, so commenting it out has zero functional impact now.
- **Files modified:** `build.gradle`
- **Verification:** `./gradlew build` exits 0 without MineColonies dep
- **Committed in:** `f8fe7de` (Task 2 commit)

**3. [Rule 2 - Missing Critical] Added .gitignore**

- **Found during:** Task 1 (after Gradle wrapper bootstrap)
- **Issue:** No .gitignore present; `build/` and `.gradle/` directories appeared as untracked after first Gradle run. Without a gitignore, future commits risk accidentally committing 100MB+ of Gradle cache artifacts.
- **Fix:** Created `.gitignore` bootstrapped from v0.5.0 project — excludes build/, .gradle/, run/, IDE files, OS artifacts.
- **Files modified:** `.gitignore` (created)
- **Verification:** `git status --short` shows build/ and .gradle/ absent from untracked list
- **Committed in:** `deccc3c` (Task 1 commit)

---

**Total deviations:** 3 auto-fixed (2 Rule 1 bugs, 1 Rule 2 missing critical)
**Impact on plan:** All fixes essential for a working build. No scope creep. MineColonies coordinates need pre-Phase 8 research — documented as TODO in build.gradle.

## Issues Encountered

- Modrinth changed their Maven hostname — `maven.modrinth.com` is the domain in most community docs but `api.modrinth.com/maven` is the actual working endpoint. This affects any project that follows the old documentation.
- MineColonies 1.21.1 API jar distribution path is undocumented. The ldtteam Jfrog Artifactory only hosts old versions. Will need to find the correct Maven coordinates before Phase 8.

## User Setup Required

None - no external service configuration required. The build is self-contained via Gradle wrapper.

## Next Phase Readiness

- Foundation is compilable: `./gradlew build` exits 0
- SquireRegistry has 4 empty DeferredRegister instances ready to receive DeferredHolder registrations
- Plan 01-02 (SquireEntity) can register entity type, attachment type via ENTITY_TYPES and ATTACHMENT_TYPES
- Plan 01-03 (SquireItemHandler) can register item via ITEMS and menu type via MENU_TYPES
- MineColonies coordinates must be resolved before Plan 08 starts — add to Phase 8 research checklist

---

## Self-Check: PASSED

- FOUND: build.gradle
- FOUND: SquireMod.java
- FOUND: SquireRegistry.java
- FOUND: neoforge.mods.toml
- FOUND: SUMMARY.md
- FOUND commit: deccc3c (Task 1)
- FOUND commit: f8fe7de (Task 2)

_Phase: 01-core-entity-foundation_
_Completed: 2026-04-03_
