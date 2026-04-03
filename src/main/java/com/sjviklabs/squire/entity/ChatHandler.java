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
        IDLE, COMBAT_START, LEVEL_UP, NEW_TIER
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

    // Private constructor — static utility class
    private ChatHandler() {}
}
