package com.sjviklabs.squire.ai;

import com.sjviklabs.squire.ai.job.FollowOwnerAI;
import com.sjviklabs.squire.ai.job.GuardAI;
import com.sjviklabs.squire.ai.job.IdleAI;
import com.sjviklabs.squire.ai.job.JobAI;
import com.sjviklabs.squire.ai.job.LumberjackAI;
import com.sjviklabs.squire.ai.job.MinerAI;
import com.sjviklabs.squire.ai.state.SquireAIState;
import com.sjviklabs.squire.ai.threat.SquireThreatScanner;
import com.sjviklabs.squire.entity.SquireEntity;

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

    private final SquireEntity squire;

    // All job AIs are instantiated eagerly and reused — they're stateless apart from
    // their internal state machines, which are cheap to hold.
    private final JobAI idleAI;
    private final JobAI followOwnerAI;
    private final JobAI guardAI;
    private final MinerAI minerAI;
    private final LumberjackAI lumberjackAI;

    private JobAI activeJob;
    private int swapCounter = 0;
    private int scanCounter = 0;

    public SquireAIController(SquireEntity squire) {
        this.squire = squire;
        this.idleAI = new IdleAI(squire);
        this.followOwnerAI = new FollowOwnerAI(squire);
        this.guardAI = new GuardAI(squire);
        this.minerAI = new MinerAI(squire);
        this.lumberjackAI = new LumberjackAI(squire);
        this.activeJob = idleAI;
    }

    /** Accessors for work-AI intake from commands. */
    public MinerAI getMinerAI() { return minerAI; }
    public LumberjackAI getLumberjackAI() { return lumberjackAI; }

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

        swapCounter++;
        if (swapCounter >= SWAP_CHECK_INTERVAL_TICKS) {
            swapCounter = 0;
            selectJob();
        }
        activeJob.tick();
    }

    /**
     * Decide which job AI should be active given the squire's current context.
     * Priority (high to low): Guard (threat present) → Follow (owner far, mode FOLLOW) → Idle.
     */
    private void selectJob() {
        JobAI next = chooseJob();
        if (next != activeJob) {
            activeJob = next;
        }
    }

    private JobAI chooseJob() {
        // Priority 1: combat. If ThreatTable has a target, nothing else matters —
        // GuardAI owns the squire until the threat is cleared.
        if (squire.getThreatTable().getTargetMob() != null) {
            return guardAI;
        }
        // Priority 2: work AIs with pending assignments. Block-breaking work (Miner,
        // Lumberjack) is interruptible by combat (priority 1 above). Between them,
        // whichever was assigned most recently wins — `hasWork` returns true only while
        // their queue is non-empty. Both queues drain themselves back to IDLE.
        if (minerAI.hasWork()) return minerAI;
        if (lumberjackAI.hasWork()) return lumberjackAI;
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
