package com.sjviklabs.squire.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Brigadier command tree for the /squire command family.
 *
 * v4.0.0 Phase 1 scope: INFO, RECALL, MODE, NAME, LIST, KILL, GODMODE only.
 * These don't need the AI layer (they read/write entity fields directly or
 * have admin-only side effects).
 *
 * Commands that DID exist in v3.x but were deleted when the v3.x brain went
 * away — they come back in the phase that re-introduces their backing AI:
 *   - /squire mine, /squire shaft, /squire place  ← Phase 4 (MinerAI)
 *   - /squire farm                                 ← Phase 5 (FarmerAI)
 *   - /squire fish                                 ← Phase 5 (FisherAI)
 *   - /squire patrol                               ← Phase 6 (PatrolAI)
 *   - /squire mount, /squire dismount              ← Phase 6 (movement AI)
 *   - /squire role, /squire homechest              ← Phase 4 (work role wiring)
 *   - /squire queue                                ← Phase 5 (intent queue)
 *   - /squire store, /squire fetch                 ← Phase 5 (ChestAI)
 *
 * DEDICATED SERVER SAFETY: No net.minecraft.client.* imports permitted.
 * This class is loaded on both physical client and dedicated server.
 */
@EventBusSubscriber(modid = SquireMod.MODID)
public final class SquireCommand {

    private SquireCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("squire")

            // /squire info
            .then(Commands.literal("info")
                .executes(ctx -> showInfo(ctx.getSource())))

            // /squire recall
            .then(Commands.literal("recall")
                .executes(ctx -> recallSquire(ctx.getSource())))

            // /squire mode <follow|stay|guard>
            .then(Commands.literal("mode")
                .then(Commands.literal("follow").executes(ctx -> setMode(ctx.getSource(), "follow")))
                .then(Commands.literal("stay").executes(ctx -> setMode(ctx.getSource(), "stay")))
                .then(Commands.literal("guard").executes(ctx -> setMode(ctx.getSource(), "guard"))))

            // /squire name <text> / clear
            .then(Commands.literal("name")
                .then(Commands.argument("text", StringArgumentType.greedyString())
                    .executes(ctx -> setName(ctx.getSource(), StringArgumentType.getString(ctx, "text"))))
                .then(Commands.literal("clear")
                    .executes(ctx -> clearName(ctx.getSource()))))

