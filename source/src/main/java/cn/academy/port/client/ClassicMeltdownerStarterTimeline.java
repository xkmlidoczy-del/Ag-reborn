/* AcademyCraft1.0.7 EntityMdBall/EntityMdShield/EntityMdRaySmall and LambdaLib1.2.3 adaptation.
 * Copyright Lambda Innovation. AcademyCraft GPLv3; LambdaLib MIT. See NOTICE. */
package cn.academy.port.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Dependency-free source render parameters and packet/input ordering. No rendered-pixel claim. */
public final class ClassicMeltdownerStarterTimeline {
    public static final String SCATTER = "scatter_bomb", SHIELD = "light_shield";
    public static final int BALL_LIFE = 2333333, BALL_TEXTURES = 5, MAX_BALLS = 7;
    public static final long RAY_MS = 700;
    public static final String STARTUP_SOUND = "md.shield_startup", LOOP_SOUND = "md.shield_loop", RAY_SOUND = "md.ray_small";
    public static final float STARTUP_VOLUME = .5F, LOOP_VOLUME = 1F, RAY_VOLUME = .5F;
    public static final double SHIELD_SIZE = 1.8F, ALPHA_CUTOFF = .05F;
    private static final float[] SINES = new float[65536];
    static { for (int i = 0; i < SINES.length; i++) SINES[i] = (float)Math.sin(i * Math.PI * 2 / 65536); }
    private static float sin(float value) { return SINES[(int)(value * 10430.378F) & 65535]; }
    private static float cos(float value) { return SINES[(int)(value * 10430.378F + 16384F) & 65535]; }
    private ClassicMeltdownerStarterTimeline() {}
    public record Point(double x, double y, double z) {
        public Point add(Point other) { return new Point(x + other.x, y + other.y, z + other.z); }
        public Point scale(double scale) { return new Point(x * scale, y * scale, z * scale); }
    }
    public record Vertex(Point point, float u, float v) {}
    public record Quad(Vertex a, Vertex b, Vertex c, Vertex d) {}
    public record ShieldFrame(float size, float unusedAlpha, float rotationSpeed) {}
    public static ShieldFrame shieldFrame(int ticks) {
        return new ShieldFrame(1.8F * lerpf(.2F, 1F, Math.min(ticks / 15F, 1F)),
                Math.min(ticks / 6F, 1F), lerpf(.8F, 2F, Math.min(ticks / 30F, 1F)));
    }
    private static float lerpf(float a, float b, float t) { return a + (b - a) * t; }
    /** Source subtracts360 ONCE; modulo would change the stored rotation after long frames. */
    public static float shieldRotation(float rotation, int ticks, long deltaMillis) {
        rotation += shieldFrame(ticks).rotationSpeed * deltaMillis;
        if (rotation >= 360F) rotation -= 360F;
        return rotation;
    }
    public static Point direction(float headYaw, float pitch) {
        float yaw = headYaw / 180F * (float)Math.PI, p = pitch / 180F * (float)Math.PI;
        Point result = new Point(-sin(yaw) * cos(p), -sin(p), cos(yaw) * cos(p));
        double length = Math.sqrt(result.x * result.x + result.y * result.y + result.z * result.z);
        return result.scale(1 / length);
    }
    /** Preserve Motion3D.checkClientOffset's unexpected observer +X, not +Y. */
    public static Point shieldPosition(Point feet, double eyeHeight, float headYaw, float pitch, boolean localPlayer) {
        return feet.add(new Point(localPlayer ? 0 : 1.6, eyeHeight - .5, 0)).add(direction(headYaw, pitch));
    }
    public static Point shieldParticleCenter(Point feet, double eyeHeight, float headYaw, float pitch, boolean localPlayer) {
        return shieldPosition(feet, eyeHeight, headYaw, pitch, localPlayer).add(new Point(0, .5, 0));
    }
    /** Explicit source client-relative correction, before the modern feet-coordinate bridge. */
    public static Point sourceBallOffset(Point serverOffset, boolean localPlayer) {
        return serverOffset.add(new Point(0, localPlayer ? -1.6 : 0, 0));
    }
    /** Legacy local-player posY differs by1.6; modern feet-coordinate entity positions do not. */
    public static Point modernBallOffset(Point serverOffset, boolean localPlayer) {
        return sourceBallOffset(serverOffset, localPlayer).add(new Point(0, localPlayer ? 1.6 : 0, 0));
    }
    public static Point ballJitter(long ageMillis) {
        float phase = ageMillis / 300F;
        return new Point(.03 * sin(phase), .04 * cos((float)(phase * 1.4 + Math.PI / 3.5)), .03 * cos(phase));
    }
    public static double ballAlpha(int lifeTicks, long ageMillis) {
        int lifeMS = lifeTicks * 50;
        if (ageMillis > lifeMS - 150) return Math.max(0, lerpf(1F, 0F, (float)(ageMillis - (lifeMS - 150)) / 150));
        if (ageMillis > lifeMS - 400) return .6 + ((double)(ageMillis - (lifeMS - 400)) / (400 - 150)) * (1 - .6);
        if (ageMillis < 300) return ((double)ageMillis / 300) * .6;
        return .6;
    }
    public static float ballSize(int lifeTicks, long ageMillis) {
        int lifeMS = lifeTicks * 50;
        if (ageMillis > lifeMS - 100) return Math.max(0, lerpf(1.5F, 0F, (float)(ageMillis - (lifeMS - 100)) / 100));
        if (ageMillis > lifeMS - 300) return lerpf(1F, 1.5F, (float)(ageMillis - (lifeMS - 300)) / 200);
        return 1;
    }
    /** RenderIcon uses [-.5,.5]X and [-.25,.75]Y; shield MeshUtils UVs run bottom0→top1. */
    public static Quad billboard(double size, boolean shield) {
        double lower = shield ? -.5 : -.25, upper = shield ? .5 : .75;
        float bottom = shield ? 0 : 1, top = shield ? 1 : 0;
        return new Quad(new Vertex(new Point(-size / 2, size * lower, 0), 0, bottom),
                new Vertex(new Point(size / 2, size * lower, 0), 1, bottom),
                new Vertex(new Point(size / 2, size * upper, 0), 1, top),
                new Vertex(new Point(-size / 2, size * upper, 0), 0, top));
    }
    public static Quad particleQuad(double size) {
        Quad centered = billboard(size, true);
        return new Quad(flipV(centered.a), flipV(centered.b), flipV(centered.c), flipV(centered.d));
    }
    private static Vertex flipV(Vertex v) { return new Vertex(v.point, v.u, 1 - v.v); }
    public static double rayLengthScale(long age) { return age < 200 ? age / 200.0 : 1; }
    public static double rayAlpha(long age) { return age > 300 ? 1 - (double)(age + 400 - 700) / 400 : 1; }
    public static double rayWidth(long age) { return age > 200 ? 1 - Math.max(0, Math.min(1, (double)(age - 200) / 500)) : 1; }
    public static double rayGlowAlpha(long age, double glowWiggle) { double alpha = rayAlpha(age); return .5 * alpha * (.9 + glowWiggle) * alpha; }
    /** Literal RendererRayCylinder geometry:12 angular divisions,4 sqrt-radius slices, mirrored rearZ. */
    public static List<Quad> cylinder(double length, double radius, double headFix) {
        List<Quad> result = new ArrayList<>(108);
        double offset = radius * (1 - headFix);
        // Preserve source layer order: entire front head, body, then mirrored entire rear head.
        for (int slice = 0; slice < 4; slice++) for (int side = 0; side < 12; side++) {
            double s0 = slice / 4.0, s1 = (slice + 1) / 4.0;
            double a = side * Math.PI * 2 / 12, b = (side + 1) * Math.PI * 2 / 12;
            result.add(ring(radius * Math.sqrt(s0), radius * Math.sqrt(s1),
                    offset + radius * headFix * s0, offset + radius * headFix * s1, a, b, false));
        }
        for (int side = 0; side < 12; side++) {
            double a = side * Math.PI * 2 / 12, b = (side + 1) * Math.PI * 2 / 12;
            result.add(ring(radius, radius, radius, length, a, b, false));
        }
        for (int slice = 0; slice < 4; slice++) for (int side = 0; side < 12; side++) {
            double s0 = slice / 4.0, s1 = (slice + 1) / 4.0;
            double a = side * Math.PI * 2 / 12, b = (side + 1) * Math.PI * 2 / 12;
            result.add(ring(radius * Math.sqrt(s0), radius * Math.sqrt(s1),
                    length + radius - offset - radius * headFix * s0,
                    length + radius - offset - radius * headFix * s1, a, b, true));
        }
        return result;
    }
    private static Quad ring(double r0, double r1, double x0, double x1, double a, double b, boolean mirrorZ) {
        return new Quad(vertex(x0, r0, a, mirrorZ, 0, 0), vertex(x1, r1, a, mirrorZ, 1, 0),
                vertex(x1, r1, b, mirrorZ, 1, 1), vertex(x0, r0, b, mirrorZ, 0, 1));
    }
    private static Vertex vertex(double x, double radius, double a, boolean mirrorZ, float u, float v) {
        return new Vertex(new Point(x, radius * Math.sin(a), radius * Math.cos(a) * (mirrorZ ? -1 : 1)), u, v);
    }
    public static double particleAlpha(int age, int life) {
        if (age > life) return Math.max(0, 1 - (double)(age - life) / 20);
        if (age < 5) return age / 5.0;
        return 1;
    }
    /** At life+fade the original particle is alive with alpha0; removal is the following tick. */
    public static boolean particleAlive(int age, int life) { return age <= life + 20; }
    public static final class BallWiggle {
        public double alpha = .8, acceleration; public int texture; private long last;
        public void frame(long now, Random entityRandom, Random globalRandom) {
            if (last != 0) {
                if (entityRandom.nextInt(8) < 3) acceleration = ranged(globalRandom, -4, 4);
                alpha = Math.max(0, Math.min(1, alpha + acceleration * (now - last) / 1000));
            }
            if (entityRandom.nextInt(8) < 2) texture = entityRandom.nextInt(BALL_TEXTURES);
            last = now;
        }
    }
    public static final class RayWiggle {
        public double width, glow; private long last;
        public void frame(long now, Random random) {
            if (last != 0) {
                width = Math.max(0, Math.min(.1, width + (now - last) * ranged(random, -.4, .4) / 1000));
                glow = Math.max(0, Math.min(.1, glow + (now - last) * ranged(random, -.4, .4) / 1000));
            }
            last = now;
        }
    }
    public static double ranged(Random random, double from, double to) { return from + random.nextDouble() * (to - from); }
    public static final class Input {
        private long sequence, expected; private boolean physical, held;
        public long press() { if (sequence == Long.MAX_VALUE) throw new IllegalStateException("Input nonce exhausted"); physical = held = true; return expected = ++sequence; }
        /** Early release keeps correlation for terminal rays/end, but prohibits speculative start visuals. */
        public void end() { physical = true; held = false; }
        public boolean accepts(long nonce) { return physical ? expected > 0 && nonce == expected : nonce == 0; }
        public boolean heldPending() { return physical && held && expected > 0; }
        public boolean mayShowStart(long nonce) { return accepts(nonce) && (!physical || held); }
        public void complete(long nonce) { if (accepts(nonce)) { expected = 0; held = false; } }
        public void clear() { physical = held = false; expected = 0; }
    }
    /** Fail closed at4096 distinct caster IDs per session; never evict a replay tombstone. */
    public static final class Tokens {
        private record State(long token, boolean ended) {}
        private final Map<Integer, State> latest = new HashMap<>();
        private static final int MAX_HISTORY = 4096;
        private boolean capacity(int id) { return latest.containsKey(id) || latest.size() < MAX_HISTORY; }
        public boolean start(int id, long token) {
            State old = latest.get(id); if (!capacity(id) || token <= 0 || old != null && token <= old.token) return false;
            latest.put(id, new State(token, false)); return true;
        }
        public boolean active(int id, long token) { State state = latest.get(id); return state != null && state.token == token && !state.ended; }
        public boolean end(int id, long token) {
            State old = latest.get(id);
            if (!capacity(id) || token <= 0 || old != null && (token < old.token || token == old.token && old.ended)) return false;
            latest.put(id, new State(token, true)); return true;
        }
        public void clear() { latest.clear(); }
    }
    public static final class PauseClock {
        private long last = -1, elapsed;
        public void update(long wall, boolean running) { if (last >= 0 && running) elapsed += Math.max(0, wall - last); last = wall; }
        public long elapsed() { return elapsed; }
        public void clear() { last = -1; elapsed = 0; }
    }
}
