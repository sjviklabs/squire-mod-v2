package com.sjviklabs.squire.ai;

import com.sjviklabs.squire.ai.job.ChestAI;
import com.sjviklabs.squire.ai.job.FarmerAI;
import com.sjviklabs.squire.ai.job.FisherAI;
import com.sjviklabs.squire.ai.job.FollowOwnerAI;
import com.sjviklabs.squire.ai.job.GearCategorizer;
import com.sjviklabs.squire.ai.job.GuardAI;
import com.sjviklabs.squire.ai.job.IdleAI;
import com.sjviklabs.squire.ai.job.JobAI;
import com.sjviklabs.squire.ai.job.LumberjackAI;
import com.sjviklabs.squire.ai.job.MinerAI;
import com.sjviklabs.squire.ai.job.PatrolAI;
import com.sjviklabs.squire.ai.job.PlacingAI;
import com.sjviklabs.squire.ai.job.RestockAI;
import com.sjviklabs.squire.ai.state.SquireAIState;
import com.sjviklabs.squire.ai.threat.SquireThreatScanner;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireItemHandler;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

/**
 * Top-level AI router for a SquireEntity.
 *
 * One instance per squire, lazy-created on first server-side tick. Holds the currently
 * active {@link JobAI} and ticks it. Swapping job AIs is the only way the squire's
 * behavior changes — there is no global state machine competing with the job AIs.
 *
 * v4.0.0 Phase 2 scope: IdleAI + FollowOwnerAI registered. The controller picks
 * FollowOwnerAI whenever it's applicable (owner walks away + mode is FOLLOW),
 * falls back to IdleAI otherwise. Phase 3 adds GuardAI (higher priority — preempts
 * for combat). Phase 4+ adds work AIs.
 *
 * Swap cadence: once per second (20 ticks). Cheap — most ticks don't need a swap.
 * Individual job AIs tick at whatever rate their transitions require.
 */
public final class SquireAIController {

    private static final int SWAP_CHECK_INTERVAL_TICKS = 20;
    private static final int THREAT_SCAN_INTERVAL_TICKS = 10;
    // Auto-action (deposit / restock) check cadence — 5 s. Cheap when either the squire has
    // no remembered chest, an auto-action is already in flight, or a work AI owns the squire.
    private static final int AUTO_ACTION_CHECK_INTERVAL_TICKS = 100;
    // Auto-deposit fires when ≥ 80% of backpack slots hold surplus (non-gear) items.
    // Tuned so a 36-slot Champion deposits at 29 full slots, a 9-slot Servant at 7.
    private static final double AUTO_DEPOSIT_FILL_RATIO = 0.80;

    private final SquireEntity squire;

    // All job AIs are instantiated eagerly and reused — they're stateless apart from
    // their internal state machines, which are cheap to hold.
    private final JobAI idleAI;
    private final JobAI followOwnerAI;
    private final JobAI guardAI;
    private final MinerAI minerAI;
    private final LumberjackAI lumberjackAI;
    private final FarmerAI farmerAI;
    private final FisherAI fisherAI;
    private final PlacingAI placingAI;
    private final PatrolAI patrolAI;
    private final ChestAI chestAI;
    private final RestockAI restockAI;

    private JobAI activeJob;
    private int swapCounter = 0;
    private int scanCounter = 0;
    private int autoActionCounter = 0;

    public SquireAIController(SquireEntity squire) {
        this.squire = squire;
        this.idleAI = new IdleAI(squire);
        this.followOwnerAI = new FollowOwnerAI(squire);
        this.guardAI = new GuardAI(squire);
        this.minerAI = new MinerAI(squire);
        this.lumberjackAI = new LumberjackAI(squire);
        this.farmerAI = new FarmerAI(squire);
        this.fisherAI = new FisherAI(squire);
        this.placingAI = new PlacingAI(squire);
        this.patrolAI = new PatrolAI(squire);
        this.chestAI = new ChestAI(squire);
        this.restockAI = new RestockAI(squire);
        this.activeJob = idleAI;
    }

