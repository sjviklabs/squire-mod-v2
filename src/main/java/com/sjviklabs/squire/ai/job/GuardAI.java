package com.sjviklabs.squire.ai.job;

import com.minecolonies.api.entity.ai.combat.threat.ThreatTable;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateStateMachine;
import com.sjviklabs.squire.ai.state.SquireAIState;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Combat job AI: engage whoever's at the top of the threat table until they're dead or gone.
 *
 * v4.0.0 replacement for the v3.x {@code CombatHandler} + the global combat-enter FSM
 * transition that caused the scream-loop. Target is ALWAYS read from the ThreatTable —
 * never from {@code squire.getTarget()}. No vanilla target goals, no custom setTarget(null)
 * paths scattered across multiple files. One authority.
 *
 * State tree:
 *   IDLE     (no threat) — checked every 10 ticks
 *     └─ threatTable.getTargetMob() != null ──► FIGHTING
 *   FIGHTING (actively engaging)
 *     ├─ per-tick: approach + swing when in range
 *     ├─ target dead/null ──► clean up + IDLE (controller will swap back to Idle/Follow)
 *     └─ this AI does NOT exit on leash/range-breach (Phase 3 scope) — v3.x had that
 *        check and it created the race. If the target is far, GuardAI just keeps chasing.
 *        HP-based flee + leash disengage come in a later phase once combat is stable.
 *
 * Attack logic:
 * - Move toward target with squire.getNavigation().moveTo
 * - When in melee reach (distSq ≤ reach²), swing main hand and call doHurtTarget
 * - Cooldown = 20/attackSpeed ticks between swings
 */
public final class GuardAI implements JobAI {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuardAI.class);
    private static final double WALK_SPEED = 1.2;

    private final SquireEntity squire;
    private final TickRateStateMachine<SquireAIState> machine;

    /** Ticks until next melee swing is ready. Decrements in the FIGHTING per-tick transition. */
    private int attackCooldownTicks;

    public GuardAI(SquireEntity squire) {
        this.squire = squire;
        this.machine = new TickRateStateMachine<>(
                SquireAIState.IDLE,
                ex -> LOGGER.error("[GuardAI] state machine error", ex)
        );

        // IDLE → FIGHTING when threat table has a target.
        machine.addTransition(new AITarget<>(
                SquireAIState.IDLE,
                this::hasTarget,
                () -> {
                    attackCooldownTicks = 0; // first swing ready
                    return SquireAIState.FIGHTING;
                },
                10
        ));

        // FIGHTING → IDLE when target dies or goes invalid.
        machine.addTransition(new AITarget<>(
                SquireAIState.FIGHTING,
                () -> !hasTarget(),
                () -> {
                    ThreatTable<?> table = squire.getThreatTable();
                    table.removeCurrentTarget();
                    squire.getNavigation().stop();
                    return SquireAIState.IDLE;
                },
                5
        ));

        // FIGHTING per-tick: approach + swing.
        machine.addTransition(new AITarget<>(
                SquireAIState.FIGHTING,
                () -> true,
                this::tickFighting,
                1
        ));
    }

    private boolean hasTarget() {
        LivingEntity target = squire.getThreatTable().getTargetMob();
        return target != null && target.isAlive();
    }

    private SquireAIState tickFighting() {
        LivingEntity target = squire.getThreatTable().getTargetMob();
        if (target == null || !target.isAlive()) {
            // Condition guard will fire next cycle; just bail for now.
            return SquireAIState.FIGHTING;
        }

        squire.getLookControl().setLookAt(target, 30.0F, 30.0F);
        squire.getNavigation().moveTo(target, WALK_SPEED);

        if (attackCooldownTicks > 0) {
            attackCooldownTicks--;
        }

        double distSq = squire.distanceToSqr(target);
        double reach = getMeleeReachSq(target);
        if (distSq <= reach && attackCooldownTicks <= 0) {
            squire.swing(InteractionHand.MAIN_HAND);
            squire.doHurtTarget(target);
            double attackSpeed = squire.getAttributeValue(Attributes.ATTACK_SPEED);
            attackCooldownTicks = (int) Math.ceil(20.0 / Math.max(0.5, Math.min(4.0, attackSpeed)));
        }

        return SquireAIState.FIGHTING;
    }

    /**
     * Effective melee reach squared. Mirrors the v3.x CombatHandler.getAttackReachSq
     * formula — baseline 1.5 plus entity width factor.
     */
    private double getMeleeReachSq(LivingEntity target) {
        double base = 1.5;
        double width = squire.getBbWidth();
        double vanilla = Math.sqrt(width * 2.0 * width * 2.0 + target.getBbWidth());
        double reach = Math.max(base, vanilla);
        return reach * reach;
    }

    @Override
    public void tick() {
        machine.tick();
    }

    @Override
    public SquireAIState getCurrentState() {
        return machine.getState();
    }
}
