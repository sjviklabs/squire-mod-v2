package com.sjviklabs.squire.brain;

import com.mojang.logging.LogUtils;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Lightweight activity logger for squire debugging.
 * Writes to SLF4J (shows in logs/latest.log) and keeps a ring buffer
 * of recent entries for the /squire log command.
 *
 * One instance per squire entity. Controlled by SquireConfig.activityLogging.
 */
public class SquireActivityLog {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_ENTRIES = 100;

    private final SquireEntity squire;
    private final Deque<String> entries = new ArrayDeque<>();
    private final long startTick;

    public SquireActivityLog(SquireEntity squire) {
        this.squire = squire;
        this.startTick = squire.level().getGameTime();
    }

    /**
     * Log an activity. Goes to both SLF4J and the ring buffer.
     *
     * @param category short tag (STATE, COMBAT, MINE, PLACE, EAT, ITEM, LEVEL, etc.)
     * @param message  details
     */
    public void log(String category, String message) {
        if (!SquireConfig.activityLogging.get()) return;

        long tick = squire.level().getGameTime();
        long elapsed = tick - startTick;
        String ownerName = resolveOwnerName();
        String entry = String.format("[t%d] [%s] %s", elapsed, category, message);

        // SLF4J — lands in logs/latest.log, grepable by [SquireAI]
        LOGGER.info("[SquireAI:{}] {}", ownerName, entry);

        // Ring buffer
        if (entries.size() >= MAX_ENTRIES) {
            entries.pollFirst();
        }
        entries.addLast(entry);
    }

    /**
     * Returns the most recent N entries (newest last).
     */
    public List<String> getRecent(int count) {
        int size = entries.size();
        int skip = Math.max(0, size - count);
        return entries.stream().skip(skip).toList();
    }

    /** Total entries in buffer. */
    public int size() {
        return entries.size();
    }

    private String resolveOwnerName() {
        if (squire.getOwnerUUID() == null) return "unowned";
        var owner = squire.level().getPlayerByUUID(squire.getOwnerUUID());
        if (owner != null) return owner.getName().getString();
        return squire.getOwnerUUID().toString().substring(0, 8);
    }
}