            // /squire list  (admin: see all squires server-wide)
            .then(Commands.literal("list")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> listSquires(ctx.getSource())))

            // /squire kill <player>  (admin)
            .then(Commands.literal("kill")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> killSquire(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))

            // /squire godmode  (admin: owner's squire invulnerability toggle)
            .then(Commands.literal("godmode")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> toggleGodMode(ctx.getSource())))

            // /squire mine   — mine the crest-selected area
            .then(Commands.literal("mine").executes(ctx -> mineArea(ctx.getSource())))

            // /squire chop   — fell every tree in the crest-selected area
            .then(Commands.literal("chop").executes(ctx -> chopArea(ctx.getSource())))

            // /squire farm / stop — farm the crest-selected area until told to stop
            .then(Commands.literal("farm")
                .executes(ctx -> farmArea(ctx.getSource()))
                .then(Commands.literal("stop").executes(ctx -> farmStop(ctx.getSource()))))

            // /squire fish / stop — fish the nearest water until told to stop
            .then(Commands.literal("fish")
                .executes(ctx -> fishStart(ctx.getSource()))
                .then(Commands.literal("stop").executes(ctx -> fishStop(ctx.getSource()))))

            // /squire place <pos> — place the squire's mainhand block at pos
            .then(Commands.literal("place")
                .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                    .executes(ctx -> placeBlock(ctx.getSource(),
                            net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(ctx, "pos")))))

            // /squire patrol / stop — walk the crest-selected area's perimeter
            .then(Commands.literal("patrol")
                .executes(ctx -> patrolStart(ctx.getSource()))
                .then(Commands.literal("stop").executes(ctx -> patrolStop(ctx.getSource()))))

            // /squire store <pos> — deposit backpack contents into the chest at pos
            .then(Commands.literal("store")
                .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                    .executes(ctx -> storeAt(ctx.getSource(),
                            net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(ctx, "pos")))))
        );
    }

    // ================================================================
    // Helpers
    // ================================================================

    @Nullable
    private static SquireEntity findOwnedSquire(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        AABB searchBox = player.getBoundingBox().inflate(128.0);
        List<SquireEntity> candidates = level.getEntitiesOfClass(
            SquireEntity.class, searchBox, e -> e.isOwnedBy(player));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    @Nullable
    private static SquireEntity requireSquire(CommandSourceStack source) {
        if (!source.isPlayer()) return null;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (player == null) return null;
        SquireEntity squire = findOwnedSquire(player);
        if (squire == null) {
            source.sendFailure(Component.literal("You have no active squire."));
        }
        return squire;
    }

    private static String modeName(byte mode) {
        return switch (mode) {
            case SquireEntity.MODE_FOLLOW -> "Follow";
            case SquireEntity.MODE_GUARD  -> "Guard";
            case SquireEntity.MODE_SIT    -> "Stay";
            default -> "Unknown";
        };
    }

    // ================================================================
    // /squire info
    // ================================================================

    private static int showInfo(CommandSourceStack source) {
        if (!source.isPlayer()) {
            source.sendFailure(Component.literal("This command requires a player."));
            return 0;
        }
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (player == null) {
            source.sendFailure(Component.literal("This command requires a player."));
            return 0;
        }

        SquireEntity squire = findOwnedSquire(player);
        if (squire == null) {
            source.sendFailure(Component.literal("You have no active squire."));
            return 0;
        }

        String name = (squire.hasCustomName() && squire.getCustomName() != null)
            ? squire.getCustomName().getString()
            : "Squire";

        String modVersion = net.neoforged.fml.ModList.get()
            .getModContainerById(SquireMod.MODID)
            .map(c -> c.getModInfo().getVersion().toString())
            .orElse("unknown");
        source.sendSuccess(() -> Component.literal("=== Squire Mod v" + modVersion + " ==="), false);

        String tier = squire.getTier().name().charAt(0) + squire.getTier().name().substring(1).toLowerCase();
        source.sendSuccess(() -> Component.literal(String.format(
            "%s — Lv.%d %s | XP: %d | HP: %.0f/%.0f | Mode: %s",
            name,
            squire.getLevel(),
            tier,
            squire.getTotalXP(),
            squire.getHealth(),
            squire.getMaxHealth(),
            modeName(squire.getSquireMode())
        )), false);

        source.sendSuccess(() -> Component.literal(
            "v4.0.0 Phase 1 — custom AI disabled (only FloatGoal + OpenDoorGoal active). " +
            "Combat/work commands return in later phases."), false);

        return 1;
    }

    // ================================================================
    // /squire recall — dismisses the squire (entity discard)
    // ================================================================

    private static int recallSquire(CommandSourceStack source) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
        squire.discard();
        source.sendSuccess(() -> Component.literal("Squire recalled."), false);
        return 1;
    }

    // ================================================================
    // /squire mode
    // ================================================================

    private static int setMode(CommandSourceStack source, String modeStr) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
        byte mode = switch (modeStr) {
            case "follow" -> SquireEntity.MODE_FOLLOW;
            case "stay", "sit" -> SquireEntity.MODE_SIT;
            case "guard" -> SquireEntity.MODE_GUARD;
            default -> -1;
        };
        if (mode < 0) {
            source.sendFailure(Component.literal("Unknown mode: " + modeStr));
            return 0;
        }
        squire.setSquireMode(mode);
        // setOrderedToSit is TamableAnimal-only — SquireEntity extends PathfinderMob.
        // The AI layer (Phase 2+) reads getSquireMode() to decide behavior; no entity
        // flag to flip here.
        source.sendSuccess(() -> Component.literal("Squire mode: " + modeName(mode)), false);
        return 1;
    }

    // ================================================================
    // /squire name / clear
    // ================================================================

    private static int setName(CommandSourceStack source, String nameText) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
        squire.setCustomName(Component.literal(nameText));
        squire.setCustomNameVisible(true);
        source.sendSuccess(() -> Component.literal("Squire named: " + nameText), false);
        return 1;
    }

    private static int clearName(CommandSourceStack source) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
        squire.setCustomName(null);
        squire.setCustomNameVisible(false);
        source.sendSuccess(() -> Component.literal("Squire name cleared."), false);
        return 1;
    }

    // ================================================================
    // /squire list (admin)
    // ================================================================

    private static int listSquires(CommandSourceStack source) {
        int total = 0;
        StringBuilder sb = new StringBuilder("--- Squires ---\n");
        for (ServerLevel level : source.getServer().getAllLevels()) {
            AABB whole = new AABB(-30_000_000, level.getMinBuildHeight(), -30_000_000,
                                   30_000_000, level.getMaxBuildHeight(),  30_000_000);
            List<SquireEntity> squires = level.getEntitiesOfClass(SquireEntity.class, whole);
            for (SquireEntity s : squires) {
                var owner = s.getOwner();
                String ownerName = owner != null ? owner.getName().getString() : "orphan";
                String name = s.hasCustomName() && s.getCustomName() != null
                        ? s.getCustomName().getString() : "Squire";
                sb.append(String.format("  %s (owner=%s, dim=%s, HP=%.0f)\n",
                        name, ownerName, level.dimension().location(), s.getHealth()));
                total++;
            }
        }
        sb.append("total: ").append(total);
        String out = sb.toString();
        source.sendSuccess(() -> Component.literal(out), false);
        return 1;
    }

    // ================================================================
    // /squire kill <player> (admin)
    // ================================================================

    private static int killSquire(CommandSourceStack source, ServerPlayer target) {
        SquireEntity squire = findOwnedSquire(target);
        if (squire == null) {
            source.sendFailure(Component.literal(target.getName().getString() + " has no active squire."));
            return 0;
        }
        squire.discard();
        source.sendSuccess(() -> Component.literal("Killed " + target.getName().getString() + "'s squire."), true);
        return 1;
    }

    // ================================================================
    // /squire godmode (admin)
    // ================================================================

    private static int toggleGodMode(CommandSourceStack source) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
        boolean now = !squire.isInvulnerable();
        squire.setInvulnerable(now);
        source.sendSuccess(() -> Component.literal("Squire godmode: " + (now ? "ON" : "OFF")), false);
        return 1;
    }

    // ================================================================
    // /squire mine, /squire chop — pass crest area to the relevant work AI
    // ================================================================

    private static int mineArea(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (player == null) return 0;
        SquireEntity squire = findOwnedSquire(player);
        if (squire == null) { source.sendFailure(Component.literal("You have no active squire.")); return 0; }

        net.minecraft.core.BlockPos[] area = readCrestArea(player);
        if (area == null) {
            source.sendFailure(Component.literal("Mark two corners with the Squire's Crest first."));
            return 0;
        }
        var ctl = squire.getAIController();
        if (ctl == null) {
            source.sendFailure(Component.literal("Squire not ready."));
            return 0;
        }
        ctl.getMinerAI().setArea(area[0], area[1]);
        int queued = ctl.getMinerAI().getQueueSize();
        com.sjviklabs.squire.item.SquireCrestItem.clearSelection(player.getMainHandItem());
        source.sendSuccess(() -> Component.literal("Squire mining area: " + queued + " blocks."), false);
        return 1;
    }

    private static int chopArea(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (player == null) return 0;
        SquireEntity squire = findOwnedSquire(player);
        if (squire == null) { source.sendFailure(Component.literal("You have no active squire.")); return 0; }

        net.minecraft.core.BlockPos[] area = readCrestArea(player);
        if (area == null) {
            source.sendFailure(Component.literal("Mark two corners with the Squire's Crest first."));
            return 0;
        }
        var ctl = squire.getAIController();
        if (ctl == null) {
            source.sendFailure(Component.literal("Squire not ready."));
            return 0;
        }
        ctl.getLumberjackAI().setArea(area[0], area[1]);
        int queued = ctl.getLumberjackAI().getQueueSize();
        com.sjviklabs.squire.item.SquireCrestItem.clearSelection(player.getMainHandItem());
        source.sendSuccess(() -> Component.literal("Squire chopping area: " + queued + " log blocks queued."), false);
        return 1;
    }

    /** Reads the crest area from the player's mainhand. Returns null if not held or not fully selected. */
    @Nullable
    private static net.minecraft.core.BlockPos[] readCrestArea(ServerPlayer player) {
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof com.sjviklabs.squire.item.SquireCrestItem)) return null;
        return com.sjviklabs.squire.item.SquireCrestItem.getSelectedArea(held);
    }

    // ================================================================
    // /squire farm / farm stop
    // ================================================================

    private static int farmArea(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (player == null) return 0;
        SquireEntity squire = findOwnedSquire(player);
        if (squire == null) { source.sendFailure(Component.literal("You have no active squire.")); return 0; }

        net.minecraft.core.BlockPos[] area = readCrestArea(player);
        if (area == null) {
            source.sendFailure(Component.literal("Mark two corners with the Squire's Crest first."));
            return 0;
        }
        var ctl = squire.getAIController();
        if (ctl == null) { source.sendFailure(Component.literal("Squire not ready.")); return 0; }
        ctl.getFarmerAI().setArea(area[0], area[1]);
        com.sjviklabs.squire.item.SquireCrestItem.clearSelection(player.getMainHandItem());
        source.sendSuccess(() -> Component.literal("Squire tending the area. Use /squire farm stop to recall."), false);
        return 1;
    }

    private static int farmStop(CommandSourceStack source) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
        var ctl = squire.getAIController();
        if (ctl != null) ctl.getFarmerAI().stop();
        source.sendSuccess(() -> Component.literal("Squire farming stopped."), false);
        return 1;
    }

    // ================================================================
    // /squire fish / fish stop
    // ================================================================

    private static int fishStart(CommandSourceStack source) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
        // Find nearest water block within 16b of the squire.
        net.minecraft.core.BlockPos waterPos = findNearestWater(squire, 16);
        if (waterPos == null) {
            source.sendFailure(Component.literal("No water within range of the squire."));
            return 0;
        }
        var ctl = squire.getAIController();
        if (ctl == null) { source.sendFailure(Component.literal("Squire not ready.")); return 0; }
        ctl.getFisherAI().start(waterPos);
        source.sendSuccess(() -> Component.literal("Squire fishing at " + waterPos.toShortString() + "."), false);
        return 1;
    }

    private static int fishStop(CommandSourceStack source) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
        var ctl = squire.getAIController();
        if (ctl != null) ctl.getFisherAI().stop();
        source.sendSuccess(() -> Component.literal("Squire fishing stopped."), false);
        return 1;
    }

    @Nullable
    private static net.minecraft.core.BlockPos findNearestWater(SquireEntity squire, int radius) {
        net.minecraft.core.BlockPos origin = squire.blockPosition();
        net.minecraft.core.BlockPos best = null;
        double bestDSq = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    net.minecraft.core.BlockPos p = origin.offset(dx, dy, dz);
                    if (!squire.level().getBlockState(p).getFluidState().is(net.minecraft.tags.FluidTags.WATER)) continue;
                    double d = origin.distSqr(p);
                    if (d < bestDSq) { bestDSq = d; best = p; }
                }
            }
        }
        return best;
    }

    // ================================================================
    // /squire patrol / patrol stop
    // ================================================================

    private static int patrolStart(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (player == null) return 0;
        SquireEntity squire = findOwnedSquire(player);
        if (squire == null) { source.sendFailure(Component.literal("You have no active squire.")); return 0; }

        net.minecraft.core.BlockPos[] area = readCrestArea(player);
        if (area == null) {
            source.sendFailure(Component.literal("Mark two corners with the Squire's Crest first."));
            return 0;
        }
        var ctl = squire.getAIController();
        if (ctl == null) { source.sendFailure(Component.literal("Squire not ready.")); return 0; }
        ctl.getPatrolAI().setArea(area[0], area[1]);
        com.sjviklabs.squire.item.SquireCrestItem.clearSelection(player.getMainHandItem());
        source.sendSuccess(() -> Component.literal("Squire patrolling the area. /squire patrol stop to recall."), false);
        return 1;
    }

    private static int patrolStop(CommandSourceStack source) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
        var ctl = squire.getAIController();
        if (ctl != null) ctl.getPatrolAI().stop();
        source.sendSuccess(() -> Component.literal("Squire patrol stopped."), false);
        return 1;
    }

    // ================================================================
    // /squire store <pos> — deposit backpack into chest at pos
    // ================================================================

    private static int storeAt(CommandSourceStack source, net.minecraft.core.BlockPos pos) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
        var ctl = squire.getAIController();
        if (ctl == null) { source.sendFailure(Component.literal("Squire not ready.")); return 0; }

        // Validate target is a container before dispatching — cheaper to fail fast here than
        // have the AI walk over and bail silently.
        if (squire.level() instanceof net.minecraft.server.level.ServerLevel lvl) {
            boolean ok = lvl.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null) != null
                    || lvl.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
            if (!ok) {
                source.sendFailure(Component.literal("No container at " + pos.toShortString() + "."));
                return 0;
            }
        }

        ctl.getChestAI().setTarget(pos);
        source.sendSuccess(() -> Component.literal("Squire depositing backpack into chest at " + pos.toShortString()), false);
        return 1;
    }

    // ================================================================
    // /squire place <pos>
    // ================================================================

    private static int placeBlock(CommandSourceStack source, net.minecraft.core.BlockPos pos) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
        net.minecraft.world.item.ItemStack main = squire.getMainHandItem();
        if (!(main.getItem() instanceof net.minecraft.world.item.BlockItem bi)) {
            source.sendFailure(Component.literal("Squire has no block in main hand."));
            return 0;
        }
        var ctl = squire.getAIController();
        if (ctl == null) { source.sendFailure(Component.literal("Squire not ready.")); return 0; }
        ctl.getPlacingAI().setTarget(pos, bi.getBlock());
        source.sendSuccess(() -> Component.literal("Squire placing " + bi.getBlock().getName().getString()
                + " at " + pos.toShortString()), false);
        return 1;
    }
}
