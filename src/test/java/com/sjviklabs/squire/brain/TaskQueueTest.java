package com.sjviklabs.squire.brain;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TaskQueue: FIFO order, NBT round-trip, max-length enforcement,
 * empty poll, and clear.
 *
 * No NeoForge/Minecraft bootstrap needed — TaskQueue and SquireTask use only
 * CompoundTag / ListTag from the DFU/NBT layer, which is pure Java.
 */
class TaskQueueTest {

    private TaskQueue queue;

    @BeforeEach
    void setUp() {
        queue = new TaskQueue();
    }

    /** enqueue "mine", "farm", "fish" — poll() returns in insertion order. */
    @Test
    void test_fifoOrder() {
        CompoundTag empty = new CompoundTag();
        queue.enqueue(new SquireTask("mine", empty));
        queue.enqueue(new SquireTask("farm", empty));
        queue.enqueue(new SquireTask("fish", empty));

        assertEquals("mine", queue.poll().commandName());
        assertEquals("farm", queue.poll().commandName());
        assertEquals("fish", queue.poll().commandName());
        assertNull(queue.poll()); // queue exhausted
    }

    /**
     * Enqueue 2 tasks with distinct params, save(), reconstruct from load(),
     * poll() must return identical commandName and params in same order.
     */
    @Test
    void test_nbtRoundTrip() {
        CompoundTag params1 = new CompoundTag();
        params1.putInt("x", 10);
        params1.putInt("y", 64);
        params1.putInt("z", -5);

        CompoundTag params2 = new CompoundTag();
        params2.putString("item", "minecraft:wheat_seeds");

        queue.enqueue(new SquireTask("mine", params1));
        queue.enqueue(new SquireTask("farm", params2));

        CompoundTag saved = queue.save();

        TaskQueue restored = new TaskQueue();
        restored.load(saved);

        SquireTask first = restored.poll();
        assertNotNull(first);
        assertEquals("mine", first.commandName());
        assertEquals(10, first.params().getInt("x"));
        assertEquals(64, first.params().getInt("y"));
        assertEquals(-5, first.params().getInt("z"));

        SquireTask second = restored.poll();
        assertNotNull(second);
        assertEquals("farm", second.commandName());
        assertEquals("minecraft:wheat_seeds", second.params().getString("item"));
    }

    /**
     * Enqueue MAX_QUEUE_LENGTH + 1 tasks. The last enqueue must return false
     * and size must remain at MAX_QUEUE_LENGTH.
     */
    @Test
    void test_maxLength_enforcement() {
        CompoundTag empty = new CompoundTag();
        for (int i = 0; i < TaskQueue.MAX_QUEUE_LENGTH; i++) {
            assertTrue(queue.enqueue(new SquireTask("mine", empty)),
                    "enqueue should return true for slot " + i);
        }
        assertFalse(queue.enqueue(new SquireTask("mine", empty)),
                "enqueue past MAX_QUEUE_LENGTH must return false");
        assertEquals(TaskQueue.MAX_QUEUE_LENGTH, queue.size());
    }

    /** poll() on an empty queue returns null without throwing. */
    @Test
    void test_emptyPoll_returnsNull() {
        assertNull(queue.poll());
    }

    /** enqueue 3, clear(), isEmpty() must be true. */
    @Test
    void test_clear() {
        CompoundTag empty = new CompoundTag();
        queue.enqueue(new SquireTask("mine", empty));
        queue.enqueue(new SquireTask("farm", empty));
        queue.enqueue(new SquireTask("fish", empty));
        assertFalse(queue.isEmpty());

        queue.clear();
        assertTrue(queue.isEmpty());
    }
}
