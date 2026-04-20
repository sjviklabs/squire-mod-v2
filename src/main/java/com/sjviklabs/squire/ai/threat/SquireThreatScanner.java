package com.sjviklabs.squire.ai.threat;

import com.minecolonies.api.entity.ai.combat.threat.ThreatTable;
import com.sjviklabs.squire.compat.MineColoniesCompat;
import com.sjviklabs.squire.data.SquireTagKeys;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Periodic radius scan that populates the squire's ThreatTable with nearby hostile mobs.
 *
 * v4.0.0 replaces vanilla {@code NearestAttackableTargetGoal} — that goal was the bug source
 * in v3.x (it set {@code squire.getTarget()} on its own cadence, racing the FSM's clear calls).
 * The scanner writes to ThreatTable; ThreatTable owns the target. No vanilla goal in the mix.
 *
 * Scan is deliberately cheap: 16-block AABB, one pass per tick-rate, no pathfinding or LOS.
 * Distance-weighted and damage-taken scoring happens inside ThreatTable.addThreat; we just
 * tell it "saw this entity, baseline threat = N" and let it rank.
 *
 * Filters (identical to v3.x NearestAttackableTargetGoal<Monster> behavior):
 * - Must be {@link Monster} subtype (vanilla hostile classification)
 * - Not in the {@code squire:do_not_attack} tag (mod-author opt-out for passive mobs
 *   mis-typed as Monster — e.g. piglins when not aggravated)
 * - Not a MineColonies citizen (via {@link MineColoniesCompat#isFriendly})
 * - Must be alive
 */
public final class SquireThreatScanner {

    private static final double SCAN_RANGE = 16.0;
    private static final int BASE_THREAT_FROM_SIGHT = 1;

    /** Pure stateless utility — one method. No internal state. */
    private SquireThreatScanner() {}

    /**
     * Scan for hostile mobs within SCAN_RANGE of the squire and register each with the
     * ThreatTable. Called from {@link com.sjviklabs.squire.ai.SquireAIController} every 10 ticks.
     * Cheap — no pathing, no LOS, just an AABB query and simple predicates.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void scan(SquireEntity squire) {
        if (squire.level().isClientSide) return;

        ThreatTable table = squire.getThreatTable();
        AABB box = squire.getBoundingBox().inflate(SCAN_RANGE);
        List<Monster> hostiles = squire.level().getEntitiesOfClass(
                Monster.class, box,
                mob -> mob.isAlive()
                        && !mob.getType().is(SquireTagKeys.DO_NOT_ATTACK)
                        && !MineColoniesCompat.isFriendly(mob)
        );

        for (Monster mob : hostiles) {
            table.addThreat(mob, BASE_THREAT_FROM_SIGHT);
        }
    }
}
