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

            // /squire mine — mine selected area (Crest corners) or single block
            .then(Commands.literal("mine")
                .executes(ctx -> mineArea(ctx.getSource()))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(ctx -> mineBlock(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos")))
                )
            )

            // /squire shaft [depth] | /squire shaft 2x2 [depth] — dig vertical shaft
            .then(Commands.literal("shaft")
                .executes(ctx -> startShaft(ctx.getSource(), 1, 16))
                .then(com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 128) != null ?
                    Commands.argument("depth", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> startShaft(ctx.getSource(), 1,
                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "depth")))
                    : Commands.literal("_"))
                .then(Commands.literal("2x2")
                    .executes(ctx -> startShaft(ctx.getSource(), 2, 16))
                    .then(Commands.argument("depth2", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> startShaft(ctx.getSource(), 2,
                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "depth2"))))
                )
            )

            // /squire place <pos> — place a block at position (uses held block from squire inventory)
            .then(Commands.literal("place")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(ctx -> placeBlock(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos")))
                )
            )

            // /squire farm — farm selected area (Crest corners) or nearby crops
            .then(Commands.literal("farm")
                .executes(ctx -> startFarm(ctx.getSource()))
            )

            // /squire clear — clear Crest area selection
            .then(Commands.literal("clear")
                .executes(ctx -> clearSelection(ctx.getSource()))
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

            // /squire store — deposit squire backpack into looked-at or specified chest
            .then(Commands.literal("store")
                .executes(ctx -> storeItems(ctx.getSource()))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(ctx -> storeItemsAt(ctx.getSource(),
                        BlockPosArgument.getBlockPos(ctx, "pos")))
                )
            )

            // /squire fetch — withdraw from looked-at or specified chest
            .then(Commands.literal("fetch")
                .executes(ctx -> fetchItems(ctx.getSource()))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(ctx -> fetchItemsAt(ctx.getSource(),
                        BlockPosArgument.getBlockPos(ctx, "pos"), null, 64))
                )
            )

            // /squire patrol — walk the perimeter of the crest-marked area
            .then(Commands.literal("patrol")
                .executes(ctx -> startPatrol(ctx.getSource()))
                .then(Commands.literal("stop")
                    .executes(ctx -> stopPatrol(ctx.getSource()))
                )
            )

            // /squire recall — dismiss the squire (only way to recall)
            .then(Commands.literal("recall")
                .executes(ctx -> recallSquire(ctx.getSource()))
            )

            // /squire godmode — op-only, toggle invulnerable + max stats
            .then(Commands.literal("godmode")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> toggleGodMode(ctx.getSource()))
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

            // /squire role <miner|farmer|fisher|none> — set work role (Wave 1)
            .then(Commands.literal("role")
                .then(Commands.literal("miner").executes(ctx -> setRole(ctx.getSource(), com.sjviklabs.squire.brain.WorkRole.MINER)))
                .then(Commands.literal("farmer").executes(ctx -> setRole(ctx.getSource(), com.sjviklabs.squire.brain.WorkRole.FARMER)))
                .then(Commands.literal("fisher").executes(ctx -> setRole(ctx.getSource(), com.sjviklabs.squire.brain.WorkRole.FISHER)))
                .then(Commands.literal("none").executes(ctx -> setRole(ctx.getSource(), com.sjviklabs.squire.brain.WorkRole.NONE)))
            )

            // /squire homechest — set home chest for auto-deposit (Wave 1)
            .then(Commands.literal("homechest")
                .executes(ctx -> setHomeChest(ctx.getSource()))
                .then(Commands.literal("clear").executes(ctx -> clearHomeChest(ctx.getSource())))
            )

            // /squire queue — task queue management
            .then(Commands.literal("queue")
                // /squire queue list — show queued tasks
                .executes(ctx -> queueList(ctx.getSource()))
                // /squire queue mine — enqueue area mine from Crest selection
                .then(Commands.literal("mine")
                    .executes(ctx -> queueMine(ctx.getSource()))
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> queueMineBlock(ctx.getSource(),
                            BlockPosArgument.getBlockPos(ctx, "pos")))
                    )
                )
                // /squire queue farm — enqueue farm from Crest selection or 16x16
                .then(Commands.literal("farm")
                    .executes(ctx -> queueFarm(ctx.getSource()))
                )
                // /squire queue fish — enqueue fishing
                .then(Commands.literal("fish")
                    .executes(ctx -> queueFish(ctx.getSource()))
                )
                // /squire queue clear — clear all queued tasks
                .then(Commands.literal("clear")
                    .executes(ctx -> queueClear(ctx.getSource()))
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
     * Returns the player's squire, or null. Sends failure message if no squire or not a player.
     */
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

        // Version header so players can verify in-game which jar they're running.
        String modVersion = net.neoforged.fml.ModList.get()
            .getModContainerById(com.sjviklabs.squire.SquireMod.MODID)
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

        // Show abilities
        source.sendSuccess(() -> Component.literal("--- Abilities ---"), false);
        var abilities = com.sjviklabs.squire.progression.ProgressionDataLoader.getAbilities();
        for (var ability : abilities) {
            String tierName = ability.unlockTier();
            boolean unlocked = squire.getTier().ordinal() >= com.sjviklabs.squire.entity.SquireTier.valueOf(tierName.toUpperCase()).ordinal();
            String status = unlocked ? "\u2714 " : "\u2718 Lv." + com.sjviklabs.squire.entity.SquireTier.valueOf(tierName.toUpperCase()).getMinLevel() + " ";
            source.sendSuccess(() -> Component.literal(status + ability.id() + " — " + ability.description()), false);
        }

        return 1;
    }

    // ================================================================
    // /squire mine, place, farm, fish, mount, dismount
    // ================================================================

    private static int startShaft(CommandSourceStack source, int width, int depth) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
        net.minecraft.core.BlockPos center = squire.blockPosition();
        squire.getSquireBrain().getMiningHandler().setShaftTarget(center, width, depth);
        String label = width == 2 ? "2x2" : "1x2";
        source.sendSuccess(() -> Component.literal("Squire digging " + label + " shaft, depth " + depth), false);
        return 1;
    }

    private static int mineBlock(CommandSourceStack source, net.minecraft.core.BlockPos pos) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
        squire.getSquireBrain().getMiningHandler().setTarget(pos);
        source.sendSuccess(() -> Component.literal("Squire mining at " + pos.toShortString()), false);
        return 1;
    }

    private static int placeBlock(CommandSourceStack source, net.minecraft.core.BlockPos pos) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
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

    private static int mineArea(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (player == null) return 0;
        SquireEntity squire = findOwnedSquire(player);
        if (squire == null) { source.sendFailure(Component.literal("You have no active squire.")); return 0; }

        // Check held Crest for area selection
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof com.sjviklabs.squire.item.SquireCrestItem) {
            net.minecraft.core.BlockPos[] area = com.sjviklabs.squire.item.SquireCrestItem.getSelectedArea(held);
            if (area != null && squire.getSquireBrain() != null) {
                try {
                    int count = squire.getSquireBrain().getMiningHandler().setAreaTarget(area[0], area[1]);
                    com.sjviklabs.squire.item.SquireCrestItem.clearSelection(held);
                    source.sendSuccess(() -> Component.literal("Squire mining area: " + count + " blocks."), false);
                    return 1;
                } catch (RuntimeException e) {
                    // Defensive: any unexpected failure surfaces as a clean command error,
                    // not a crash. v3.1.1 — "breaks easily on accidental crest command" fix.
                    source.sendFailure(Component.literal("Could not start mining: " + e.getMessage()));
                    return 0;
                }
            }
        }
        source.sendFailure(Component.literal("Select an area first: right-click two blocks with the Crest."));
        return 0;
    }

    private static int startFarm(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (player == null) return 0;
        SquireEntity squire = findOwnedSquire(player);
        if (squire == null) { source.sendFailure(Component.literal("You have no active squire.")); return 0; }

        // Check held Crest for area selection
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof com.sjviklabs.squire.item.SquireCrestItem) {
            net.minecraft.core.BlockPos[] area = com.sjviklabs.squire.item.SquireCrestItem.getSelectedArea(held);
            if (area != null && squire.getSquireBrain() != null) {
                try {
                    squire.getSquireBrain().getFarmingHandler().setArea(area[0], area[1]);
                    com.sjviklabs.squire.item.SquireCrestItem.clearSelection(held);
                    source.sendSuccess(() -> Component.literal("Squire farming the selected area."), false);
                    return 1;
                } catch (RuntimeException e) {
                    source.sendFailure(Component.literal("Could not start farming: " + e.getMessage()));
                    return 0;
                }
            }
        }
        // No area selected — create a 16x16 area around the squire
        if (squire.getSquireBrain() == null) {
            source.sendFailure(Component.literal("Squire is not ready yet — try again in a moment."));
            return 0;
        }
        try {
            net.minecraft.core.BlockPos squirePos = squire.blockPosition();
            squire.getSquireBrain().getFarmingHandler().setArea(
                    squirePos.offset(-8, 0, -8), squirePos.offset(8, 0, 8));
            source.sendSuccess(() -> Component.literal("Squire farming 16x16 area around current position."), false);
            return 1;
        } catch (RuntimeException e) {
            source.sendFailure(Component.literal("Could not start farming: " + e.getMessage()));
            return 0;
        }
    }

    private static int clearSelection(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (player == null) return 0;
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof com.sjviklabs.squire.item.SquireCrestItem) {
            com.sjviklabs.squire.item.SquireCrestItem.clearSelection(held);
            source.sendSuccess(() -> Component.literal("Area selection cleared."), false);
            return 1;
        }
        source.sendFailure(Component.literal("Hold the Crest to clear selection."));
        return 0;
    }

    private static int startFish(CommandSourceStack source) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
        squire.getSquireBrain().getFishingHandler().startFishing();
        source.sendSuccess(() -> Component.literal("Squire is fishing."), false);
        return 1;
    }

    private static int mountHorse(CommandSourceStack source) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;

        // Find nearest saddled horse within 16 blocks of the squire
        var mountHandler = squire.getSquireBrain().getMountHandler();
        double range = 16.0;
        var horses = squire.level().getEntitiesOfClass(
                net.minecraft.world.entity.animal.horse.AbstractHorse.class,
                squire.getBoundingBox().inflate(range),
                h -> h.isSaddled() && h.isAlive() && !h.isVehicle());

        if (horses.isEmpty()) {
            source.sendFailure(Component.literal("No saddled horse nearby."));
            return 0;
        }

        // Pick the closest one
        net.minecraft.world.entity.animal.horse.AbstractHorse nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (var horse : horses) {
            double dist = squire.distanceToSqr(horse);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = horse;
            }
        }

        // Bond squire to this horse (persists via NBT)
        mountHandler.setHorseUUID(nearest.getUUID());
        squire.getSquireBrain().getMachine().forceState(com.sjviklabs.squire.brain.SquireAIState.MOUNTING);
        String horseName = nearest.hasCustomName() && nearest.getCustomName() != null
                ? nearest.getCustomName().getString() : "horse";
        source.sendSuccess(() -> Component.literal("Squire is mounting " + horseName + "."), false);
        return 1;
    }

    private static int dismountHorse(CommandSourceStack source) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
        squire.getSquireBrain().getMountHandler().orderDismount(squire);
        // Keep horse UUID — squire stays bonded for remounting
        squire.getSquireBrain().getMachine().forceState(com.sjviklabs.squire.brain.SquireAIState.IDLE);
        source.sendSuccess(() -> Component.literal("Squire dismounted."), false);
        return 1;
    }

    private static int recallSquire(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (player == null) return 0;
        SquireEntity squire = findOwnedSquire(player);
        if (squire == null) { source.sendFailure(Component.literal("You have no active squire.")); return 0; }

        // Persist state + inventory to attachment before discarding
        com.sjviklabs.squire.entity.SquireDataAttachment.SquireData data =
                player.getData(com.sjviklabs.squire.SquireRegistry.SQUIRE_DATA.get());
        // Serialize inventory to NBT for recall persistence
        net.minecraft.nbt.CompoundTag inventoryNBT = squire.getItemHandler()
                .serializeNBT(squire.registryAccess());
        player.setData(com.sjviklabs.squire.SquireRegistry.SQUIRE_DATA.get(),
                data.withXP(squire.getTotalXP(), squire.getLevel())
                    .withSquireUUID(java.util.Optional.empty())
                    .withInventory(inventoryNBT));
        squire.releaseChunkLoading();
        squire.discard();
        source.sendSuccess(() -> Component.literal("Your squire returns to the Crest."), false);
        return 1;
    }

    private static int toggleGodMode(CommandSourceStack source) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;

        boolean godMode = !squire.isInvulnerable();
        squire.setInvulnerable(godMode);
        if (godMode) {
            squire.setHealth(squire.getMaxHealth());
            squire.setLevel(30); // Champion
            source.sendSuccess(() -> Component.literal("Squire GOD MODE enabled — invulnerable, Champion tier."), false);
        } else {
            source.sendSuccess(() -> Component.literal("Squire GOD MODE disabled."), false);
        }
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
    // /squire store, fetch
    // ================================================================

    /**
     * Get the block the player is looking at (raycast, 5 block range).
     * Returns null if not looking at a block.
     */
    @Nullable
    private static net.minecraft.core.BlockPos getLookedAtBlock(ServerPlayer player) {
        var hitResult = player.pick(5.0, 1.0f, false);
        if (hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit
                && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return blockHit.getBlockPos();
        }
        return null;
    }

    private static int storeItems(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (player == null) return 0;
        net.minecraft.core.BlockPos pos = getLookedAtBlock(player);
        if (pos == null || player.serverLevel().getBlockEntity(pos) == null) {
            source.sendFailure(Component.literal("Look at a chest or container."));
            return 0;
        }
        return storeItemsAt(source, pos);
    }

    private static int storeItemsAt(CommandSourceStack source, net.minecraft.core.BlockPos pos) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
        squire.getSquireBrain().getChestHandler().setDepositTarget(pos);
        source.sendSuccess(() -> Component.literal("Squire depositing items at " + pos.toShortString()), false);
        return 1;
    }

    private static int fetchItems(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (player == null) return 0;
        net.minecraft.core.BlockPos pos = getLookedAtBlock(player);
        if (pos == null || player.serverLevel().getBlockEntity(pos) == null) {
            source.sendFailure(Component.literal("Look at a chest or container."));
            return 0;
        }
        return fetchItemsAt(source, pos, null, 64);
    }

    private static int fetchItemsAt(CommandSourceStack source, net.minecraft.core.BlockPos pos,
                                     @Nullable net.minecraft.world.item.Item item, int amount) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
        squire.getSquireBrain().getChestHandler().setWithdrawTarget(pos, item, amount);
        source.sendSuccess(() -> Component.literal("Squire fetching items from " + pos.toShortString()), false);
        return 1;
    }

    // ================================================================
    // /squire patrol
    // ================================================================

    private static int startPatrol(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (player == null) return 0;
        SquireEntity squire = findOwnedSquire(player);
        if (squire == null) { source.sendFailure(Component.literal("You have no active squire.")); return 0; }

        // v3.1.0 — patrol route is built from the Crest's area selection.
        // Player must be holding their Squire's Crest with a completed area marked.
        net.minecraft.world.item.ItemStack mainHand = player.getMainHandItem();
        net.minecraft.world.item.ItemStack offHand = player.getOffhandItem();
        net.minecraft.world.item.ItemStack crestStack = null;
        if (mainHand.getItem() instanceof com.sjviklabs.squire.item.SquireCrestItem) {
            crestStack = mainHand;
        } else if (offHand.getItem() instanceof com.sjviklabs.squire.item.SquireCrestItem) {
            crestStack = offHand;
        }
        if (crestStack == null) {
            source.sendFailure(Component.literal(
                    "Hold your Squire's Crest with a marked area before patrolling."));
            return 0;
        }

        net.minecraft.core.BlockPos[] corners =
                com.sjviklabs.squire.item.SquireCrestItem.getSelectedArea(crestStack);
        if (corners == null) {
            source.sendFailure(Component.literal(
                    "Mark an area first: right-click two blocks with the Crest to define the patrol zone."));
            return 0;
        }

        java.util.List<net.minecraft.core.BlockPos> route =
                com.sjviklabs.squire.brain.handler.PatrolHandler
                        .buildRouteFromCrestArea(corners[0], corners[1]);

        if (route.isEmpty()) {
            source.sendFailure(Component.literal("Patrol route could not be built from the selected area."));
            return 0;
        }

        squire.getSquireBrain().getMachine().forceState(
                com.sjviklabs.squire.brain.SquireAIState.PATROL_WALK);

        var patrolField = getPatrolHandler(squire);
        if (patrolField != null) {
            patrolField.startPatrol(route);
        }

        source.sendSuccess(() -> Component.literal(
                "Squire patrolling " + route.size() + "-corner perimeter."), false);
        return 1;
    }

    private static int stopPatrol(CommandSourceStack source) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;

        var patrolField = getPatrolHandler(squire);
        if (patrolField != null) {
            patrolField.stopPatrol();
        }
        squire.getSquireBrain().getMachine().forceState(com.sjviklabs.squire.brain.SquireAIState.IDLE);
        source.sendSuccess(() -> Component.literal("Squire stopped patrolling."), false);
        return 1;
    }

    /** Returns the PatrolHandler from SquireBrain, or null if brain is not yet initialized. */
    @Nullable
    private static com.sjviklabs.squire.brain.handler.PatrolHandler getPatrolHandler(SquireEntity squire) {
        var brain = squire.getSquireBrain();
        return brain != null ? brain.getPatrolHandler() : null;
    }

    // ================================================================
    // /squire role, homechest (Wave 1)
    // ================================================================

    private static int setRole(CommandSourceStack source, com.sjviklabs.squire.brain.WorkRole role) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
        squire.getSquireBrain().setWorkRole(role);
        source.sendSuccess(() -> Component.literal("Squire role set to: " + role.name().toLowerCase()), false);
        return 1;
    }

    private static int setHomeChest(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (player == null) return 0;
        SquireEntity squire = findOwnedSquire(player);
        if (squire == null) { source.sendFailure(Component.literal("You have no active squire.")); return 0; }

        // Raycast to find looked-at block
        net.minecraft.core.BlockPos pos = getLookedAtBlock(player);
        if (pos != null) {
            // v3.1.2 — accept any block that exposes an ItemHandler capability.
            // Was limited to BaseContainerBlockEntity, which excluded most modded storage
            // (Iron Chests, Sophisticated Storage, Storage Drawers, Refined Storage, AE2, etc.).
            // ChestHandler's deposit path already uses the capability API; the command was the only gate.
            if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                var handler = serverLevel.getCapability(
                        net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null);
                if (handler != null) {
                    squire.getSquireBrain().setHomeChest(pos);
                    source.sendSuccess(() -> Component.literal("Home chest set at " + pos.toShortString()), false);
                    return 1;
                }
            }
            // Vanilla fallback for non-capability containers
            var blockEntity = player.level().getBlockEntity(pos);
            if (blockEntity instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity) {
                squire.getSquireBrain().setHomeChest(pos);
                source.sendSuccess(() -> Component.literal("Home chest set at " + pos.toShortString()), false);
                return 1;
            }
            source.sendFailure(Component.literal("Look at a chest or container."));
            return 0;
        }
        source.sendFailure(Component.literal("Look at a chest to set it as home chest."));
        return 0;
    }

    private static int clearHomeChest(CommandSourceStack source) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;
        squire.getSquireBrain().setHomeChest(null);
        source.sendSuccess(() -> Component.literal("Home chest cleared."), false);
        return 1;
    }

    // ================================================================
    // /squire queue
    // ================================================================

    private static int queueList(CommandSourceStack source) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;

        var queue = squire.getSquireBrain().getTaskQueue();
        if (queue.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Task queue is empty."), false);
        } else {
            source.sendSuccess(() -> Component.literal("Task queue: " + queue.size() + " task(s) pending."), false);
        }
        return 1;
    }

    private static int queueMine(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (player == null) return 0;
        SquireEntity squire = findOwnedSquire(player);
        if (squire == null) { source.sendFailure(Component.literal("You have no active squire.")); return 0; }

        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof com.sjviklabs.squire.item.SquireCrestItem) {
            net.minecraft.core.BlockPos[] area = com.sjviklabs.squire.item.SquireCrestItem.getSelectedArea(held);
            if (area != null) {
                net.minecraft.nbt.CompoundTag p = new net.minecraft.nbt.CompoundTag();
                p.putInt("ax", area[0].getX()); p.putInt("ay", area[0].getY()); p.putInt("az", area[0].getZ());
                p.putInt("bx", area[1].getX()); p.putInt("by", area[1].getY()); p.putInt("bz", area[1].getZ());
                boolean ok = squire.getSquireBrain().getTaskQueue()
                        .enqueue(new com.sjviklabs.squire.brain.SquireTask("mine_area", p));
                if (ok) {
                    com.sjviklabs.squire.item.SquireCrestItem.clearSelection(held);
                    source.sendSuccess(() -> Component.literal("Queued: mine area."), false);
                } else {
                    source.sendFailure(Component.literal("Task queue is full."));
                }
                return 1;
            }
        }
        source.sendFailure(Component.literal("Select an area with the Crest first."));
        return 0;
    }

    private static int queueMineBlock(CommandSourceStack source, net.minecraft.core.BlockPos pos) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;

        net.minecraft.nbt.CompoundTag p = new net.minecraft.nbt.CompoundTag();
        p.putInt("x", pos.getX()); p.putInt("y", pos.getY()); p.putInt("z", pos.getZ());
        boolean ok = squire.getSquireBrain().getTaskQueue()
                .enqueue(new com.sjviklabs.squire.brain.SquireTask("mine", p));
        if (ok) {
            source.sendSuccess(() -> Component.literal("Queued: mine at " + pos.toShortString()), false);
        } else {
            source.sendFailure(Component.literal("Task queue is full."));
        }
        return 1;
    }

    private static int queueFarm(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (player == null) return 0;
        SquireEntity squire = findOwnedSquire(player);
        if (squire == null) { source.sendFailure(Component.literal("You have no active squire.")); return 0; }

        net.minecraft.nbt.CompoundTag p = new net.minecraft.nbt.CompoundTag();

        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof com.sjviklabs.squire.item.SquireCrestItem) {
            net.minecraft.core.BlockPos[] area = com.sjviklabs.squire.item.SquireCrestItem.getSelectedArea(held);
            if (area != null) {
                p.putInt("ax", area[0].getX()); p.putInt("ay", area[0].getY()); p.putInt("az", area[0].getZ());
                p.putInt("bx", area[1].getX()); p.putInt("by", area[1].getY()); p.putInt("bz", area[1].getZ());
                com.sjviklabs.squire.item.SquireCrestItem.clearSelection(held);
            }
        }

        // If no Crest area, default to 16x16 around squire
        if (!p.contains("ax")) {
            net.minecraft.core.BlockPos sp = squire.blockPosition();
            p.putInt("ax", sp.getX() - 8); p.putInt("ay", sp.getY()); p.putInt("az", sp.getZ() - 8);
            p.putInt("bx", sp.getX() + 8); p.putInt("by", sp.getY()); p.putInt("bz", sp.getZ() + 8);
        }

        boolean ok = squire.getSquireBrain().getTaskQueue()
                .enqueue(new com.sjviklabs.squire.brain.SquireTask("farm", p));
        if (ok) {
            source.sendSuccess(() -> Component.literal("Queued: farm area."), false);
        } else {
            source.sendFailure(Component.literal("Task queue is full."));
        }
        return 1;
    }

    private static int queueFish(CommandSourceStack source) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;

        boolean ok = squire.getSquireBrain().getTaskQueue()
                .enqueue(new com.sjviklabs.squire.brain.SquireTask("fish", new net.minecraft.nbt.CompoundTag()));
        if (ok) {
            source.sendSuccess(() -> Component.literal("Queued: fish."), false);
        } else {
            source.sendFailure(Component.literal("Task queue is full."));
        }
        return 1;
    }

    private static int queueClear(CommandSourceStack source) {
        SquireEntity squire = requireSquire(source);
        if (squire == null) return 0;

        squire.getSquireBrain().getTaskQueue().clear();
        source.sendSuccess(() -> Component.literal("Task queue cleared."), false);
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
