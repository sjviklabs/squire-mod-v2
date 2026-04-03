package com.sjviklabs.squire.network;

import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.entity.SquireEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server payload dispatching a radial menu command to a squire.
 *
 * Wire format: Two VarInts — command ID, then squire entity network ID.
 * Uses StreamCodec.composite() — zero raw FriendlyByteBuf reads/writes (ARC-08).
 *
 * Phase 5 handles all four radial menu commands. Additional commands (patrol, mount,
 * store, fetch) are declared as constants and deferred to their respective phases.
 *
 * Command IDs (Phase 5 radial wedge order):
 *   0 - Follow, 1 - Guard, 2 - Stay, 3 - Inventory
 *
 * DEDICATED SERVER SAFETY: No net.minecraft.client.* imports in this file.
 * The handle() method runs server-side only. openMenu is a server API.
 */
public record SquireCommandPayload(int commandId, int squireEntityId) implements CustomPacketPayload {

    // ---- Phase 5 command constants (radial menu wedge order) ----
    public static final int CMD_FOLLOW    = 0;
    public static final int CMD_GUARD     = 1;
    public static final int CMD_STAY      = 2;
    public static final int CMD_INVENTORY = 3;

    // ---- Future phase constants (deferred — handlers are no-ops until their phase) ----
    public static final int CMD_PATROL    = 4;
    public static final int CMD_STORE     = 5;
    public static final int CMD_FETCH     = 6;
    public static final int CMD_MOUNT     = 7;

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

    // ---- Server-side handler — NO net.minecraft.client.* imports permitted ----

    /**
     * Handles the command server-side. Validates sender is the squire's owner,
     * then routes by commandId.
     *
     * Phase 5 implements all four Phase 5 radial commands.
     * Future-phase constants (CMD_PATROL etc.) are no-ops until their phase.
     */
    public static void handle(SquireCommandPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;

            ServerLevel level = serverPlayer.serverLevel();
            if (!(level.getEntity(payload.squireEntityId()) instanceof SquireEntity squire)) return;
            if (!squire.isOwnedBy(serverPlayer)) return;

            switch (payload.commandId()) {
                case CMD_FOLLOW -> squire.setSquireMode(SquireEntity.MODE_FOLLOW);
                case CMD_GUARD  -> squire.setSquireMode(SquireEntity.MODE_GUARD);
                case CMD_STAY   -> squire.setSquireMode(SquireEntity.MODE_SIT);
                case CMD_INVENTORY -> {
                    String displayName = (squire.hasCustomName() && squire.getCustomName() != null)
                            ? squire.getCustomName().getString()
                            : "Squire";
                    serverPlayer.openMenu(
                        new SimpleMenuProvider(
                            (id, inv, p) -> new com.sjviklabs.squire.inventory.SquireMenu(id, inv, squire),
                            Component.literal(displayName)
                        ),
                        buf -> buf.writeInt(squire.getId())
                    );
                }
                default -> { /* future-phase commands: no-op until their phase is implemented */ }
            }
        });
    }
}
