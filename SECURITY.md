# Security Policy

## Reporting a vulnerability

Please report security issues privately by emailing **security@stevenjvik.tech** with a reproducer and affected version. Expect acknowledgement within 72 hours.

Do **not** open a public issue for security bugs — that exposes other players to risk before a fix lands.

## Scope

This mod runs in the Minecraft client / dedicated server process. Reportable issues include:

- Crash or denial-of-service triggered by crafted NBT, packets, or world state.
- Exploits that let a player affect another player's data without permission (e.g. cross-squire commands).
- Any command dispatch that bypasses Minecraft's permission checks.

Mechanical balance, griefing mitigations, and modpack compatibility quirks are **not** security issues — open a regular issue for those.

## Supported versions

Only the latest minor release on `main` is supported. Older majors receive no backport fixes.
