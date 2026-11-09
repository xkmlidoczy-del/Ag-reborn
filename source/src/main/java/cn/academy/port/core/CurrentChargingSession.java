/* AcademyCraft 1.0.7 ChargingContext adaptation. Copyright Lambda Innovation, GPLv3. See NOTICE. */
package cn.academy.port.core;

import java.util.Objects;

/** Dependency-free source ordering. A target is resolved afresh for every server tick. */
public final class CurrentChargingSession {
    public static final String ID = "charging";
    public static final double RANGE = 15;
    public static final double SUPPORTED_EXPERIENCE = .0001F;
    public static final double UNSUPPORTED_EXPERIENCE = .00003F;

    public interface Target {
        /** Only item mode distinguishes an absent current hand from an unsupported item. */
        boolean present();
        /** Support classification, deliberately independent of accepted energy or canReceive. */
        boolean supported();
        /** Returns untransferred IF. Block mode asks to bypass bandwidth; FE may not permit it. */
        double charge(double imaginaryFlux, boolean ignoreBandwidth);
    }

    public enum TickResult { CONTINUE, INVALID_STATE, ITEM_REMOVED, RESOURCE_EXHAUSTED, ALREADY_ENDED }

    private final AbilityProgress state;
    private final boolean itemMode;
    private final float mastery;
    private final double overloadFloor;
    private boolean active;

    private CurrentChargingSession(AbilityProgress state, boolean itemMode, boolean creative) {
        this.state = state;
        this.itemMode = itemMode;
        mastery = (float) ClassicRules.clamp(state.exp(ID), 0, 1);
        state.consumeSkill(ID,0, startOverload(mastery), creative);
        overloadFloor = state.overload;
        // Source OverloadEvent disposes the context even when its zero-CP start succeeds.
        active = mayStart(state);
    }

    public static boolean mayStart(AbilityProgress state) {
        return state != null && "electromaster".equals(state.category) && state.level >= 1 && state.canUse(ID);
    }

    public static CurrentChargingSession begin(AbilityProgress state, boolean itemMode, boolean creative) {
        return mayStart(state) ? new CurrentChargingSession(state, itemMode, creative) : null;
    }

    /** Source lerpf uses float arithmetic and mastery is captured at context construction. */
    public static float speed(double mastery) { return lerp(15, 35, mastery); }
    public static float consumption(double mastery) { return lerp(3, 7, mastery); }
    public static float startOverload(double mastery) { return lerp(65, 48, mastery); }
    private static float lerp(float a, float b, double value) {
        float mastery = (float) ClassicRules.clamp(value, 0, 1);
        return a + mastery * (b - a);
    }

    public boolean active() { return active; }
    public boolean itemMode() { return itemMode; }
    public float mastery() { return mastery; }
    public double overloadFloor() { return overloadFloor; }
    public AbilityProgress state() { return state; }
    public void end() { active = false; }

    public TickResult tick(Target target, boolean creative) {
        Objects.requireNonNull(target);
        if (!active) return TickResult.ALREADY_ENDED;
        if (!mayStart(state)) return finish(TickResult.INVALID_STATE);
        if (state.overload < overloadFloor) state.overload = overloadFloor;
        if (itemMode) {
            if (!target.present()) return finish(TickResult.ITEM_REMOVED);
            // Source item branch spends CP BEFORE checking support, transfer or experience.
            if (!state.consumeSkill(ID,consumption(mastery), 0, creative))
                return finish(TickResult.RESOURCE_EXHAUSTED);
            boolean supported = target.supported();
            if (supported) target.charge(speed(mastery), false);
            award(supported);
        } else {
            // Source block branch charges and awards EXP BEFORE its CP check. Preserve the
            // final free transfer/EXP on the exhausted tick, including unsupported-target EXP.
            boolean supported = target.supported();
            if (supported) target.charge(speed(mastery), true);
            award(supported);
            if (!state.consumeSkill(ID,consumption(mastery), 0, creative))
                return finish(TickResult.RESOURCE_EXHAUSTED);
        }
        return TickResult.CONTINUE;
    }

    private void award(boolean supported) {
        state.addExperience(ID, supported ? SUPPORTED_EXPERIENCE : UNSUPPORTED_EXPERIENCE);
    }
    private TickResult finish(TickResult result) { active = false; return result; }
}
