package com.sjviklabs.squire.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Squire mod configuration — 53 entries in 10 sections, all server-enforced (COMMON type).
 *
 * Generated file: squire-common.toml
 *
 * Usage: SquireConfig.aggroRange.get()
 *        SquireConfig.maxSquiresPerPlayer.get()
 *
 * VALIDATOR RULE: every defineInRange default satisfies its own min/max.
 * Violating this causes an infinite correction loop on every server start.
 */
public final class SquireConfig {

    public static final ModConfigSpec SPEC;

    // ── [general] ────────────────────────────────────────────────────────────
    public static final ModConfigSpec.IntValue maxSquiresPerPlayer;
    public static final ModConfigSpec.BooleanValue announceEvents;

    // ── [combat] ─────────────────────────────────────────────────────────────
    public static final ModConfigSpec.DoubleValue aggroRange;
    public static final ModConfigSpec.DoubleValue combatLeashDistance;
    public static final ModConfigSpec.DoubleValue meleeRange;
    public static final ModConfigSpec.DoubleValue rangedRange;
    public static final ModConfigSpec.DoubleValue fleeHealthThreshold;
    public static final ModConfigSpec.IntValue combatTickRate;
    public static final ModConfigSpec.BooleanValue autoEquipWeapon;
    public static final ModConfigSpec.BooleanValue autoEquipArmor;
    public static final ModConfigSpec.BooleanValue shieldBlocking;
    public static final ModConfigSpec.IntValue shieldCooldownTicks;
    public static final ModConfigSpec.IntValue halberdSweepInterval;

    // ── [follow] ─────────────────────────────────────────────────────────────
    public static final ModConfigSpec.DoubleValue followStartDistance;
    public static final ModConfigSpec.DoubleValue followStopDistance;
    public static final ModConfigSpec.DoubleValue sprintDistance;
    public static final ModConfigSpec.DoubleValue walkSpeed;
    public static final ModConfigSpec.DoubleValue sprintSpeed;
    public static final ModConfigSpec.IntValue followTickRate;
    public static final ModConfigSpec.IntValue stuckDetectionTicks;
    public static final ModConfigSpec.IntValue stuckRecoveryTicks;

    // ── [torch] ──────────────────────────────────────────────────────────────
    public static final ModConfigSpec.IntValue torchLightThreshold;
    public static final ModConfigSpec.IntValue torchCheckInterval;
    public static final ModConfigSpec.IntValue torchPlaceCooldown;

    // ── [mining] ─────────────────────────────────────────────────────────────
    public static final ModConfigSpec.DoubleValue mineReach;
    public static final ModConfigSpec.DoubleValue breakSpeedMultiplier;
    public static final ModConfigSpec.DoubleValue miningSpeedPerLevel;
    public static final ModConfigSpec.IntValue maxClearVolume;
    public static final ModConfigSpec.IntValue areaMaxBlocks;
    public static final ModConfigSpec.IntValue clearConfirmThreshold;
    public static final ModConfigSpec.IntValue miningTickRate;
    public static final ModConfigSpec.IntValue chunkLoadRadius;

    // ── [placing] ────────────────────────────────────────────────────────────
    public static final ModConfigSpec.DoubleValue placingReach;

    // ── [chest] ──────────────────────────────────────────────────────────────
    public static final ModConfigSpec.DoubleValue chestReach;
    public static final ModConfigSpec.IntValue chestInteractCooldown;
    public static final ModConfigSpec.IntValue chestAbilityMinLevel;

    // ── [farming] ────────────────────────────────────────────────────────────
    public static final ModConfigSpec.DoubleValue farmingReach;
    public static final ModConfigSpec.IntValue farmingTickRate;
    public static final ModConfigSpec.BooleanValue autoReplant;

    // ── [fishing] ────────────────────────────────────────────────────────────
    public static final ModConfigSpec.IntValue fishingDurationTicks;
    public static final ModConfigSpec.IntValue fishingTickRate;
    public static final ModConfigSpec.IntValue fishingLootRolls;

