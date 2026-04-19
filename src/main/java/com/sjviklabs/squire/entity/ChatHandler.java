package com.sjviklabs.squire.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Personality chat line scaffolding for RND-04.
 *
 * String pools are populated here. Actual triggering logic is added in Phase 6
 * (task queue + personality plan). This stub keeps the pools and the send mechanism
 * so Phase 6 can call ChatHandler.sendLine(squire, owner, ChatEvent.IDLE) directly.
 *
 * Lines vary by tier — higher tiers get more composed, confident speech.
 *
 * No client-side imports. Safe to load on dedicated server.
 * Tier order (SquireTier.ordinal()): SERVANT=0, APPRENTICE=1, SQUIRE=2, KNIGHT=3, CHAMPION=4
 */
public class ChatHandler {

    private static final Random RANDOM = new Random();

    public enum ChatEvent {
        IDLE, COMBAT_START, LEVEL_UP, NEW_TIER, CRAFTED_TOOL, NEED_MATERIALS
    }

    // String pools by event, then by tier index (SquireTier.ordinal()).
    private static final Map<ChatEvent, List<List<String>>> LINES = Map.of(
        ChatEvent.IDLE, List.of(
            // SERVANT
            List.of("Ready when you are.", "I'm here.", "Awaiting orders."),
            // APPRENTICE
            List.of("At your side.", "Just say the word.", "I've been practicing."),
            // SQUIRE
            List.of("Steady and ready.", "What's our next move?", "I've got your back."),
            // KNIGHT
            List.of("The path is clear.", "I see no threats nearby.", "Standing watch."),
            // CHAMPION
            List.of("Nothing can stop us.", "I am prepared for anything.", "Lead on.")
        ),
        ChatEvent.COMBAT_START, List.of(
            List.of("Watch out!", "Hostile!"),
            List.of("Engaging!", "I'll handle this!"),
            List.of("Weapons out!", "On your left!"),
            List.of("Threat detected.", "Covering your flank."),
            List.of("They picked the wrong fight.", "Neutralizing.")
        ),
        ChatEvent.LEVEL_UP, List.of(
            List.of("Getting better at this."),
            List.of("Learning fast."),
            List.of("Experience is everything."),
            List.of("I grow stronger still."),
            List.of("There are no limits.")
        ),
        ChatEvent.NEW_TIER, List.of(
            List.of("I've become an Apprentice."),
            List.of("Now a Squire. Feels right."),
            List.of("Squire rank earned."),
            List.of("Knight — this is what I was made for."),
            List.of("Champion. I won't forget who brought me here.")
        ),
        ChatEvent.CRAFTED_TOOL, List.of(
            List.of("Made myself a new %s."),
            List.of("Crafted a %s — back to work."),
            List.of("New %s ready."),
            List.of("Forged a replacement %s."),
            List.of("A fresh %s. Nothing stops us.")
        ),
        ChatEvent.NEED_MATERIALS, List.of(
            // v3.1.1 — squire no longer crafts. These lines ask the player for a tool.
            List.of("I need a %s, if you have one."),
            List.of("Could you hand me a %s?"),
            List.of("A %s would help me work."),
            List.of("I require a %s to continue."),
            List.of("A %s, my liege — and I'll be about it.")
        )
    );

    /**
     * Sends a random chat line to the squire's owner for the given event and tier.
     * No-op if owner is null, server-side check is false, or pool is empty.
     *
     * Phase 6 calls this from task queue completion, combat handler, and progression handler.
     * Do NOT call from client-side code.
     *
     * @param squire  The squire entity (server-side)
     * @param owner   The owning player (must be online and non-null)
     * @param event   The triggering chat event
     */
    public static void sendLine(SquireEntity squire, Player owner, ChatEvent event) {
        if (owner == null || squire.level().isClientSide()) return;

        // TODO Phase 6: replace with event bus publish when SquireBrainEventBus is wired
        SquireTier tier = SquireTier.fromLevel(squire.getLevel());
        List<List<String>> byTier = LINES.get(event);
        if (byTier == null) return;

        int tierIdx = Math.min(tier.ordinal(), byTier.size() - 1);
        List<String> pool = byTier.get(tierIdx);
        if (pool.isEmpty()) return;

        String line = pool.get(RANDOM.nextInt(pool.size()));
        String squireName = squire.hasCustomName() && squire.getCustomName() != null
            ? squire.getCustomName().getString()
            : "Your squire";

        owner.sendSystemMessage(Component.literal("[" + squireName + "] " + line));
    }

