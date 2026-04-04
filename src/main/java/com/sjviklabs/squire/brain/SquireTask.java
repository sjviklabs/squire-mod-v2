package com.sjviklabs.squire.brain;

import net.minecraft.nbt.CompoundTag;

/**
 * A single queued work task: a command name and optional parameters stored as NBT.
 *
 * commandName values: "mine", "mine_area", "place", "farm", "fish",
 *                     "chest_deposit", "chest_withdraw"
 *
 * params stores BlockPos, Item, area corners etc. encoded as NBT sub-tags.
 * An empty CompoundTag is valid — used when a command needs no parameters.
 */
public record SquireTask(String commandName, CompoundTag params) {

    /** Serialize this task to a CompoundTag for NBT storage. */
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("command", commandName);
        tag.put("params", params.copy());
        return tag;
    }

    /** Deserialize a task from a CompoundTag produced by toNBT(). */
    public static SquireTask fromNBT(CompoundTag tag) {
        String cmd = tag.getString("command");
        CompoundTag p = tag.contains("params") ? tag.getCompound("params") : new CompoundTag();
        return new SquireTask(cmd, p);
    }
}
