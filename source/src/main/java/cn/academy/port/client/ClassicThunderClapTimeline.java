/* AcademyCraft 1.0.7 ThunderClap/EntitySurroundArc/RippleMark adaptation. GPLv3; see NOTICE. */
package cn.academy.port.client;

import java.util.LinkedHashMap;
import java.util.Map;

/** Dependency-free source parameters and lifecycle decisions shared with the actual adapter. */
public final class ClassicThunderClapTimeline {
    public static final double RANGE = 40;
    public static final int CHARGE_TICKS = 60, END_LINGER_TICKS = 10, SURROUND_LIFE_TICKS = 100;
    public static final float NORMAL_WALK_SPEED = .1F, MIN_WALK_SPEED = .001F;
    public static final int BOLD_COUNT = 5, BOLD_TEMPLATES = 10, BOLD_PASSES = 3;
    public static final double BOLD_LENGTH_FROM = 3.5, BOLD_LENGTH_TO_EXCLUSIVE = 4.5;
    public static final double BOLD_WIDTH = .35, BOLD_OFFSET = 1.2, BOLD_BRANCH = .45;
    public static final double BOLD_WIDTH_SHRINK = .9, SURROUND_SCALE = .3, ENTITY_SIZE_MULTIPLIER = 1.3;
    public static final int SUBARC_LIFE = 30;
    public static final double SUBARC_FRAME_RATE = .6, SUBARC_SWITCH_RATE = .7;
    public static final long RIPPLE_CYCLE_MS = 3_600, RIPPLE_OFFSET_MS = 1_200;
    public static final int RIPPLE_LAYERS = 3;
    public static final float RIPPLE_RED = .8F, RIPPLE_GREEN = .8F, RIPPLE_BLUE = .8F, RIPPLE_ALPHA = .7F;

    private ClassicThunderClapTimeline() {}

    /** The source increments its local counter before applying this curve and clamps after 60 ticks. */
    public static float walkSpeed(int ticks) {
        return Math.max(MIN_WALK_SPEED, NORMAL_WALK_SPEED
                - (NORMAL_WALK_SPEED - MIN_WALK_SPEED) / CHARGE_TICKS * Math.max(0, ticks));
    }

    public static boolean subArcShown(boolean shown, double sample) {
        return sample < (shown ? .4 : .3) * SUBARC_SWITCH_RATE ? !shown : shown;
    }
    public static boolean replaceSubArc(double sample) { return sample < .5 * SUBARC_FRAME_RATE; }
    public static int subArcAge(int age, double sample) { return sample < .9 ? age + 1 : age; }

    /** Renderer offsets are {0,-1200,-2400}; subtraction therefore advances layers by +1200ms. */
    public static long ripplePhase(long ageMillis, int layer) {
        if (layer < 0 || layer >= RIPPLE_LAYERS) throw new IllegalArgumentException("Ripple layer must be in [0,3)");
        return (Math.floorMod(ageMillis, RIPPLE_CYCLE_MS) + layer * RIPPLE_OFFSET_MS) % RIPPLE_CYCLE_MS;
    }
    public static float rippleHeight(long phase) { return phase * 3e-4F; }
    public static float rippleSize(long phase) { return 1.9F + (1.4F - 1.9F) * ((float) phase / RIPPLE_CYCLE_MS); }
    public static float rippleFade(long phase) {
        final float fade = 1_600;
        if (phase < fade) return phase / fade;
        if (phase > RIPPLE_CYCLE_MS - fade) return 1 - (phase - (RIPPLE_CYCLE_MS - fade)) / fade;
        return 1;
    }
    public static int colorByte(float color) { return Math.max(0, Math.min(255, Math.round(color * 255))); }
    public static boolean matchingEnd(long activeToken, long receivedToken) {
        return activeToken > 0 && activeToken == receivedToken;
    }
    public static boolean matchingLocalEnd(long activeToken, long activeInput, long receivedToken, long receivedInput) {
        return matchingEnd(activeToken, receivedToken) && activeInput >= 0 && activeInput == receivedInput;
    }

    /** This does not discharge at 60: only the authoritative server end packet can terminate a hold. */
    public static final class Hold {
        private int ticks, surroundAge, endAge;
        private boolean ended;
        public int ticks() { return ticks; }
        public boolean active() { return !ended; }
        public boolean markVisible(boolean local) { return local && !ended; }
        public boolean surroundAlive() {
            return surroundAge < SURROUND_LIFE_TICKS && (!ended || endAge < END_LINGER_TICKS);
        }
        public float speed() { return ended ? NORMAL_WALK_SPEED : walkSpeed(ticks); }
        public void tick() {
            if (surroundAge < SURROUND_LIFE_TICKS) surroundAge++;
            if (ended) { if (endAge < END_LINGER_TICKS) endAge++; }
            else if (ticks < Integer.MAX_VALUE) ticks++;
        }
        /** Duplicate end packets cannot restart the ten-tick surround tail. */
        public void end() { if (!ended) { ended = true; endAge = 0; } }
    }

    /**
     * Authenticated server-echoed client nonces distinguish rapid abort/repress requests even if
     * the old acknowledgement is not received until after the new physical press. Generation
     * additionally rejects callbacks queued before cancellation. Clear never reuses a nonce.
     */
    public static final class InputGate {
        private long generation, lastNonce, expectedNonce;
        private boolean driven, held;
        public long press() {
            if (lastNonce == Long.MAX_VALUE) throw new IllegalStateException("ThunderClap input nonce exhausted");
            driven = held = true;
            generation++;
            return expectedNonce = ++lastNonce;
        }
        public void abort() { held = false; expectedNonce = 0; generation++; }
        public long capture() { return generation; }
        public boolean acceptPacket(long inputNonce) {
            return driven ? held && inputNonce > 0 && inputNonce == expectedNonce : inputNonce == 0;
        }
        public boolean acceptStart(long inputNonce, long capturedGeneration) {
            return acceptPacket(inputNonce) && (!driven || capturedGeneration == generation);
        }
        /** An internal nonce0 completion must not turn on physical-input mode. */
        public boolean end(long inputNonce) {
            if (!acceptPacket(inputNonce)) return false;
            if (driven) abort();
            else generation++;
            return true;
        }
        public void clear() { driven = held = false; expectedNonce = 0; generation++; }
    }

    /** Bounded session-local replay history; ending before receipt of start also creates a tombstone. */
    public static final class Tokens {
        private static final int MAX_HISTORY = 1_024;
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

    /** Pause-aware GameTimer replacement: ripple animation uses elapsed milliseconds, arcs use ticks. */
    public static final class PauseClock {
        private long wall, elapsed;
        private boolean initialized, wasRunning;
        public long update(long now, boolean running) {
            if (initialized && running && wasRunning) elapsed += Math.max(0, now - wall);
            wall = now; wasRunning = running; initialized = true;
            return elapsed;
        }
        public long elapsed() { return elapsed; }
        public void clear() { wall = elapsed = 0; initialized = wasRunning = false; }
    }
}
