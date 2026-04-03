package com.sjviklabs.squire.network;

import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.entity.SquireEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server payload dispatching a radial menu command to a squire.
 *
 * Wire format: Two VarInts — command ID, then squire entity network ID.
 * Uses StreamCodec.composite() — zero raw FriendlyByteBuf reads/writes (ARC-08).
 *
 * Phase 2 handles CMD_STAY and CMD_FOLLOW only. All other command constants are
 * declared here for completeness; their handler branches are no-ops until their
 * respective phases are implemented.
 *
 * Command IDs (match wedge order in SquireRadialScreen — future phase):
 *   0 - Follow, 1 - Guard, 2 - Patrol, 3 - Stay,
 *   4 - Store,  5 - Fetch,  6 - Mount,  7 - Inventory, 8 - Come
 */
public record SquireCommandPayload(int commandId, int squireEntityId) implements CustomPacketPayload {

    // ---- Command constants ----
    public static final int CMD_FOLLOW    = 0;
    public static final int CMD_GUARD     = 1;
    public static final int CMD_PATROL    = 2;
    public static final int CMD_STAY      = 3;
    public static final int CMD_STORE     = 4;
    public static final int CMD_FETCH     = 5;
    public static final int CMD_MOUNT     = 6;
    public static final int CMD_INVENTORY = 7;
    public static final int CMD_COME      = 8;

    // ---- CustomPacketPayload identity ----
    public static final CustomPacketPayload.Type<SquireCommandPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "squire_command"));

    // ---- StreamCodec (ARC-08: no raw FriendlyByteBuf) ----
    public static final StreamCodec<ByteBuf, SquireCommandPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SquireCommandPayload::commandId,
                    ByteBufCodecs.VAR_INT, SquireCommandPayload::squireEntityId,
                    SquireCommandPayload::new
            );

    @Override
    public CustomPacketPayload.Type<SquireCommandPayload> type() {
        return TYPE;
    }

    // ---- Server-side handler ----

    /**
     * Handles the command server-side. Validates sender is the squire's owner,
     * then routes by commandId.
     *
     * Phase 2 implements CMD_STAY and CMD_FOLLOW only. Other cases are no-ops
     * reserved for later phases.
     */
    public static void handle(SquireCommandPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;

            Entity entity = serverPlayer.serverLevel().getEntity(payload.squireEntityId());
            if (!(entity instanceof SquireEntity squire)) return;
            if (!squire.isOwnedBy(serverPlayer)) return;

            switch (payload.commandId()) {
                case CMD_STAY   -> squire.setSquireMode(SquireEntity.MODE_SIT);
                case CMD_FOLLOW -> squire.setSquireMode(SquireEntity.MODE_FOLLOW);
                default         -> { /* other commands handled in later phases */ }
            }
        });
    }
}