    // ── [patrol] ─────────────────────────────────────────────────────────────
    public static final ModConfigSpec.IntValue patrolDefaultWait;
    public static final ModConfigSpec.IntValue patrolMaxRouteLength;

    // ── [mount] ──────────────────────────────────────────────────────────────
    public static final ModConfigSpec.DoubleValue horseSearchRange;
    public static final ModConfigSpec.DoubleValue mountedFollowSpeed;
    public static final ModConfigSpec.DoubleValue horseFleeThreshold;
    public static final ModConfigSpec.DoubleValue mountedMeleeReachBonus;

    // ── [progression] ────────────────────────────────────────────────────────
    public static final ModConfigSpec.IntValue xpPerKill;
    public static final ModConfigSpec.IntValue xpPerBlock;
    public static final ModConfigSpec.IntValue xpPerHarvest;
    public static final ModConfigSpec.IntValue xpPerFish;
    public static final ModConfigSpec.DoubleValue healthPerLevel;
    public static final ModConfigSpec.DoubleValue damagePerLevel;
    public static final ModConfigSpec.DoubleValue speedPerLevel;
    public static final ModConfigSpec.IntValue undyingCooldownTicks;

    // ── [inventory] ──────────────────────────────────────────────────────────
    public static final ModConfigSpec.DoubleValue itemPickupRange;
    public static final ModConfigSpec.BooleanValue autoPickup;
    public static final ModConfigSpec.IntValue autoPickupTickRate;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> junkFilterList;

    // ── [work] ───────────────────────────────────────────────────────────────
    public static final ModConfigSpec.DoubleValue autoDepositThreshold;
    public static final ModConfigSpec.IntValue idlePatrolRadius;
    public static final ModConfigSpec.BooleanValue autoCraftTools;
    public static final ModConfigSpec.ConfigValue<String> maxCraftTier;

    // ── [rendering] ──────────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue showNameTag;
    public static final ModConfigSpec.BooleanValue backpackVisual;
    public static final ModConfigSpec.IntValue clientTrackingRange;

    // ── [debug] ──────────────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue godMode;
    public static final ModConfigSpec.BooleanValue logFsmTransitions;
    public static final ModConfigSpec.BooleanValue showAiState;
    public static final ModConfigSpec.BooleanValue drawPathfinding;
    public static final ModConfigSpec.BooleanValue activityLogging;
    public static final ModConfigSpec.BooleanValue verboseInventoryLogs;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        // ── [general] ────────────────────────────────────────────────────────
        builder.push("general");
        maxSquiresPerPlayer = builder
                .comment("Maximum squires one player can have summoned simultaneously")
                .defineInRange("maxSquiresPerPlayer", 1, 1, 5);
        announceEvents = builder
                .comment("Send chat messages for squire events (death, tier-up, recall)")
                .define("announceEvents", true);
        builder.pop();

        // ── [combat] ─────────────────────────────────────────────────────────
        builder.push("combat");
        aggroRange = builder
                .comment("Range in blocks at which squire detects and engages hostile mobs")
                .defineInRange("aggroRange", 12.0, 1.0, 32.0);
        combatLeashDistance = builder
                .comment("Max distance squire will chase a target before disengaging")
                .defineInRange("combatLeashDistance", 20.0, 4.0, 64.0);
        meleeRange = builder
                .comment("Melee attack reach in blocks")
                .defineInRange("meleeRange", 2.5, 1.0, 6.0);
        rangedRange = builder
                .comment("Range at which squire switches from melee to ranged combat")
                .defineInRange("rangedRange", 8.0, 3.0, 20.0);
        fleeHealthThreshold = builder
                .comment("HP fraction (0.0-1.0) below which squire flees combat")
                .defineInRange("fleeHealthThreshold", 0.25, 0.0, 0.9);
        combatTickRate = builder
                .comment("Ticks between combat AI evaluations (lower = more responsive, higher = better performance)")
                .defineInRange("combatTickRate", 4, 1, 20);
        autoEquipWeapon = builder
                .comment("Squire automatically equips the best weapon from its inventory")
                .define("autoEquipWeapon", true);
        autoEquipArmor = builder
                .comment("Squire automatically equips the best armor from its inventory")
                .define("autoEquipArmor", true);
        shieldBlocking = builder
                .comment("Squire uses shield to block incoming melee damage")
                .define("shieldBlocking", true);
        shieldCooldownTicks = builder
                .comment("Cooldown ticks after shield is broken before squire can raise it again")
                .defineInRange("shieldCooldownTicks", 100, 20, 400);
        halberdSweepInterval = builder
                .comment("Number of hits between Halberd sweep AoE attacks")
                .defineInRange("halberdSweepInterval", 3, 1, 10);
        builder.pop();

