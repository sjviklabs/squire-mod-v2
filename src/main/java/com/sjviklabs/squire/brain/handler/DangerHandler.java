package com.sjviklabs.squire.brain.handler;

import com.sjviklabs.squire.data.SquireTagKeys;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;

/**
 * Detects explosive threats (creepers swelling, primed TNT) and triggers flee.
 * Called from CombatHandler.tick() for entities in the EXPLOSIVE_THREAT tag.
 *
 * Kept lightweight: one concern only -- detect imminent explosion and signal flee.
 * Expansion point: when more explosive threat types are added (TNT minecarts,
 * wither skulls), add checks here rather than in CombatHandler.tickExplosive().
 */
public class DangerHandler {

    private final SquireEntity squire;

    public DangerHandler(SquireEntity squire) {
        this.squire = squire;
    }

    /**
     * Returns true if the target is an imminent explosive threat the squire should flee immediately.
     * Called by CombatHandler when tactic == EXPLOSIVE.
     *
     * Creeper-specific: flee when swellDir > 0 (charging/swelling).
     * Other EXPLOSIVE_THREAT entities: treat as immediate if within 4 blocks.
     */
    public boolean isImmediateThreat(LivingEntity target) {
        if (target instanceof Creeper creeper) {
            return creeper.getSwellDir() > 0; // swellDir > 0 means actively charging
        }
        // Other EXPLOSIVE_THREAT entities: treat as immediate if within 4 blocks
        return squire.distanceTo(target) < 4.0;
    }

    /**
     * Tick called from CombatHandler when tactic == EXPLOSIVE.
     * Triggers flee on the combatHandler if the threat is imminent;
     * otherwise CombatHandler.tickExplosive() handles ranged-preferred melee fallback.
     */
    public void tick(LivingEntity target, CombatHandler combatHandler) {
        if (isImmediateThreat(target)) {
            combatHandler.startFlee();
        }
    }
}
