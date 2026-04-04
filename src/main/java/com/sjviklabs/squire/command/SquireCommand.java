package com.sjviklabs.squire.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Brigadier command tree for the /squire command family.
 *
 * Phase 5 scope: info, mine (stub), place (stub), mode, name, list, kill.
 * Additional commands (patrol, farm, fish, store, fetch, mount) are deferred to
 * their respective feature phases. Registering stub entries here satisfies GUI-02
 * and prevents "unknown command" errors if players try them early.
 *
 * DEDICATED SERVER SAFETY: No net.minecraft.client.* imports permitted.
 * This class is loaded on both physical client and dedicated server.
 *
 * Registration: @EventBusSubscriber on the NeoForge GAME bus via RegisterCommandsEvent.
 */
@EventBusSubscriber(modid = SquireMod.MODID)
public final class SquireCommand {

    private SquireCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("squire")

            // /squire info — show squire name, level, XP, HP, mode (no permission required)
            .then(Commands.literal("info")
                .executes(ctx -> showInfo(ctx.getSource()))
            )

            // /squire mine <pos> — mine a block at position
            .then(Commands.literal("mine")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(ctx -> mineBlock(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos")))
                )
            )

            // /squire place <pos> — place a block at position (uses held block from squire inventory)
            .then(Commands.literal("place")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(ctx -> placeBlock(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos")))
                )
            )

            // /squire farm — start farming nearby crops
            .then(Commands.literal("farm")
                .executes(ctx -> startFarm(ctx.getSource()))
            )

            // /squire fish — start fishing at nearest water
            .then(Commands.literal("fish")
                .executes(ctx -> startFish(ctx.getSource()))
            )

            // /squire mount — mount nearest horse
            .then(Commands.literal("mount")
                .executes(ctx -> mountHorse(ctx.getSource()))
            )

            // /squire dismount — dismount current horse
            .then(Commands.literal("dismount")
                .executes(ctx -> dismountHorse(ctx.getSource()))
            )

            // /squire mode <follow|stay|guard> — change squire mode (no permission required)
            .then(Commands.literal("mode")
                .then(Commands.argument("mode", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        builder.suggest("follow");
                        builder.suggest("stay");
                        builder.suggest("guard");
                        return builder.buildFuture();
                    })
                    .executes(ctx -> setMode(ctx.getSource(),
                        StringArgumentType.getString(ctx, "mode")))
                )
            )

            // /squire appearance male|female — switch skin variant
            .then(Commands.literal("appearance")
                .then(Commands.argument("variant", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        builder.suggest("male");
                        builder.suggest("female");
                        return builder.buildFuture();
                    })
                    .executes(ctx -> setAppearance(ctx.getSource(),
                        StringArgumentType.getString(ctx, "variant")))
                )
            )

            // /squire name [text] — set custom name, or clear if no argument
            .then(Commands.literal("name")
                .executes(ctx -> clearName(ctx.getSource()))
                .then(Commands.argument("text", StringArgumentType.greedyString())
                    .executes(ctx -> setName(ctx.getSource(),
                        StringArgumentType.getString(ctx, "text")))
                )
            )