        // ── [follow] ─────────────────────────────────────────────────────────
        builder.push("follow");
        followStartDistance = builder
                .comment("Distance in blocks at which squire begins following the player")
                .defineInRange("followStartDistance", 6.0, 2.0, 20.0);
        followStopDistance = builder
                .comment("Distance in blocks at which squire stops following (within this range, stays put)")
                .defineInRange("followStopDistance", 3.0, 1.0, 10.0);
        sprintDistance = builder
                .comment("Distance in blocks at which squire begins sprinting to catch up")
                .defineInRange("sprintDistance", 10.0, 4.0, 32.0);
        walkSpeed = builder
                .comment("Squire walk speed multiplier (applied to base 0.3)")
                .defineInRange("walkSpeed", 1.0, 0.5, 2.0);
        sprintSpeed = builder
                .comment("Squire sprint speed multiplier (applied to base 0.3)")
                .defineInRange("sprintSpeed", 1.4, 1.0, 3.0);
        followTickRate = builder
                .comment("Ticks between follow AI evaluations")
                .defineInRange("followTickRate", 2, 1, 10);
        stuckDetectionTicks = builder
                .comment("Ticks of no movement before squire is considered stuck")
                .defineInRange("stuckDetectionTicks", 60, 20, 200);
        stuckRecoveryTicks = builder
                .comment("Ticks squire waits in recovery mode after being unstuck")
                .defineInRange("stuckRecoveryTicks", 20, 5, 100);
        builder.pop();

        // ── [torch] ──────────────────────────────────────────────────────────
        builder.push("torch");
        torchLightThreshold = builder
                .comment("Block light level at or below which squire places a torch (0=only in pitch dark, 15=always)")
                .defineInRange("torchLightThreshold", 7, 0, 15);
        torchCheckInterval = builder
                .comment("Ticks between torch placement checks when following (lower = more responsive)")
                .defineInRange("torchCheckInterval", 20, 5, 100);
        torchPlaceCooldown = builder
                .comment("Ticks squire waits after placing a torch before placing another")
                .defineInRange("torchPlaceCooldown", 40, 10, 200);
        builder.pop();

        // ── [mining] ─────────────────────────────────────────────────────────
        builder.push("mining");
        mineReach = builder
                .comment("Mining reach in blocks")
                .defineInRange("mineReach", 4.0, 1.0, 8.0);
        breakSpeedMultiplier = builder
                .comment("Mining speed multiplier relative to player with correct tool")
                .defineInRange("breakSpeedMultiplier", 0.8, 0.1, 3.0);
        miningSpeedPerLevel = builder
                .comment("Break speed bonus added per squire level (at default 0.0167, Lv30 = +50%)")
                .defineInRange("miningSpeedPerLevel", 0.0167, 0.0, 0.1);
        maxClearVolume = builder
                .comment("Maximum blocks in an area-clear operation before requiring confirmation")
                .defineInRange("maxClearVolume", 64, 1, 512);
        areaMaxBlocks = builder
                .comment("Hard cap on blocks queued in a single area-clear operation")
                .defineInRange("areaMaxBlocks", 256, 1, 1024);
        clearConfirmThreshold = builder
                .comment("Block count above which a confirmation is required to begin area clear")
                .defineInRange("clearConfirmThreshold", 32, 1, 512);
        miningTickRate = builder
                .comment("Ticks between block-break progress ticks during mining")
                .defineInRange("miningTickRate", 4, 1, 20);
        chunkLoadRadius = builder
                .comment("Chunk radius to force-load during area clear (0 = no force loading)")
                .defineInRange("chunkLoadRadius", 1, 0, 3);
        builder.pop();

