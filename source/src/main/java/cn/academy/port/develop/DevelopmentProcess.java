/* Adapted from AcademyCraft 1.0.7 DevelopData. See NOTICE. */
package cn.academy.port.develop;

import java.util.Objects;
import java.util.function.Consumer;

/** Pure server-side state machine. It owns no player, inventory, transport, or infinite battery. */
public final class DevelopmentProcess {
    public enum State { IDLE, FAILED, DEVELOPING, DONE }

    public interface Developer {
        DeveloperType type();
        /** Must pull real energy; the source portable implementation drains a partial remainder on failure. */
        boolean tryPullEnergy(double amount);
        double energy();
        double maxEnergy();
    }

    public interface Action {
        String id();
        int stimulations();
        /** Evaluated at completion, against current player/inventory state. */
        boolean validate(Developer developer);
        void complete();
    }

    public record Snapshot(State state, DeveloperType developerType, String action,
                           int stimulation, int maxStimulations, int tickThisStimulation,
                           double energy, double maxEnergy) {
        public boolean developing() { return developerType != null; }
        /** Preserves the original DevelopData formula, including its missing per-stimulation divisor. */
        public double sourceProgress() {
            return !developing() ? 0 : (double) stimulation / maxStimulations
                    + (double) tickThisStimulation / developerType.tps;
        }
        /** Explicit modern display adaptation; the source accessor is also retained. */
        public double normalizedProgress() {
            return !developing() ? 0 : Math.min(1, (stimulation
                    + (double) tickThisStimulation / developerType.ticksPerStimulation()) / maxStimulations);
        }
    }

    private Developer developer;
    private Action action;
    private int stimulation, maxStimulations, tickThisStimulation, tickSync;
    private boolean dirty;
    private State state = State.IDLE;

    /** Like startDeveloping, overrides an active action and does not refund already spent energy. */
    public void start(Developer nextDeveloper, Action nextAction) {
        Objects.requireNonNull(nextDeveloper, "developer");
        Objects.requireNonNull(nextAction, "action");
        int count = nextAction.stimulations();
        if (count <= 0) throw new IllegalArgumentException("stimulations must be positive");
        resetProgress(false);
        developer = nextDeveloper;
        action = nextAction;
        state = State.DEVELOPING;
        maxStimulations = count;
        dirty = true;
    }

    public boolean isDeveloping() { return developer != null; }
    public State state() { return state; }
    public Action action() { return action; }
    public int stimulation() { return stimulation; }
    public int maxStimulations() { return maxStimulations; }
    public int tickThisStimulation() { return tickThisStimulation; }
    public double getDevelopProgress() { return snapshot().sourceProgress(); }

    public Snapshot snapshot() {
        return new Snapshot(state, developer == null ? null : developer.type(),
                action == null ? "" : action.id(), stimulation, maxStimulations, tickThisStimulation,
                developer == null ? 0 : developer.energy(), developer == null ? 0 : developer.maxEnergy());
    }

    public void abort() { if (state == State.DEVELOPING) resetProgress(true); }
    public void reset() { resetProgress(false); }

    private void resetProgress(boolean failed) {
        developer = null;
        action = null;
        tickSync = 5;
        stimulation = maxStimulations = tickThisStimulation = 0;
        state = failed ? State.FAILED : State.IDLE;
        dirty = true;
    }

    /** Original sync-before-consume ordering, with dirty sync plus periodic tickSync-- == 0. */
    public void tick(Consumer<Snapshot> sync) {
        Objects.requireNonNull(sync, "sync");
        if (dirty) {
            dirty = false;
            sync.accept(snapshot());
        }
        if (!isDeveloping()) return;
        DeveloperType type = developer.type();
        if (tickSync-- == 0) {
            tickSync = 5;
            sync.accept(snapshot());
        }
        if (!developer.tryPullEnergy(type.energyPerTick())) {
            resetProgress(true);
            return;
        }
        // Deliberately preserve ++tick > TPS, not >= TPS. Every stimulation costs TPS+1 ticks.
        if (++tickThisStimulation > type.tps) {
            tickThisStimulation = 0;
            ++stimulation;
            if (stimulation >= maxStimulations) {
                boolean success = action.validate(developer);
                if (success) action.complete();
                resetProgress(!success);
                if (success) state = State.DONE;
            }
        }
    }

    public void tick() { tick(ignored -> {}); }
}