    /** Accessors for work-AI intake from commands. */
    public MinerAI getMinerAI() { return minerAI; }
    public LumberjackAI getLumberjackAI() { return lumberjackAI; }
    public FarmerAI getFarmerAI() { return farmerAI; }
    public FisherAI getFisherAI() { return fisherAI; }
    public PlacingAI getPlacingAI() { return placingAI; }
    public PatrolAI getPatrolAI() { return patrolAI; }
    public ChestAI getChestAI() { return chestAI; }
    public RestockAI getRestockAI() { return restockAI; }

    /** Called once per server tick from SquireEntity.aiStep. */
    public void tick() {
        // Threat scan runs regardless of active AI — it POPULATES the table, which is
        // what lets GuardAI activate. If we only scanned when GuardAI was active, we'd
        // never enter combat from idle.
        scanCounter++;
        if (scanCounter >= THREAT_SCAN_INTERVAL_TICKS) {
            scanCounter = 0;
            SquireThreatScanner.scan(squire);
        }

        autoActionCounter++;
        if (autoActionCounter >= AUTO_ACTION_CHECK_INTERVAL_TICKS) {
            autoActionCounter = 0;
            maybeAutoAction();
        }

        swapCounter++;
        if (swapCounter >= SWAP_CHECK_INTERVAL_TICKS) {
            swapCounter = 0;
            selectJob();
        }
        activeJob.tick();
    }

    /**
     * v4.0.3 / v4.0.4 — between-tasks auto-action. When the squire is passive (no active
     * work, no combat) AND has a remembered deposit chest, dispatch one of:
     * <ol>
     *   <li><b>Auto-deposit</b> (v4.0.4) — if ≥{@value #AUTO_DEPOSIT_FILL_RATIO} of backpack
     *       slots hold surplus (non-gear) items, assign ChestAI to empty them.</li>
     *   <li><b>Auto-restock</b> (v4.0.3) — if any gear category is missing entirely, assign
     *       RestockAI to pull a replacement.</li>
     * </ol>
     *
     * <p>Deposit fires before restock on purpose: if both conditions hold (full backpack AND
     * broken tool), we'd rather dump surplus first so the restocked tool has backpack space
     * to land in. Worst case two trips; same-chest means it's still fast.
     *
     * <p>Short-circuits cheaply when any precondition fails: no remembered chest, an
     * auto-action already in flight, a work AI owning the squire, or GuardAI active.
     */
    private void maybeAutoAction() {
        if (chestAI.hasWork() || restockAI.hasWork()) return;  // auto-action already in flight
        if (activeJob == guardAI) return;                      // combat owns the squire
        if (minerAI.hasWork() || lumberjackAI.hasWork() || farmerAI.hasWork()
                || fisherAI.hasWork() || placingAI.hasWork() || patrolAI.hasWork()) {
            return;                                            // work AI owns the squire
        }
        var chest = squire.getLastDepositChest();
        if (chest == null) return;                             // no memory of any chest

        SquireItemHandler backpack = squire.getItemHandler();
        if (backpack == null) return;

        // Priority 1: deposit surplus if the squire is holding enough junk to be worth the trip.
        if (surplusFillRatio(backpack) >= AUTO_DEPOSIT_FILL_RATIO) {
            chestAI.setTarget(chest);
            return;
        }

        // Priority 2: restock any missing gear category.
        EnumSet<GearCategorizer.Category> missing = GearCategorizer.missingCategories(squire, backpack);
        if (!missing.isEmpty()) {
            restockAI.setTarget(chest);
        }
    }

    /**
     * Fraction of backpack slots holding surplus (non-gear) items. "Gear" per
     * {@link GearCategorizer#classify} is preserved by keep-best and does not count toward
     * the fill ratio — we measure how much stuff the squire has accumulated that belongs
     * in a chest, not how full the backpack looks.
     *
     * <p>Returns 0.0 for an empty backpack, 1.0 for a backpack where every slot holds a
     * non-gear stack. A squire carrying only tools + armor reports 0.0 and never triggers
     * auto-deposit, which is what we want — nothing there is surplus.
     */
    private static double surplusFillRatio(SquireItemHandler backpack) {
        int backpackSize = backpack.getSlots() - SquireItemHandler.EQUIPMENT_SLOTS;
        if (backpackSize <= 0) return 0.0;
        int surplus = 0;
        for (int i = SquireItemHandler.EQUIPMENT_SLOTS; i < backpack.getSlots(); i++) {
            ItemStack s = backpack.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (GearCategorizer.classify(s) != null) continue;   // gear — preserved by keep-best
            surplus++;
        }
        return (double) surplus / backpackSize;
    }

