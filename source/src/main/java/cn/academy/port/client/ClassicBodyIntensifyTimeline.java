/* AcademyCraft1.0.7 BodyIntensify/CurrentChargingHUD/EntityIntensifyEffect adaptation. GPLv3; see NOTICE. */
package cn.academy.port.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Dependency-free source parameters and executable HUD/token decisions. No GPU/pixel-parity claim. */
public final class ClassicBodyIntensifyTimeline {
    public static final String LOOP_SOUND = "em.intensify_loop", ACTIVATE_SOUND = "em.intensify_activate";
    public static final float SOUND_VOLUME = .5F;
    public static final String MASK_TEXTURE = "textures/effects/em_intensify_mask.png";
    public static final String ARC_PREFIX = "textures/effects/arcs/";
    public static final int HUD_TEMPLATES = 10;
    public static final long BLEND_IN_MS = 500, BLEND_OUT_MS = 200, DISPOSE_AFTER_MS = 1000;
    public static final int HELD_COUNT_FROM = 5, HELD_COUNT_TO = 7, HELD_LIFE = 233333;
    public static final double HELD_RADIUS_FROM = .84, HELD_RADIUS_TO = .96;
    public static final double HELD_SIZE_FROM = 25, HELD_SIZE_TO = 30;
    public static final int BURST_COUNT_FROM = 10, BURST_COUNT_TO = 15, BURST_LIFE = 25;
    public static final double BURST_RADIUS_FROM = .6, BURST_RADIUS_TO = 1;
    public static final double BURST_SIZE_FROM = 35, BURST_SIZE_TO = 40;
    public static final double HUD_FRAME_RATE = .3, HELD_SWITCH_RATE = 0, BURST_SWITCH_RATE = .2;
    public static final double HELD_ALPHA = .3, BURST_ALPHA = .4, BLACK_MASK_ALPHA = .1;
    public static final int WORLD_LIFE = 15, WORLD_ARC_LIFE = 3;
    public static final int WORLD_COUNT_FROM = 3, WORLD_COUNT_TO = 4;
    public static final int WORLD_TEMPLATES = 10, WORLD_PASSES = 3;
    public static final double WORLD_LENGTH_FROM = 1.5, WORLD_LENGTH_TO = 2;
    public static final double WORLD_WIDTH = .2, WORLD_OFFSET = .8, WORLD_BRANCH = .7, WORLD_SHRINK = .9;
    public static final double WORLD_SCALE = .3, WORLD_RADIUS_FROM = .5, WORLD_RADIUS_TO = .6;
    public static final double WORLD_FRAME_RATE = .6, WORLD_SWITCH_RATE = .7;
    public record Wave(int tick, double height) {}
    private static final List<Wave> WAVES = List.of(new Wave(0, 2), new Wave(1, 1.8), new Wave(3, 1.5),
            new Wave(4, 1), new Wave(6, .5), new Wave(7, 0), new Wave(8, -.1));

