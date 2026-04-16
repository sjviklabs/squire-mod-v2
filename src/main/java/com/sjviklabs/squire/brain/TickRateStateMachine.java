package com.sjviklabs.squire.brain;

import com.sjviklabs.squire.entity.SquireEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tick-rate-gated state machine inspired by MineColonies' TickRateStateMachine.
 *
 * Each transition has its own countdown timer. Only transitions whose countdown
 * reaches zero are evaluated on a given tick. This allows expensive checks
 * (item scans, entity searches) to run infrequently while critical checks
 * (combat, drowning) run every tick.
 *
 * Evaluation order per tick:
 * 1. Global transitions (sourceState == null), sorted by priority
 * 2. Current-state transitions, sorted by priority
 * 3. First matching transition (condition returns true) fires
 */
public class TickRateStateMachine implements StateController {

    private SquireAIState currentState = SquireAIState.IDLE;
    private final List<AITransition> transitions = new ArrayList<>();
    private int[] countdowns = new int[0];

    // Pre-computed indices into transitions[] for global transitions, sorted by priority
    private int[] globalIndices = new int[0];
    private boolean dirty = true;

    public void addTransition(AITransition transition) {
        transitions.add(transition);
        dirty = true;
    }

    public SquireAIState getCurrentState() {
        return currentState;
    }

    /**
     * Force the machine into a specific state immediately.
     * Used by debug commands (/squire mine, /squire place) to override normal transitions,
     * and by WorkHandlers via the StateController narrow interface to transition
     * between their own sub-states without coupling to the full FSM.
     */
    public void forceState(SquireAIState state) {
        this.currentState = state;
    }

    /**
     * Called once per server tick from SquireBrain.tick().
     * Decrements all countdowns, evaluates eligible transitions, fires the first match.
     */
    public void tick(SquireEntity squire) {
        if (dirty) {
            rebuild();
        }

        // Decrement all countdowns
        for (int i = 0; i < countdowns.length; i++) {
            countdowns[i]--;
        }

        // Evaluate global transitions first (highest priority interrupts)
        SquireAIState nextState = evaluateGlobals(squire);

        // If no global transition fired, evaluate current-state transitions
        if (nextState == null) {
            nextState = evaluateCurrentState(squire);
        }

        // Transition if a match was found
        if (nextState != null && nextState != currentState) {
            var log = squire.getActivityLog();
            if (log != null) {
                log.log("STATE", currentState.name() + " -> " + nextState.name());
            }
            currentState = nextState;
        }
    }

    private SquireAIState evaluateGlobals(SquireEntity squire) {
        for (int idx : globalIndices) {
            if (countdowns[idx] > 0) continue;

            // Reset countdown regardless of condition result
            countdowns[idx] = transitions.get(idx).tickRate();

            AITransition t = transitions.get(idx);
            if (t.condition().getAsBoolean()) {
                return t.action().apply(squire);
            }
        }
        return null;
    }

    private SquireAIState evaluateCurrentState(SquireEntity squire) {
        for (int i = 0; i < transitions.size(); i++) {
            AITransition t = transitions.get(i);
            if (t.isGlobal()) continue;
            if (!t.appliesTo(currentState)) continue;
            if (countdowns[i] > 0) continue;

            // Reset countdown
            countdowns[i] = t.tickRate();

            if (t.condition().getAsBoolean()) {
                return t.action().apply(squire);
            }
        }
        return null;
    }

    /**
     * Rebuild internal data structures after transitions are added.
     */
    private void rebuild() {
        // Sort transitions: globals first, then by priority
        transitions.sort(Comparator.<AITransition, Boolean>comparing(AITransition::isGlobal).reversed()
                .thenComparingInt(AITransition::priority));

        // Build countdown array
        countdowns = new int[transitions.size()];
        for (int i = 0; i < countdowns.length; i++) {
            countdowns[i] = 1; // All transitions eligible on first tick
        }

        // Pre-compute global transition indices (already sorted by priority)
        List<Integer> globals = new ArrayList<>();
        for (int i = 0; i < transitions.size(); i++) {
            if (transitions.get(i).isGlobal()) {
                globals.add(i);
            }
        }
        globalIndices = globals.stream().mapToInt(Integer::intValue).toArray();

        dirty = false;
    }

    /** Number of registered transitions. */
    public int transitionCount() {
        return transitions.size();
    }
}
