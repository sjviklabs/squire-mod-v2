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
 * Client-to-server payload for querying or toggling squire mode by entity ID.
 *
 * Wire format: Single VarInt — the squire's entity network ID.
 * Uses StreamCodec.composite() — zero raw FriendlyByteBuf reads/writes (ARC-08).
 *
 * Phase 2: mode is set authoritatively via SquireCommandPayload CMD_STAY/CMD_FOLLOW.
 * This payload exists for future client-to-server mode query use cases (Phase 5 UI).
 * The server-side handler is a no-op in Phase 2.
 */
public record SquireModePayload(int squireEntityId) implements CustomPacketPayload {

    // ---- CustomPacketPayload identity ----
    public static final CustomPacketPayload.Type<SquireModePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "squire_mode"));

    // ---- StreamCodec (ARC-08: no raw FriendlyByteBuf) ----
    public static final StreamCodec<ByteBuf, SquireModePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SquireModePayload::squireEntityId,
                    SquireModePayload::new
            );

    @Override
    public CustomPacketPayload.Type<SquireModePayload> type() {
        return TYPE;
    }

    // ---- Server-side handler ----

    /**
     * Server-side handler. Validates sender is the squire's owner.
     * No action in Phase 2 — mode changes are driven by SquireCommandPayload.
     * Reserved for client-initiated mode queries in Phase 5.
     */
    public static void handle(SquireModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;

            Entity entity = serverPlayer.serverLevel().getEntity(payload.squireEntityId());
            if (!(entity instanceof SquireEntity squire)) return;
            if (!squire.isOwnedBy(serverPlayer)) return;

            // Phase 2: no action — mode set via SquireCommandPayload.
            // Phase 5: implement mode query/sync response here.
        });
    }
}