    /**
     * Decide which job AI should be active given the squire's current context.
     * Priority (high to low): Guard (threat present) → Follow (owner far, mode FOLLOW) → Idle.
     *
     * Fires chat lines on meaningful transitions (idle/follow/work → Guard = COMBAT_START,
     * any → idle after task completion = IDLE). Each uses {@link com.sjviklabs.squire.entity.ChatHandler#sendLineWithCooldown}
     * so a flapping state machine can't spam chat — the v3.x scream-loop defense stays wired
     * in even though the bug class that caused it is structurally gone.
     */
    private void selectJob() {
        JobAI next = chooseJob();
        if (next != activeJob) {
            JobAI previous = activeJob;
            activeJob = next;
            fireTransitionChat(previous, next);
        }
    }

    /** Publish chat lines for player-visible job transitions. Cooldowns prevent spam. */
    private void fireTransitionChat(JobAI previous, JobAI next) {
        var owner = squire.getOwner();
        if (owner == null) return;

        // Combat entry: from ANY non-combat AI → GuardAI.
        if (next == guardAI && previous != guardAI) {
            com.sjviklabs.squire.entity.ChatHandler.sendLineWithCooldown(
                    squire, owner,
                    com.sjviklabs.squire.entity.ChatHandler.ChatEvent.COMBAT_START,
                    40L);  // 2 s cooldown — defense in depth
            return;
        }

        // Work completion: from any work AI → IDLE/Follow (i.e., controller chose Idle or Follow
        // because the work AI's queue drained). One "task complete" line per drain, cooldown 60t.
        boolean previousWasWork = previous == minerAI || previous == lumberjackAI
                || previous == farmerAI || previous == fisherAI || previous == placingAI
                || previous == patrolAI;
        boolean nextIsPassive = next == idleAI || next == followOwnerAI;
        if (previousWasWork && nextIsPassive) {
            com.sjviklabs.squire.entity.ChatHandler.sendLineWithCooldown(
                    squire, owner,
                    com.sjviklabs.squire.entity.ChatHandler.ChatEvent.IDLE,
                    60L);
        }
    }

    private JobAI chooseJob() {
        // Priority 1: combat. If ThreatTable has a target, nothing else matters —
        // GuardAI owns the squire until the threat is cleared.
        if (squire.getThreatTable().getTargetMob() != null) {
            return guardAI;
        }
        // Priority 2: work AIs with pending assignments. All are interruptible by combat
        // above. Between them, first-hasWork-wins — assigning a new task to a different
        // AI while one is active effectively switches the squire to the new one (the old
        // AI's queue/assignment stays; whichever gets re-checked first wins, but since
        // hasWork checks are cheap, in practice the order here matches player intent).
        if (minerAI.hasWork())      return minerAI;
        if (lumberjackAI.hasWork()) return lumberjackAI;
        if (farmerAI.hasWork())     return farmerAI;
        if (fisherAI.hasWork())     return fisherAI;
        if (placingAI.hasWork())    return placingAI;
        if (patrolAI.hasWork())     return patrolAI;
        if (chestAI.hasWork())      return chestAI;
        // Priority 2.5: restock (v4.0.3). Between work and follow — a squire missing a
        // pickaxe should walk to its chest before resuming passive follow, but an active
        // mine/chop/farm assignment wins.
        if (restockAI.hasWork())    return restockAI;
        // Priority 3: follow. Active only in MODE_FOLLOW; sit/guard modes anchor the squire.
        if (squire.getOwner() != null
                && squire.getSquireMode() == SquireEntity.MODE_FOLLOW) {
            return followOwnerAI;
        }
        // Priority 4: idle (default fallback).
        return idleAI;
    }

    public JobAI getActiveJob() {
        return activeJob;
    }

    public SquireAIState getCurrentState() {
        return activeJob.getCurrentState();
    }
}
