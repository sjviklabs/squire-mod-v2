package com.sjviklabs.squire.item;

import com.sjviklabs.squire.SquireRegistry;
import com.sjviklabs.squire.entity.SquireDataAttachment;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Squire's Crest — the single item that controls squire presence.
 *
 * Right-click (air or block):
 *   - If squire is alive in any dimension → RECALL: instant discard()
 *   - If no squire → SUMMON: spawn at crosshair (up to 4 blocks), SOUL particles
 *
 * One-per-player enforcement: summon checks ALL server levels.
 * Recall: always works regardless of combat state — player has full control.
 */
public class SquireCrestItem extends Item {

    public SquireCrestItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Client side: pass — let server handle state
        if (level.isClientSide) return InteractionResultHolder.pass(stack);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.pass(stack);

        ServerLevel serverLevel = (ServerLevel) level;

        // Check for existing squire across all dimensions
        SquireEntity existing = findPlayerSquire(serverLevel, serverPlayer);
        if (existing != null) {
            return recallSquire(serverPlayer, stack, existing);
        }

        // No existing squire — summon
        return summonSquire(serverLevel, serverPlayer, stack);
    }

    // ================================================================
    // Recall — instant discard, write state to attachment
    // ================================================================

    private InteractionResultHolder<ItemStack> recallSquire(
            ServerPlayer player, ItemStack stack, SquireEntity squire) {

        // Persist current state to player attachment before discarding
        SquireDataAttachment.SquireData data = player.getData(SquireRegistry.SQUIRE_DATA.get());
        player.setData(SquireRegistry.SQUIRE_DATA.get(),
                data.withXP(squire.getTotalXP(), squire.getLevel())
                    .withSquireUUID(Optional.empty()));

        squire.discard();

        player.sendSystemMessage(Component.literal("Your squire returns to the Crest."));
        return InteractionResultHolder.success(stack);
    }

    // ================================================================
    // Summon — spawn at crosshair, restore identity from attachment
    // ================================================================

    private InteractionResultHolder<ItemStack> summonSquire(
            ServerLevel level, ServerPlayer player, ItemStack stack) {

        // One-per-player enforcement across all dimensions
        if (countPlayerSquires(level, player) >= 1) {
            player.sendSystemMessage(Component.literal("Your squire is already with you."));
            return InteractionResultHolder.fail(stack);
        }

        // Spawn at crosshair (up to 4 blocks away, 1 tick delta — picks block surface)
        Vec3 spawnPos = player.pick(4.0, 1.0f, false).getLocation();

        SquireEntity squire = new SquireEntity(SquireRegistry.SQUIRE.get(), level);
        squire.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        squire.setOwnerId(player.getUUID());

        // Restore identity from player attachment
        SquireDataAttachment.SquireData data = player.getData(SquireRegistry.SQUIRE_DATA.get());
        squire.setCustomName(Component.literal(data.customName()));
        squire.setLevel(data.level());
        squire.setTotalXP(data.totalXP());
        squire.setSlimModel(data.slimModel());

        level.addFreshEntity(squire);

        // SOUL particles for materialization feel
        level.sendParticles(ParticleTypes.SOUL,
                spawnPos.x, spawnPos.y + 1.0, spawnPos.z,
                30, 0.3, 0.5, 0.3, 0.05);

        player.sendSystemMessage(Component.literal(data.customName() + " answers your call."));
        return InteractionResultHolder.success(stack);
    }

    // ================================================================
    // Lookup helpers — search ALL server levels for one-per-player enforcement
    // ================================================================

    @Nullable
    private SquireEntity findPlayerSquire(ServerLevel currentLevel, ServerPlayer player) {
        for (ServerLevel serverLevel : currentLevel.getServer().getAllLevels()) {
            for (SquireEntity squire : serverLevel.getEntities(
                    SquireRegistry.SQUIRE.get(), e -> e.isAlive() && e.isOwnedBy(player))) {
                return squire;
            }
        }
        return null;
    }

    private int countPlayerSquires(ServerLevel currentLevel, ServerPlayer player) {
        int count = 0;
        for (ServerLevel serverLevel : currentLevel.getServer().getAllLevels()) {
            count += serverLevel.getEntities(SquireRegistry.SQUIRE.get(),
                    e -> e.isAlive() && e.isOwnedBy(player)).size();
        }
        return count;
    }
}
