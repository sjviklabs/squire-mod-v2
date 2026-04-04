package com.sjviklabs.squire.brain;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * FIFO queue of SquireTask entries for sequential work execution.
 *
 * Backed by ArrayDeque (not LinkedList — per RESEARCH.md architecture section).
 * Lives in brain/ package, not util/.
 *
 * Persistence: serialized via SquireEntity.addAdditionalSaveData() / readAdditionalSaveData()
 * NOT in SquireBrain constructor — per pitfall 7 (NBT desync on disconnect).
 *
 * Usage:
 *   queue.enqueue(task) — returns false if at MAX_QUEUE_LENGTH
 *   queue.poll()        — returns null if empty
 *   queue.save()        — CompoundTag for NBT write
 *   queue.load(tag)     — clears and reconstructs from NBT
 */
public class TaskQueue {

    /** Hard cap: prevents unbounded queuing and runaway server-side work. */
    public static final int MAX_QUEUE_LENGTH = 16;

    private final Deque<SquireTask> tasks = new ArrayDeque<>();

    // -------------------------------------------------------------------------
    // Queue operations
    // -------------------------------------------------------------------------

    /**
     * Add a task to the back of the queue.
     *
     * @return true if enqueued; false if queue is at MAX_QUEUE_LENGTH.
     */
    public boolean enqueue(SquireTask task) {
        if (tasks.size() >= MAX_QUEUE_LENGTH) {
            return false;
        }
        tasks.addLast(task);
        return true;
    }

    /**
     * Remove and return the next task from the front of the queue.
     *
     * @return the task, or null if the queue is empty (never throws).
     */
    @Nullable
    public SquireTask poll() {
        return tasks.pollFirst();
    }

    /** @return true if the queue has no tasks. */
    public boolean isEmpty() {
        return tasks.isEmpty();
    }

    /** @return current number of queued tasks. */
    public int size() {
        return tasks.size();
    }

    /** Remove all queued tasks. */
    public void clear() {
        tasks.clear();
    }

    // -------------------------------------------------------------------------
    // NBT persistence
    // -------------------------------------------------------------------------

    /**
     * Serialize the queue to a CompoundTag containing a ListTag of task entries.
     * Call from SquireEntity.addAdditionalSaveData().
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (SquireTask task : tasks) {
            list.add(task.toNBT());
        }
        tag.put("tasks", list);
        return tag;
    }

    /**
     * Reconstruct the queue from a CompoundTag produced by save().
     * Clears existing tasks first. Respects MAX_QUEUE_LENGTH when loading.
     * Call from SquireEntity.readAdditionalSaveData() after a contains("taskQueue") guard.
     */
    public void load(CompoundTag tag) {
        tasks.clear();
        if (!tag.contains("tasks")) return;

        ListTag list = tag.getList("tasks", Tag.TAG_COMPOUND);
        int limit = Math.min(list.size(), MAX_QUEUE_LENGTH);
        for (int i = 0; i < limit; i++) {
            tasks.addLast(SquireTask.fromNBT(list.getCompound(i)));
        }
    }
}