        // ── [placing]
        builder.push("placing");
        placingReach = builder
                .comment("Reach in blocks for block placement operations")
                .defineInRange("placingReach", 4.0, 1.0, 8.0);
        builder.pop();

        // ── [chest]
        builder.push("chest");
        chestReach = builder
                .comment("Reach in blocks for chest interaction (deposit/withdraw)")
                .defineInRange("chestReach", 3.0, 1.0, 8.0);
        chestInteractCooldown = builder
                .comment("Ticks between individual item transfers during chest interaction")
                .defineInRange("chestInteractCooldown", 2, 1, 20);
        chestAbilityMinLevel = builder
                .comment("Minimum squire level required to use chest deposit/withdraw (0 = available from start)")
                .defineInRange("chestAbilityMinLevel", 0, 0, 30);
        builder.pop();

        // ── [farming] ────────────────────────────────────────────────────────
        builder.push("farming");
        farmingReach = builder
                .comment("Reach in blocks for farming operations (till, harvest, plant)")
                .defineInRange("farmingReach", 4.0, 1.0, 8.0);
        farmingTickRate = builder
                .comment("Ticks between farming action ticks")
                .defineInRange("farmingTickRate", 10, 1, 40);
        autoReplant = builder
                .comment("Squire automatically replants seeds after harvesting")
                .define("autoReplant", true);
        builder.pop();

        // ── [fishing] ────────────────────────────────────────────────────────
        builder.push("fishing");
        fishingDurationTicks = builder
                .comment("Base ticks per fishing attempt before catch is resolved")
                .defineInRange("fishingDurationTicks", 200, 40, 800);
        fishingTickRate = builder
                .comment("Ticks between fishing AI ticks")
                .defineInRange("fishingTickRate", 20, 5, 100);
        fishingLootRolls = builder
                .comment("Number of loot table rolls per catch (higher = more items)")
                .defineInRange("fishingLootRolls", 1, 1, 5);
        builder.pop();

        // ── [patrol] ─────────────────────────────────────────────────────────
        builder.push("patrol");
        patrolDefaultWait = builder
                .comment("Ticks the squire waits at each waypoint before moving on")
                .defineInRange("patrolDefaultWait", 40, 0, 200);
        patrolMaxRouteLength = builder
                .comment("Maximum number of signposts in a patrol route")
                .defineInRange("patrolMaxRouteLength", 32, 2, 128);
        builder.pop();

        // ── [mount] ──────────────────────────────────────────────────────────
        builder.push("mount");
        horseSearchRange = builder
                .comment("Range in blocks to scan for the squire's assigned horse")
                .defineInRange("horseSearchRange", 32.0, 4.0, 64.0);
        mountedFollowSpeed = builder
                .comment("Speed multiplier when following player while mounted (1.0 = horse base speed)")
                .defineInRange("mountedFollowSpeed", 1.0, 0.3, 2.0);
        horseFleeThreshold = builder
                .comment("Horse HP fraction below which squire dismounts to protect itself")
                .defineInRange("horseFleeThreshold", 0.2, 0.0, 0.9);
        mountedMeleeReachBonus = builder
                .comment("Extra melee reach added when attacking from horseback (compensates for height offset)")
                .defineInRange("mountedMeleeReachBonus", 1.5, 0.0, 4.0);
        builder.pop();

