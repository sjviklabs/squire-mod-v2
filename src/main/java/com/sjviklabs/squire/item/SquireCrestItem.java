package com.sjviklabs.squire.item;

import com.sjviklabs.squire.SquireRegistry;
import com.sjviklabs.squire.entity.SquireDataAttachment;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
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

    // ================================================================
    // Area selection — right-click blocks to set corners
    // ================================================================

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        // Sneak+click block = clear selection
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS; // fall through to use() for recall
        }

        // Only on server
        if (level.isClientSide) return InteractionResult.SUCCESS;

        ItemStack stack = ctx.getItemInHand();
        BlockPos clicked = ctx.getClickedPos();

        // If squire exists, this is area selection mode
        if (player instanceof ServerPlayer sp) {
            SquireEntity existing = findPlayerSquire((ServerLevel) level, sp);
            if (existing != null) {
                CompoundTag tag = getOrCreateCrestTag(stack);

                if (!tag.contains("Corner1X")) {
                    // Set corner 1
                    tag.putInt("Corner1X", clicked.getX());
                    tag.putInt("Corner1Y", clicked.getY());
                    tag.putInt("Corner1Z", clicked.getZ());
                    setCrestTag(stack, tag);
                    player.sendSystemMessage(Component.literal(
                            "Corner 1 set at " + clicked.toShortString() + ". Right-click another block for corner 2."));
                    return InteractionResult.SUCCESS;
                } else {
                    // Set corner 2 — area defined
                    tag.putInt("Corner2X", clicked.getX());
                    tag.putInt("Corner2Y", clicked.getY());
                    tag.putInt("Corner2Z", clicked.getZ());
                    setCrestTag(stack, tag);

                    BlockPos c1 = new BlockPos(tag.getInt("Corner1X"), tag.getInt("Corner1Y"), tag.getInt("Corner1Z"));
                    int sizeX = Math.abs(clicked.getX() - c1.getX()) + 1;
                    int sizeY = Math.abs(clicked.getY() - c1.getY()) + 1;
                    int sizeZ = Math.abs(clicked.getZ() - c1.getZ()) + 1;
                    player.sendSystemMessage(Component.literal(
                            "Area selected: " + sizeX + "x" + sizeY + "x" + sizeZ +
                            " (" + c1.toShortString() + " to " + clicked.toShortString() + ")" +
                            ". Use /squire mine or /squire farm to assign work."));
                    return InteractionResult.SUCCESS;
                }
            }
        }

        return InteractionResult.PASS; // no squire — fall through to summon
    }

    // ================================================================
    // Area data helpers
    // ================================================================

    private static CompoundTag getOrCreateCrestTag(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.copyTag();
    }

    private static void setCrestTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Get the selected area corners from a Crest item. Returns null if not fully selected. */
    @Nullable
    public static BlockPos[] getSelectedArea(ItemStack stack) {
        CompoundTag tag = getOrCreateCrestTag(stack);
        if (!tag.contains("Corner1X") || !tag.contains("Corner2X")) return null;
        return new BlockPos[]{
                new BlockPos(tag.getInt("Corner1X"), tag.getInt("Corner1Y"), tag.getInt("Corner1Z")),
                new BlockPos(tag.getInt("Corner2X"), tag.getInt("Corner2Y"), tag.getInt("Corner2Z"))
        };
    }

    /** Clear the area selection from a Crest item. */
    public static void clearSelection(ItemStack stack) {
        CompoundTag tag = getOrCreateCrestTag(stack);
        tag.remove("Corner1X"); tag.remove("Corner1Y"); tag.remove("Corner1Z");
        tag.remove("Corner2X"); tag.remove("Corner2Y"); tag.remove("Corner2Z");
        setCrestTag(stack, tag);
    }

    // ================================================================
    // Summon / Recall — right-click air
    // ================================================================

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
            // Recall is command-only (/squire recall) — Crest only summons
            player.sendSystemMessage(Component.literal("Your squire is already active. Use /squire recall to dismiss."));
            return InteractionResultHolder.pass(stack);
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