            // /squire list — list all active squires (op level 2)
            .then(Commands.literal("list")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> listSquires(ctx.getSource()))
            )

            // /squire kill <player> — discard target player's squire (op level 2)
            .then(Commands.literal("kill")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> killSquire(ctx.getSource(),
                        EntityArgument.getPlayer(ctx, "player")))
                )
            )
        );
    }

    // ================================================================
    // Helper — find the squire owned by a player
    // ================================================================

    /**
     * Returns the first SquireEntity owned by the given player in their current level,
     * or null if none is found.
     */
    @Nullable
    private static SquireEntity findOwnedSquire(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        AABB searchBox = player.getBoundingBox().inflate(128.0);
        List<SquireEntity> candidates = level.getEntitiesOfClass(
            SquireEntity.class, searchBox, e -> e.isOwnedBy(player));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * Maps a squire mode byte to a display name.
     */
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

        source.sendSuccess(() -> Component.literal(String.format(
            "%s — Lv.%d | XP: %d | HP: %.0f/%.0f | Mode: %s",
            name,
            squire.getLevel(),
            squire.getTotalXP(),
            squire.getHealth(),
            squire.getMaxHealth(),
            modeName(squire.getSquireMode())
        )), false);

        return 1;
    }

    // ================================================================
    // /squire mine, place, farm, fish, mount, dismount
    // ================================================================

    private static int mineBlock(CommandSourceStack source, net.minecraft.core.BlockPos pos) {
        if (!source.isPlayer()) return 0;
        SquireEntity squire = findOwnedSquire((ServerPlayer) source.getEntity());
        if (squire == null) { source.sendFailure(Component.literal("You have no active squire.")); return 0; }
        squire.getSquireBrain().getMiningHandler().setTarget(pos);
        source.sendSuccess(() -> Component.literal("Squire mining at " + pos.toShortString()), false);
        return 1;
    }

    private static int placeBlock(CommandSourceStack source, net.minecraft.core.BlockPos pos) {
        if (!source.isPlayer()) return 0;
        SquireEntity squire = findOwnedSquire((ServerPlayer) source.getEntity());
        if (squire == null) { source.sendFailure(Component.literal("You have no active squire.")); return 0; }
        // Place uses whatever block item is in squire's mainhand
        net.minecraft.world.item.ItemStack mainhand = squire.getMainHandItem();
        if (mainhand.isEmpty() || !(mainhand.getItem() instanceof net.minecraft.world.item.BlockItem blockItem)) {
            source.sendFailure(Component.literal("Squire has no block to place."));
            return 0;
        }
        squire.getSquireBrain().getPlacingHandler().setTarget(pos, blockItem.getBlock().asItem());
        source.sendSuccess(() -> Component.literal("Squire placing at " + pos.toShortString()), false);
        return 1;
    }

    private static int startFarm(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        SquireEntity squire = findOwnedSquire((ServerPlayer) source.getEntity());
        if (squire == null) { source.sendFailure(Component.literal("You have no active squire.")); return 0; }
        squire.getSquireBrain().getFarmingHandler().tick(com.sjviklabs.squire.brain.SquireAIState.FARM_SCAN);
        source.sendSuccess(() -> Component.literal("Squire is farming."), false);
        return 1;
    }

    private static int startFish(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        SquireEntity squire = findOwnedSquire((ServerPlayer) source.getEntity());
        if (squire == null) { source.sendFailure(Component.literal("You have no active squire.")); return 0; }
        squire.getSquireBrain().getFishingHandler().startFishing();
        source.sendSuccess(() -> Component.literal("Squire is fishing."), false);
        return 1;
    }

    private static int mountHorse(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        SquireEntity squire = findOwnedSquire((ServerPlayer) source.getEntity());
        if (squire == null) { source.sendFailure(Component.literal("You have no active squire.")); return 0; }
        squire.getSquireBrain().getMachine().forceState(com.sjviklabs.squire.brain.SquireAIState.MOUNTING);
        source.sendSuccess(() -> Component.literal("Squire is mounting a horse."), false);
        return 1;
    }

    private static int dismountHorse(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        SquireEntity squire = findOwnedSquire((ServerPlayer) source.getEntity());
        if (squire == null) { source.sendFailure(Component.literal("You have no active squire.")); return 0; }
        squire.getSquireBrain().getMountHandler().orderDismount(squire);
        squire.getSquireBrain().getMachine().forceState(com.sjviklabs.squire.brain.SquireAIState.IDLE);
        source.sendSuccess(() -> Component.literal("Squire dismounted."), false);
        return 1;
    }

    // ================================================================
    // /squire mode <follow|stay|guard>
    // ================================================================

    private static int setMode(CommandSourceStack source, String modeStr) {
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

        byte newMode;
        String label;
        switch (modeStr.toLowerCase()) {
            case "follow" -> { newMode = SquireEntity.MODE_FOLLOW; label = "Follow"; }
            case "stay"   -> { newMode = SquireEntity.MODE_SIT;    label = "Stay";   }
            case "guard"  -> { newMode = SquireEntity.MODE_GUARD;  label = "Guard";  }
            default -> {
                source.sendFailure(Component.literal(
                    "Unknown mode '" + modeStr + "'. Valid modes: follow, stay, guard"));
                return 0;
            }
        }

        squire.setSquireMode(newMode);
        source.sendSuccess(() -> Component.literal("Squire mode set to: " + label), false);
        return 1;
    }

    // ================================================================
    // /squire name [text]
    // ================================================================

    private static int setAppearance(CommandSourceStack source, String variant) {
        if (!source.isPlayer()) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (player == null) return 0;
        SquireEntity squire = findOwnedSquire(player);
        if (squire == null) { source.sendFailure(Component.literal("You have no active squire.")); return 0; }

        boolean slim = variant.equalsIgnoreCase("female");
        squire.setSlimModel(slim);
        source.sendSuccess(() -> Component.literal("Squire appearance set to " + (slim ? "female" : "male") + "."), false);
        return 1;
    }

    private static int setName(CommandSourceStack source, String nameText) {
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

        squire.setCustomName(Component.literal(nameText));
        squire.setCustomNameVisible(true);
        source.sendSuccess(() -> Component.literal("Squire renamed to: " + nameText), false);
        return 1;
    }

    private static int clearName(CommandSourceStack source) {
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

        squire.setCustomName(null);
        squire.setCustomNameVisible(false);
        source.sendSuccess(() -> Component.literal("Squire name cleared."), false);
        return 1;
    }

    // ================================================================
    // /squire list (op level 2)
    // ================================================================

    private static int listSquires(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<SquireEntity> squires = level.getEntitiesOfClass(
            SquireEntity.class, new AABB(
                level.getWorldBorder().getMinX(), level.getMinBuildHeight(),
                level.getWorldBorder().getMinZ(), level.getWorldBorder().getMaxX(),
                level.getMaxBuildHeight(), level.getWorldBorder().getMaxZ()
            ), e -> true);

        if (squires.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active squires in this dimension."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
            "Active squires in this dimension: " + squires.size()), false);

        for (SquireEntity squire : squires) {
            String sqName = (squire.hasCustomName() && squire.getCustomName() != null)
                ? squire.getCustomName().getString()
                : "Squire";
            int x = squire.blockPosition().getX();
            int y = squire.blockPosition().getY();
            int z = squire.blockPosition().getZ();
            source.sendSuccess(() -> Component.literal(String.format(
                "  %s (Lv.%d) at [%d, %d, %d] — Mode: %s",
                sqName, squire.getLevel(), x, y, z, modeName(squire.getSquireMode())
            )), false);
        }

        return squires.size();
    }

    // ================================================================
    // /squire kill <player> (op level 2)
    // ================================================================

    private static int killSquire(CommandSourceStack source, ServerPlayer target) {
        SquireEntity squire = findOwnedSquire(target);
        if (squire == null) {
            source.sendFailure(Component.literal(
                target.getGameProfile().getName() + " has no active squire."));
            return 0;
        }

        String sqName = (squire.hasCustomName() && squire.getCustomName() != null)
            ? squire.getCustomName().getString()
            : "Squire";
        squire.discard();

        source.sendSuccess(() -> Component.literal(
            "Discarded " + sqName + " (owned by " + target.getGameProfile().getName() + ")."
        ), true);
        return 1;
    }
}