        // ── [progression] ────────────────────────────────────────────────────
        builder.push("progression");
        xpPerKill = builder
                .comment("XP awarded to squire per mob kill")
                .defineInRange("xpPerKill", 10, 1, 1000);
        xpPerBlock = builder
                .comment("XP awarded per block mined by the squire")
                .defineInRange("xpPerBlock", 1, 0, 100);
        xpPerHarvest = builder
                .comment("XP awarded per crop harvested by the squire")
                .defineInRange("xpPerHarvest", 2, 0, 100);
        xpPerFish = builder
                .comment("XP awarded per fish caught by the squire")
                .defineInRange("xpPerFish", 5, 0, 100);
        healthPerLevel = builder
                .comment("HP bonus added per level (base 20 HP at level 0)")
                .defineInRange("healthPerLevel", 0.5, 0.0, 5.0);
        damagePerLevel = builder
                .comment("Attack damage bonus added per level")
                .defineInRange("damagePerLevel", 0.1, 0.0, 2.0);
        speedPerLevel = builder
                .comment("Movement speed bonus added per level")
                .defineInRange("speedPerLevel", 0.002, 0.0, 0.02);
        undyingCooldownTicks = builder
                .comment("Ticks after Champion undying triggers before the ability resets (requires re-summon)")
                .defineInRange("undyingCooldownTicks", 24000, 100, 72000);
        builder.pop();

        // ── [inventory] ──────────────────────────────────────────────────────
        builder.push("inventory");
        itemPickupRange = builder
                .comment("Range in blocks within which squire picks up dropped items")
                .defineInRange("itemPickupRange", 3.0, 1.0, 8.0);
        autoPickup = builder
                .comment("Squire automatically picks up nearby dropped items (subject to junk filter)")
                .define("autoPickup", true);
        autoPickupTickRate = builder
                .comment("Ticks between auto-pickup scans")
                .defineInRange("autoPickupTickRate", 20, 5, 100);
        junkFilterList = builder
                .comment("Items the squire ignores during pickup (resource locations, e.g. minecraft:cobblestone)")
                .defineListAllowEmpty("junkFilter",
                        List.of("minecraft:cobblestone", "minecraft:dirt", "minecraft:gravel",
                                "minecraft:rotten_flesh", "minecraft:poisonous_potato"),
                        s -> s instanceof String str && str.contains(":"));
        builder.pop();

        // ── [work] ───────────────────────────────────────────────────────────
        builder.push("work");
        autoDepositThreshold = builder
                .comment("Backpack fill fraction (0.1-1.0) that triggers auto-deposit to home chest. Default: 0.8")
                .defineInRange("autoDepositThreshold", 0.8, 0.1, 1.0);
        idlePatrolRadius = builder
                .comment("Wander radius (blocks) when work role has no assigned area. Default: 8")
                .defineInRange("idlePatrolRadius", 8, 4, 32);
        autoCraftTools = builder
                .comment("Whether the squire auto-crafts replacement tools from inventory materials. Default: true")
                .define("autoCraftTools", true);
        maxCraftTier = builder
                .comment("Highest tier the squire will auto-craft (wooden, stone, iron, diamond). Default: stone")
                .define("maxCraftTier", "stone");
        builder.pop();

        // ── [rendering] ──────────────────────────────────────────────────────
        builder.push("rendering");
        showNameTag = builder
                .comment("Show the squire's name tag above its head")
                .define("showNameTag", true);
        backpackVisual = builder
                .comment("Show the visual backpack layer that scales with inventory tier")
                .define("backpackVisual", true);
        clientTrackingRange = builder
                .comment("Client-side entity tracking range in chunks")
                .defineInRange("clientTrackingRange", 10, 4, 32);
        builder.pop();

        // ── [debug] ──────────────────────────────────────────────────────────
        builder.push("debug");
        godMode = builder
                .comment("[DEBUG] Squire is invincible — takes no damage. Off by default.")
                .define("godMode", false);
        logFsmTransitions = builder
                .comment("[DEBUG] Log all FSM state transitions to console")
                .define("logFsmTransitions", false);
        showAiState = builder
                .comment("[DEBUG] Display current AI state above squire head (client)")
                .define("showAiState", false);
        drawPathfinding = builder
                .comment("[DEBUG] Render pathfinding nodes (client)")
                .define("drawPathfinding", false);
        activityLogging = builder
                .comment("[DEBUG] Log squire activity ticks to console")
                .define("activityLogging", false);
        verboseInventoryLogs = builder
                .comment("[DEBUG] Log all inventory insertItem/extractItem calls")
                .define("verboseInventoryLogs", false);
        builder.pop();

        SPEC = builder.build();
    }

    private SquireConfig() {}
}
