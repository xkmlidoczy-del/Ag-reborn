/* AcademyCraft 1.0.7 Groundshock/SmokeEffect adaptation. Copyright Lambda Innovation; GPLv3. See NOTICE. */
package cn.academy.port.client;

import java.util.LinkedHashMap;
import java.util.Map;

/** Dependency-free classic parameters and lifecycle decisions shared with the actual adapter. */
public final class ClassicGroundShockTimeline {
    public static final int MAX_BLOCKS = 125;
    public static final int MIN_DIGGING = 4, MAX_DIGGING_EXCLUSIVE = 8;
    public static final int SLASH_TICKS = 4;
    public static final float SLASH_PITCH = 3.4F, SOUND_VOLUME = 2F;
    public static final long SMOKE_LIFE_MS = 4_000;
    public static final double SMOKE_SIZE = 1, SMOKE_CHANCE = .5;
    public static final double SMOKE_LIFE_MIN = .5, SMOKE_LIFE_MAX_EXCLUSIVE = .7;

    private ClassicGroundShockTimeline() {}

    /** localTick increments before evaluating this source curve. There is no hold expiry. */
    public static float liftPitch(int localTick) {
        if (localTick <= 0) return 0;
        float delta = localTick < 4 ? localTick / 4F
                : localTick <= 20 ? 1F : localTick <= 25 ? 1F - (localTick - 20) / 5F : 0;
        return -.2F * delta;
    }

    public static float slashPitch(int slashTick) {
        return slashTick >= 1 && slashTick <= SLASH_TICKS ? SLASH_PITCH : 0;
    }

    /** Smoke is invisible after 2*modifier seconds but remains alive until four seconds. */
    public static double smokeAlpha(double ageMillis, double lifeModifier) {
        if (!Double.isFinite(ageMillis) || !Double.isFinite(lifeModifier) || lifeModifier <= 0) return 0;
        double time = Math.max(0, ageMillis) / 1_000 / lifeModifier;
        if (time <= .3) return time / .3;
        if (time <= 1.5) return 1;
        if (time <= 2) return 1 - (time - 1.5) / .5;
        return 0;
    }

    public static boolean smokeAlive(long ageMillis) { return ageMillis >= 0 && ageMillis < SMOKE_LIFE_MS; }
    public static double smokeU(int frame) { return (frame & 1) / 2.0; }
    public static double smokeV(int frame) { return (frame >> 1 & 1) / 2.0; }
    public static boolean validBlockArrayLength(int length) { return length >= 0 && length % 3 == 0 && length <= MAX_BLOCKS * 3; }
    public static boolean matchingToken(long active, long received) { return active > 0 && active == received; }

    /** The same tested counter drives the live local gesture; binding an acknowledgement preserves its age. */
    public static final class Uplift {
        private long token;
        private int ticks;
        public Uplift(long token) { this.token = token; }
        public long token() { return token; }
        public int ticks() { return ticks; }
        public void bind(long acceptedToken) {
            if (token == 0 && acceptedToken > 0) token = acceptedToken;
        }
        public float tick() {
            // The source keeps its context indefinitely after the curve; never wrap back to tick 1.
            if (ticks < 26) ticks++;
            return liftPitch(ticks);
        }
    }

    /**
     * Positive, process-increasing tokens suppress duplicate/reordered starts and performances.
     * A perform may be an observer's first packet. Abort-before-start creates a tombstone.
     * A hold can run indefinitely; bounding this history does not impose a context lifetime.
     */
    public static final class Tokens {
        private static final int MAX_HISTORY = 1024;
        private enum Phase { HOLD, PERFORM, ABORT }
        private record Entry(long token, Phase phase) {}
        private final Map<Integer, Entry> latest = new LinkedHashMap<>();

        public boolean acceptStart(int entity, long token) {
            Entry previous = latest.get(entity);
            if (token <= 0 || previous != null && token <= previous.token) return false;
            remember(entity, new Entry(token, Phase.HOLD));
            return true;
        }

        public boolean acceptPerform(int entity, long token) {
            Entry previous = latest.get(entity);
            if (token <= 0 || previous != null && (token < previous.token
                    || token == previous.token && previous.phase != Phase.HOLD)) return false;
            remember(entity, new Entry(token, Phase.PERFORM));
            return true;
        }

        public void rememberAbort(int entity, long token) {
            if (token <= 0) return;
            Entry previous = latest.get(entity);
            // A cancelled hold cannot undo a successfully committed independent slash.
            if (previous == null || token > previous.token || token == previous.token && previous.phase == Phase.HOLD)
                remember(entity, new Entry(token, Phase.ABORT));
        }

        private void remember(int entity, Entry entry) {
            latest.remove(entity);
            latest.put(entity, entry);
            if (latest.size() > MAX_HISTORY) latest.remove(latest.keySet().iterator().next());
        }
        public void clear() { latest.clear(); }
    }

    /** Pause-aware real-time SmokeEffect alpha/lifetime, while velocity remains tick-based. */
    public static final class PauseClock {
        private long wall, elapsed;
        private boolean initialized, wasRunning;
        public long update(long now, boolean running) {
            if (initialized && running && wasRunning) elapsed += Math.max(0, now - wall);
            wall = now;
            wasRunning = running;
            initialized = true;
            return elapsed;
        }
        public long elapsed() { return elapsed; }
        public void clear() { wall = elapsed = 0; initialized = wasRunning = false; }
    }
}
