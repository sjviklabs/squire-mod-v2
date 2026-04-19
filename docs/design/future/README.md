# Future Assets — Awaiting Code Integration

Design deliverables that require code changes before they can be used in-game.

## squire_inventory.png (+ 4x preview)

A heraldic-panel GUI background matching the brand system (Ink header with Seal
corner mark, Vellum body, Brass hairlines, 6 equipment slots + 5×4 inventory grid).

**Current state:** The in-game GUI is drawn programmatically in
`src/main/java/com/sjviklabs/squire/client/SquireScreen.java` using `g.fill()` calls.
The 2026-04-16 color swap matches this PNG in palette but not in layout/decoration.

**To integrate:** Rewrite `SquireScreen.renderBg()` to load this texture and draw
it via `g.blit()`, then align slot positions in `SquireMenu` to match the PNG's
grid. Approximately 1-2 hours of work. Should be batched with the item icon
redesign for a single coordinated visual release (tentative v3.1.0).
