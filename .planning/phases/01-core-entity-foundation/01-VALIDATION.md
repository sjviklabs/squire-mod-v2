---
phase: 1
slug: core-entity-foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-02
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property               | Value                                               |
| ---------------------- | --------------------------------------------------- |
| **Framework**          | JUnit 5 + NeoForge GameTest                         |
| **Config file**        | build.gradle (test task config)                     |
| **Quick run command**  | `./gradlew test`                                    |
| **Full suite command** | `./gradlew test runGameTestServer`                  |
| **Estimated runtime**  | ~30 seconds (unit), ~60 seconds (GameTest)          |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test`
- **After every plan wave:** Run `./gradlew test runGameTestServer`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 90 seconds

---

## Per-Task Verification Map

| Task ID   | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status     |
| --------- | ---- | ---- | ----------- | --------- | ----------------- | ----------- | ---------- |
| 01-01-01  | 01   | 1    | ARC-05      | build     | `./gradlew build` | ❌ W0       | ⬜ pending |
| 01-02-01  | 02   | 1    | ENT-05      | unit      | `./gradlew test`  | ❌ W0       | ⬜ pending |
| 01-02-02  | 02   | 1    | ENT-03/04   | unit      | `./gradlew test`  | ❌ W0       | ⬜ pending |
| 01-02-03  | 02   | 1    | ENT-06      | unit      | `./gradlew test`  | ❌ W0       | ⬜ pending |
| 01-03-01  | 03   | 2    | INV-01/02   | unit      | `./gradlew test`  | ❌ W0       | ⬜ pending |
| 01-03-02  | 03   | 2    | INV-06      | gametest  | `./gradlew runGameTestServer` | ❌ W0 | ⬜ pending |
| 01-04-01  | 04   | 2    | ARC-06      | unit      | `./gradlew test`  | ❌ W0       | ⬜ pending |
| 01-04-02  | 04   | 2    | ARC-07      | gametest  | `./gradlew runGameTestServer` | ❌ W0 | ⬜ pending |
| 01-05-01  | 05   | 1    | TST-01/02   | unit+gametest | `./gradlew test runGameTestServer` | ❌ W0 | ⬜ pending |

_Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky_

---

## Wave 0 Requirements

- [ ] `src/test/java/com/sjviklabs/squire/` — test package directory
- [ ] `src/test/java/com/sjviklabs/squire/entity/SquireEntityTest.java` — NBT, attachment, one-per-player
- [ ] `src/test/java/com/sjviklabs/squire/inventory/SquireItemHandlerTest.java` — slot capacity, tier gating
- [ ] `src/test/java/com/sjviklabs/squire/config/SquireConfigTest.java` — validator correctness
- [ ] `src/test/java/com/sjviklabs/squire/test/SquireGameTests.java` — GameTest scaffolding
- [ ] JUnit 5 dependency in build.gradle

---

## Manual-Only Verifications

| Behavior   | Requirement | Why Manual | Test Instructions |
| ---------- | ----------- | ---------- | ----------------- |
| Crest summon particle animation | ENT-01 | Visual effect — cannot assert in headless | Summon squire, verify particle burst appears at crosshair location |
| Inventory GUI opens correctly | INV-06 | GUI rendering requires client | Right-click squire, verify 9-slot grid + equipment slots visible |
| Config generates on first run | ARC-06 | Requires clean server start | Delete config dir, start server, check squire-common.toml has 50+ entries |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
