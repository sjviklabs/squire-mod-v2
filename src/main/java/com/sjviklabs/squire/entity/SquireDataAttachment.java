package com.sjviklabs.squire.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.Optional;
import java.util.UUID;

/**
 * Player-attached data that persists squire identity across death, crest loss,
 * server restarts, and dimension changes. The squire's soul lives here.
 *
 * IMPORTANT: Registration lives in SquireRegistry.ATTACHMENT_TYPES — NOT here.
 * This class only provides the SquireData record and its CODEC.
 */
public class SquireDataAttachment {

    private SquireDataAttachment() {}

    /**
     * Immutable record holding all persistent squire identity data.
     * Mutate via builder methods (returns new record, never mutates in place).
     */
    public record SquireData(
            int totalXP,
            int level,
            String customName,
            boolean slimModel,
            Optional<UUID> squireUUID,
            Optional<CompoundTag> inventoryNBT
    ) {
        public static final Codec<SquireData> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.INT.fieldOf("totalXP").forGetter(SquireData::totalXP),
                        Codec.INT.fieldOf("level").forGetter(SquireData::level),
                        Codec.STRING.fieldOf("customName").forGetter(SquireData::customName),
                        Codec.BOOL.fieldOf("slimModel").forGetter(SquireData::slimModel),
                        Codec.STRING.optionalFieldOf("squireUUID").xmap(
                                opt -> opt.map(UUID::fromString),
                                opt -> opt.map(UUID::toString)
                        ).forGetter(SquireData::squireUUID),
                        CompoundTag.CODEC.optionalFieldOf("inventoryNBT")
                                .forGetter(SquireData::inventoryNBT)
                ).apply(instance, SquireData::new));

        public static SquireData empty() {
            return new SquireData(0, 0, "Squire", false, Optional.empty(), Optional.empty());
        }

        /** Return new record with updated XP and level. */
        public SquireData withXP(int totalXP, int level) {
            return new SquireData(totalXP, level, this.customName, this.slimModel, this.squireUUID, this.inventoryNBT);
        }

        /** Return new record with updated custom name. */
        public SquireData withName(String name) {
            return new SquireData(this.totalXP, this.level, name, this.slimModel, this.squireUUID, this.inventoryNBT);
        }

        /** Return new record with updated slim model flag. */
        public SquireData withAppearance(boolean slim) {
            return new SquireData(this.totalXP, this.level, this.customName, slim, this.squireUUID, this.inventoryNBT);
        }

        /** Return new record with the given squire UUID (or cleared if empty). */
        public SquireData withSquireUUID(Optional<UUID> uuid) {
            return new SquireData(this.totalXP, this.level, this.customName, this.slimModel, uuid, this.inventoryNBT);
        }

        /** Return new record with squire UUID cleared. */
        public SquireData clearSquireUUID() {
            return new SquireData(this.totalXP, this.level, this.customName, this.slimModel, Optional.empty(), this.inventoryNBT);
        }

        /** Return new record with inventory NBT saved (for recall persistence). */
        public SquireData withInventory(CompoundTag nbt) {
            return new SquireData(this.totalXP, this.level, this.customName, this.slimModel, this.squireUUID, Optional.ofNullable(nbt));
        }

        /** Return new record with inventory cleared (after re-summon). */
        public SquireData clearInventory() {
            return new SquireData(this.totalXP, this.level, this.customName, this.slimModel, this.squireUUID, Optional.empty());
        }

        /** True if a squire UUID is currently stored (i.e., a squire is summoned). */
        public boolean hasSquire() {
            return this.squireUUID.isPresent();
        }
    }

    /**
     * Factory for use in SquireRegistry.ATTACHMENT_TYPES.register().
     * Supplies a serialized AttachmentType backed by SquireData.CODEC.
     */
    public static AttachmentType<SquireData> buildAttachmentType() {
        return AttachmentType.builder(SquireData::empty)
                .serialize(SquireData.CODEC)
                .build();
    }
}
