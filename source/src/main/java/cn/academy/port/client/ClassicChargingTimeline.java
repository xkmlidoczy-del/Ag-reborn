/* AcademyCraft 1.0.7 EntityArc/SubArc/CurrentCharging adaptation. See NOTICE. */
package cn.academy.port.client;

import java.util.LinkedHashMap;
import java.util.Map;

/** Dependency-free source parameters and state decisions, also used by headless regression. */
public final class ClassicChargingTimeline {
    public static final double RANGE = 15;
    public static final int BEAM_TEMPLATES = 20, BEAM_PASSES = 5;
    public static final double BEAM_LENGTH = 20, BEAM_WIDTH = .1, BEAM_MAX_OFFSET = 1.2;
    public static final double BEAM_BRANCH = .3, BEAM_WIDTH_SHRINK = .7;
    public static final double BEAM_SHOW_WIGGLE = .2, BEAM_HIDE_WIGGLE = .8, BEAM_TEX_WIGGLE = .8;
    public static final int SURROUND_TEMPLATES = 10, SURROUND_PASSES = 3;
    public static final int THIN_COUNT = 4, NORMAL_COUNT = 6, SUBARC_LIFE = 30;
    public static final double SURROUND_OFFSET = .8, SURROUND_BRANCH = .7, SURROUND_WIDTH_SHRINK = .9;
    public static final double SURROUND_SCALE = .3, ITEM_SIZE_MULTIPLIER = 1.3;
    public static final double SUBARC_FRAME_RATE = .6, SUBARC_SWITCH_RATE = .7;
    public static final float LOOP_VOLUME = .3F;

    private ClassicChargingTimeline() {}

    /** EntityArc tests showWiggle when shown and hideWiggle when hidden. */
    public static boolean beamShown(boolean shown, double sample) {
        return sample < (shown ? BEAM_SHOW_WIGGLE : BEAM_HIDE_WIGGLE) ? !shown : shown;
    }

    public static boolean subArcShown(boolean shown, double sample) {
        return sample < (shown ? .4 : .3) * SUBARC_SWITCH_RATE ? !shown : shown;
    }

    public static int subArcAge(int age, double sample) { return sample < .9 ? age + 1 : age; }

    /** This visibility test is deliberately the same in item and block modes. */
    public static boolean surroundShown(boolean supportedBlock) { return supportedBlock; }

    public static boolean matchingEnd(long activeToken, long receivedToken) {
        return activeToken > 0 && activeToken == receivedToken;
    }

    /**
     * Server tokens are positive and process-global increasing. Tombstones suppress replayed
     * starts after release or entity loss. Clear on session changes; no hold-duration timeout.
     * A bounded history limits memory under long sessions with many unrelated entity IDs.
     */
    public static final class Tokens {
        private static final int MAX_HISTORY = 1024;
        private final Map<Integer, Long> latest = new LinkedHashMap<>();

        public boolean acceptStart(int entity, long token) {
            if (token <= 0 || token <= latest.getOrDefault(entity, 0L)) return false;
            remember(entity, token);
            return true;
        }

        public void rememberEnd(int entity, long token) {
            if (token > latest.getOrDefault(entity, 0L)) remember(entity, token);
        }

        private void remember(int entity, long token) {
            latest.remove(entity);
            latest.put(entity, token);
            if (latest.size() > MAX_HISTORY) latest.remove(latest.keySet().iterator().next());
        }

        public void clear() { latest.clear(); }
    }
}