    /**
     * Sends a random chat line with String.format substitution for parameterized messages.
     * Used by CRAFTED_TOOL and NEED_MATERIALS events that include a tool name.
     *
     * @param squire  The squire entity (server-side)
     * @param owner   The owning player (must be online and non-null)
     * @param event   The triggering chat event
     * @param args    Format arguments applied to the selected line via String.format
     */
    public static void sendFormattedLine(SquireEntity squire, Player owner, ChatEvent event, Object... args) {
        if (owner == null || squire.level().isClientSide()) return;

        SquireTier tier = SquireTier.fromLevel(squire.getLevel());
        List<List<String>> byTier = LINES.get(event);
        if (byTier == null) return;

        int tierIdx = Math.min(tier.ordinal(), byTier.size() - 1);
        List<String> pool = byTier.get(tierIdx);
        if (pool.isEmpty()) return;

        String template = pool.get(RANDOM.nextInt(pool.size()));
        String line = String.format(template, args);
        String squireName = squire.hasCustomName() && squire.getCustomName() != null
            ? squire.getCustomName().getString()
            : "Your squire";

        owner.sendSystemMessage(Component.literal("[" + squireName + "] " + line));
    }

    // ── Cooldown map for askForTool (v3.1.1) ────────────────────────────────
    // Per-squire-per-tool last-ask gameTime, so the squire doesn't spam chat
    // every tick while waiting for the player to hand over a missing tool.
    private static final java.util.Map<String, Long> LAST_ASK = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long ASK_COOLDOWN_TICKS = 200L; // 10 seconds

    // ── Per-event cooldown (v3.1.5) ─────────────────────────────────────────
    // Defense-in-depth against state-loop bugs that re-publish events on a 5-tick
    // cycle. Key = squire UUID + ":" + event name. askForTool keeps its own map
    // above because its key includes the tool name.
    private static final java.util.Map<String, Long> LAST_EVENT = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Rate-limited variant of sendLine. Drops the call if this (squire, event) pair fired
     * within cooldownTicks of the current game time. Intended for events that might be
     * published on a tight loop — COMBAT_START especially.
     *
     * cooldownTicks == 0 means "no limit" (for one-shot events like LEVEL_UP / NEW_TIER).
     */
    public static void sendLineWithCooldown(SquireEntity squire, Player owner, ChatEvent event, long cooldownTicks) {
        if (owner == null || squire.level().isClientSide()) return;
        if (cooldownTicks > 0) {
            String key = squire.getUUID() + ":" + event.name();
            long now = squire.level().getGameTime();
            Long last = LAST_EVENT.get(key);
            if (last != null && now - last < cooldownTicks) return;
            LAST_EVENT.put(key, now);
        }
        sendLine(squire, owner, event);
    }

    /**
     * Ask the owner for a missing tool. Uses NEED_MATERIALS chat pool, rate-limited to
     * one ask per 10 seconds per (squire, tool) pair so it doesn't spam while waiting.
     *
     * v3.1.1 — replaces the old CraftingHandler auto-craft behavior. The squire no
     * longer makes its own tools; it requests them from the player.
     *
     * @param squire   The squire entity (server-side)
     * @param toolName Human-readable tool name, e.g. "pickaxe"
     */
    public static void askForTool(SquireEntity squire, String toolName) {
        if (squire.level().isClientSide()) return;
        Player owner = squire.getOwner();
        if (owner == null) return;

        String key = squire.getUUID() + ":" + toolName;
        long now = squire.level().getGameTime();
        Long last = LAST_ASK.get(key);
        if (last != null && now - last < ASK_COOLDOWN_TICKS) return;
        LAST_ASK.put(key, now);

        sendFormattedLine(squire, owner, ChatEvent.NEED_MATERIALS, toolName);
    }

    // Private constructor — static utility class
    private ChatHandler() {}
}