    private ClassicBodyIntensifyTimeline() {}
    public static List<Wave> waves() { return WAVES; }
    /** LambdaLib1.2.3 rangei uses [from,to), so rangei(3,4) ALWAYS returns3. */
    public static int rangedCount(Random random, int from, int to) { return from + random.nextInt(to - from); }
    public static double ranged(Random random, double from, double to) { return from + random.nextDouble() * (to - from); }
    public static int subArcAge(int age, double sample) { return sample < .9 ? age + 1 : age; }
    public static boolean subArcShown(boolean shown, double switchRate, double sample) {
        return sample < (shown ? .4 : .3) * switchRate ? !shown : shown;
    }
    public static boolean replaceTemplate(double frameRate, double sample) { return sample < .5 * frameRate; }
    public static double maskAlpha(long activeMillis, long blendMillis) {
        return blendMillis < 0 ? clamp(activeMillis / (double) BLEND_IN_MS)
                : clamp(1 - blendMillis / (double) BLEND_OUT_MS);
    }
    public static boolean disposed(long blendMillis) { return blendMillis > DISPOSE_AFTER_MS; }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }

    /** Original screen-relative offsets, centered in scaled GUI coordinates; size is GUI pixels. */
    public static final class Sprite {
        public final double x, y, size, switchRate;
        public final int life;
        public int template, age;
        public boolean shown = true, dead;
        private Sprite(Random random, boolean burst) {
            double radius = ranged(random, burst ? BURST_RADIUS_FROM : HELD_RADIUS_FROM,
                    burst ? BURST_RADIUS_TO : HELD_RADIUS_TO);
            double angle = random.nextDouble() * Math.PI * 2;
            size = ranged(random, burst ? BURST_SIZE_FROM : HELD_SIZE_FROM,
                    burst ? BURST_SIZE_TO : HELD_SIZE_TO);
            x = radius * Math.sin(angle); y = radius * Math.cos(angle);
            template = random.nextInt(HUD_TEMPLATES);
            life = burst ? BURST_LIFE : HELD_LIFE;
            switchRate = burst ? BURST_SWITCH_RATE : HELD_SWITCH_RATE;
        }
        public void tick(Random random) {
            if (replaceTemplate(HUD_FRAME_RATE, random.nextDouble())) template = random.nextInt(HUD_TEMPLATES);
            age = subArcAge(age, random.nextDouble());
            if (age == life) dead = true;
            shown = subArcShown(shown, switchRate, random.nextDouble());
        }
        public double left(double width) { return width / 2 + x * width / 2 - size / 2; }
        public double top(double height) { return height / 2 + y * height / 2 - size / 2; }
    }

    /** Clear held sprites on ANY end; regenerate only for a successful first-person end. */
    public static final class Hud {
        private final Random random;
        private final List<Sprite> sprites = new ArrayList<>();
        private final long started;
        private long blendStarted = -1;
        public Hud(long started, long seed) { this.started = started; random = new Random(seed); generate(false); }
        private void generate(boolean burst) {
            int count = rangedCount(random, burst ? BURST_COUNT_FROM : HELD_COUNT_FROM,
                    burst ? BURST_COUNT_TO : HELD_COUNT_TO);
            for (int i = 0; i < count; i++) sprites.add(new Sprite(random, burst));
        }
        public void startBlend(long now, boolean performed, boolean firstPerson) {
            if (blending()) return;
            blendStarted = now; sprites.clear();
            if (performed && firstPerson) generate(true);
        }
        public boolean blending() { return blendStarted >= 0; }
        public double maskAlpha(long now) { return ClassicBodyIntensifyTimeline.maskAlpha(now - started,
                blending() ? now - blendStarted : -1); }
        public double arcAlpha() { return blending() ? BURST_ALPHA : HELD_ALPHA; }
        public boolean disposed(long now) { return blending() && ClassicBodyIntensifyTimeline.disposed(now - blendStarted); }
        public List<Sprite> sprites() { return List.copyOf(sprites); }
        public void tick() {
            // Source handler removes previously dead sprites before ticking the remaining records.
            var iterator = sprites.iterator();
            while (iterator.hasNext()) { var sprite = iterator.next(); if (sprite.dead) iterator.remove(); else sprite.tick(random); }
        }
    }

    /** End broadcasts can arrive without a start. Duplicate/stale starts, ends and aborted tokens stay dead. */
    public static final class Tokens {
        private record State(long token, boolean ended) {}
        private final Map<Integer, State> latest = new LinkedHashMap<>();
        private static final int MAX_HISTORY = 1024;
        public boolean acceptStart(int entity, long token) {
            State old = latest.get(entity);
            if (token <= 0 || old != null && token <= old.token) return false;
            remember(entity, new State(token, false)); return true;
        }
        public boolean acceptEnd(int entity, long token) {
            State old = latest.get(entity);
            if (token <= 0 || old != null && (token < old.token || token == old.token && old.ended)) return false;
            remember(entity, new State(token, true)); return true;
        }
        public void rememberAbort(int entity, long token) {
            State old = latest.get(entity);
            if (token > 0 && (old == null || token >= old.token)) remember(entity, new State(token, true));
        }
        private void remember(int entity, State state) {
            latest.remove(entity); latest.put(entity, state);
            if (latest.size() > MAX_HISTORY) latest.remove(latest.keySet().iterator().next());
        }
        public void clear() { latest.clear(); }
        public int size() { return latest.size(); }
    }

    /**
     * Physical input identity is separate from authoritative server effect tokens. A new press
     * supersedes the old expected nonce immediately, including before either acknowledgement.
     * Clear retires current state but retains the counter, avoiding nonce reuse after teardown.
     */
    public static final class InputNonce {
        private long sequence, expected;
        private boolean driven, held;
        public long press() {
            if (sequence == Long.MAX_VALUE) throw new IllegalStateException("Input nonce exhausted");
            driven = held = true; expected = ++sequence; return expected;
        }
        public boolean accepts(long input) {
            return driven ? held && expected > 0 && input == expected : input == 0;
        }
        public void abort() { driven = true; held = false; expected = 0; }
        /** Complete only the currently expected press; stale packets cannot retire newer input. */
        public boolean complete(long input) {
            if (!accepts(input)) return false;
            if (driven) { held = false; expected = 0; }
            return true;
        }
        public void clear() { driven = held = false; expected = 0; }
        public boolean driven() { return driven; }
        public boolean held() { return held; }
        public long expected() { return expected; }
    }

    /** Pause-aware millisecond clock, matching GameTimer's use for the GUI independently of ticks. */
    public static final class PauseClock {
        private long lastWall = -1, elapsed;
        public long update(long wall, boolean running) {
            if (lastWall >= 0 && running) elapsed += Math.max(0, wall - lastWall);
            lastWall = wall; return elapsed;
        }
        public long elapsed() { return elapsed; }
        public void clear() { lastWall = -1; elapsed = 0; }
    }
}
