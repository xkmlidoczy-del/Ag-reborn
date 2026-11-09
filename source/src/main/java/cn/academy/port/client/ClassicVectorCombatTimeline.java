/* AcademyCraft 1.0.7 vector combat effect adaptation. Copyright Lambda Innovation; GPLv3. See NOTICE. */
package cn.academy.port.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Pure source parameters, random draw order, mesh samples and replay decisions used by the live adapter. */
public final class ClassicVectorCombatTimeline {
    public static final int WAVE_LIFE = 15, WING_FADE_TICKS = 15, BLOOD_FRAMES = 10, SPRAY_LIFE = 1200;
    public static final int TORNADO_DIVIDE = 40, TORNADO_SEGMENTS = 20, WING_DUST_PER_TICK = 12;
    public static final double TORNADO_HEIGHT = 2, TORNADO_SIZE = .16, TORNADO_DISPLACEMENT = 2;
    public static final float BLAST_VOLUME = .5F, WING_VOLUME = .5F, BLOOD_VOLUME = 1;
    public static final String WAVE_TEXTURE = "textures/effects/glow_circle.png", TORNADO_TEXTURE = "textures/effects/tornado_ring.png";
    public static final String BLAST_SOUND = "vecmanip.directed_blast", WING_SOUND = "vecmanip.storm_wing", BLOOD_SOUND = "vecmanip.blood_retro";
    public static final int[] BLOOD_PITCHES = {0, 30, 45, 60, 80, -30, -45, -60, -80};
    private static final ClassicCubicCurve ALPHA = new ClassicCubicCurve(0, 0, .2, 1, .5, 1, .8, 1, 1, 0);
    private static final ClassicCubicCurve SIZE = new ClassicCubicCurve(0, .4, .2, .8, 2.5, 1.5);
    private static final ClassicCubicCurve PREPARE_X = new ClassicCubicCurve(0, 0, 1, -.02);
    private static final ClassicCubicCurve PREPARE_Y = new ClassicCubicCurve(0, 0, .5, .2, 1, .4);
    private static final ClassicCubicCurve PREPARE_Z = new ClassicCubicCurve(0, 0, 1, -.05);
    private static final ClassicCubicCurve PREPARE_RX = new ClassicCubicCurve(0, 0, 1, -20);
    private static final ClassicCubicCurve PUNCH_X = new ClassicCubicCurve(0, -.04, .5, -.04, 1, 0);
    private static final ClassicCubicCurve PUNCH_Y = new ClassicCubicCurve(0, .8, .5, .75, 1, 0);
    private static final ClassicCubicCurve PUNCH_Z = new ClassicCubicCurve(0, 0, .3, -.4, 1, 0);
    private static final ClassicCubicCurve PUNCH_RX = new ClassicCubicCurve(0, -40, .5, -45, 1, 0);
    private static final ClassicCubicCurve PUNCH_RY = new ClassicCubicCurve(0, 0, .3, 10, 1, 0);
    private ClassicVectorCombatTimeline() {}

    public record HandPose(double x, double y, double z, double rotationX, double rotationY) {}
    public static HandPose prepare(double ageMillis) {
        double t = Math.min(2, Math.max(0, ageMillis) / 150);
        return new HandPose(PREPARE_X.valueAt(t), PREPARE_Y.valueAt(t), PREPARE_Z.valueAt(t), PREPARE_RX.valueAt(t), 0);
    }
    public static HandPose punch(double ageMillis) {
        double t = Math.max(0, ageMillis) / 300;
        return new HandPose(PUNCH_X.valueAt(t), PUNCH_Y.valueAt(t), PUNCH_Z.valueAt(t), PUNCH_RX.valueAt(t), PUNCH_RY.valueAt(t));
    }
    public record WaveRing(int life, double offset, double size, int timeOffset) {}
    /** rangei(2,3) produces two rings; the renderer's original mesh endpoints are -.5 and 1. */
    public static List<WaveRing> waveRings(Random random) {
        int count = 2 + random.nextInt(1);
        var rings = new ArrayList<WaveRing>();
        for (int index = 0; index < count; index++) rings.add(new WaveRing(8 + random.nextInt(4),
                index * 1.5 + range(random, -.3, .3), range(random, .8, 1.2), index * 2 - 1 + random.nextInt(2)));
        return List.copyOf(rings);
    }
    public static double waveAlpha(int tick, WaveRing ring) {
        return .7 * Math.min(clamp(ALPHA.valueAt(tick / (double) WAVE_LIFE)),
                clamp(ALPHA.valueAt((tick - ring.timeOffset) / (double) ring.life)));
    }
    public static double waveSize(int tick, WaveRing ring) { return ring.size * SIZE.valueAt(Math.max(0, Math.min(1.62, tick / 20.0))); }
    public static double waveDepth(int tick, WaveRing ring) { return tick / 40.0 + ring.offset; }
    public static boolean waveAlive(int tick) { return tick >= 0 && tick < WAVE_LIFE; }

    public record TornadoRing(double y, double width, double phase, double sizeScale) {}
    public record Tornado(List<TornadoRing> rings, double timeOffset) {}
    /** Exact TornadoEffect initialization, including duplicate rings. */
    public static Tornado tornado(Random random) {
        double timeOffset = random.nextDouble() * 20;
        var rings = new ArrayList<TornadoRing>(); double accum = 0, step = TORNADO_HEIGHT / TORNADO_DIVIDE;
        // The source has an unbounded Gaussian walk; pathological RNG input is bounded for a client packet.
        for (int guard = 0; accum < TORNADO_HEIGHT && guard < 4096; guard++) {
            accum += step * (1 + random.nextGaussian() * .2);
            if (random.nextDouble() < 1) {
                rings.add(new TornadoRing(accum, step * range(random, 1.8, 2.2), random.nextDouble() * 360, range(random, .9, 1.2)));
                if (random.nextDouble() < .35) rings.add(new TornadoRing(accum, step * range(random, 1.8, 2.2), random.nextDouble() * 360, range(random, 1.2, 1.7)));
            }
        }
        return new Tornado(List.copyOf(rings), timeOffset);
    }
    public record TornadoSample(double dx, double dz, double radius, double rotation) {}
    /** Original ImprovedNoise displacement/radius and texture phase; phase is intentionally not radians. */
    public static TornadoSample tornadoSample(TornadoRing ring, double time) {
        double ny = ring.y / TORNADO_HEIGHT, drift = .3 + Math.pow(ny * 2, 1.4);
        double dx = Noise.noise(ny, time * .1, 0) * drift * TORNADO_SIZE * TORNADO_DISPLACEMENT;
        double dz = Noise.noise(ny, time * .1, 1) * drift * TORNADO_SIZE * TORNADO_DISPLACEMENT;
        double radius = ((.5 + .3 * Noise.noise(ny, .2 * time, 0)) + .5 * Math.pow(1.5 * ny, 2) + Noise.noise(ny, 0, 0)) * TORNADO_SIZE * ring.sizeScale;
        return new TornadoSample(dx, dz, radius, .1 * (1 + .5 * ny) * time + ring.phase);
    }
    public record WingTransform(double x, double y, double z, double rotationY, double rotationZ) {}
    public static List<WingTransform> wingTransforms() { return List.of(new WingTransform(-.1, -.3, .1, 45, 45),
            new WingTransform(.1, -.3, .1, -45, -45), new WingTransform(-.1, -.5, -.1, -45, 45), new WingTransform(.1, -.5, -.1, 45, -45)); }
    /** Charge branch precedes termination in the source: a cancelled charge remains in its charge branch. */
    public static double wingAlpha(boolean active, int stateTicks, double chargeTicks, int terminateTicks) {
        if (!active) return stateTicks / chargeTicks * .7;
        return terminateTicks < 0 ? .7 : .7 * (1 - terminateTicks / (double) WING_FADE_TICKS);
    }
    public static boolean wingAlive(int terminateTicks) { return terminateTicks <= WING_FADE_TICKS; }
    public record Dust(double x, double y, double z, double vx, double vy, double vz) {}
    public static Dust wingDust(Random random) {
        double theta = range(random, 0, Math.PI * 2), phi = range(random, -Math.PI, Math.PI), radius = range(random, 3, 8);
        double rzx = radius * Math.sin(phi), c = Math.cos(theta), s = Math.sin(theta);
        return new Dust(rzx * c, radius * Math.cos(phi), rzx * s, s * .7F, range(random, -.01F, .05F), -c * .7F);
    }

    public record Splash(double size, double x, double y, double z) {}
    public static List<Splash> splashes(Random random, double width, double height) {
        int count = 6 + random.nextInt(4); var result = new ArrayList<Splash>();
        for (int i = 0; i < count; i++) {
            // EntityBloodSplash constructor draws its old default size before skill overrides it.
            random.nextFloat(); double size = 1.4F + random.nextFloat() * (1.8F - 1.4F);
            result.add(new Splash(size, range(random, -1, 1) * width, range(random, 0, 1) * height, range(random, -1, 1) * width));
        }
        return List.copyOf(result);
    }
    public record Spray(int texture, double size, double rotation, double offsetX, double offsetY) {}
    /** Preserve classic isWall inversion: UP/DOWN choose wall textures; lateral faces choose grnd. */
    public static boolean sprayUsesWall(int side) { return side == 0 || side == 1; }
    public static Spray spray(Random random, int side) { return new Spray(random.nextInt(10),
            range(random, 1.1, 1.4) * (sprayUsesWall(side) ? 1 : .8), range(random, 0, 360), random.nextGaussian() * .15, random.nextGaussian() * .15); }
    public static int sprayFrame(int id) { return id % 3; }
    public static boolean splashAlive(int tick) { return tick >= 0 && tick < BLOOD_FRAMES; }
    public static boolean sprayAlive(int tick, boolean air) { return tick <= SPRAY_LIFE && !air; }
    public static int splashFrame(int tick) { return Math.max(0, Math.min(BLOOD_FRAMES - 1, tick)); }
    public static double range(Random random, double from, double to) { return from + random.nextDouble() * (to - from); }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }

    /** Distinct skill/entity histories, monotonic state indices, and successful-end-before-perform delivery. */
    public static final class Tokens {
        private enum Phase { HOLD, PERFORM, PERFORM_END, END_SUCCESS, END, ABORT }
        private record Key(String skill, int entity) {}
        private record Entry(long token, Phase phase, int index) {}
        private final Map<Key, Entry> latest = new LinkedHashMap<>();
        public boolean start(String skill, int entity, long token) {
            Key key = new Key(skill, entity); Entry prev = latest.get(key);
            if (token <= 0 || prev != null && token <= prev.token) return false;
            remember(key, new Entry(token, Phase.HOLD, -1)); return true;
        }
        public boolean state(String skill, int entity, long token, int index) {
            Key key = new Key(skill, entity); Entry prev = latest.get(key);
            if (index < 0 || prev == null || token != prev.token || prev.phase != Phase.HOLD || index <= prev.index) return false;
            remember(key, new Entry(token, Phase.HOLD, index)); return true;
        }
        public boolean perform(String skill, int entity, long token) {
            Key key = new Key(skill, entity); Entry prev = latest.get(key);
            if (token <= 0 || prev != null && (token < prev.token || token == prev.token && prev.phase != Phase.HOLD && prev.phase != Phase.END_SUCCESS)) return false;
            remember(key, new Entry(token, Phase.PERFORM, prev == null ? -1 : prev.index)); return true;
        }
        public boolean end(String skill, int entity, long token, boolean performed) {
            Key key = new Key(skill, entity); Entry prev = latest.get(key);
            if (token <= 0 || prev != null && (token < prev.token || token == prev.token && (prev.phase == Phase.END || prev.phase == Phase.END_SUCCESS || prev.phase == Phase.PERFORM_END || prev.phase == Phase.ABORT))) return false;
            // Keep the action's tombstone after its visual context ends, so duplicate action never replays.
            remember(key, new Entry(token, prev != null && token == prev.token && prev.phase == Phase.PERFORM
                    ? Phase.PERFORM_END : performed ? Phase.END_SUCCESS : Phase.END, prev == null ? -1 : prev.index)); return true;
        }
        public void abort(String skill, int entity, long token) {
            if (token <= 0) return; Key key = new Key(skill, entity); Entry prev = latest.get(key);
            if (prev == null || token > prev.token || token == prev.token && prev.phase == Phase.HOLD) remember(key, new Entry(token, Phase.ABORT, -1));
        }
        private void remember(Key key, Entry entry) { latest.remove(key); latest.put(key, entry); if (latest.size() > 1024) latest.remove(latest.keySet().iterator().next()); }
        public int size() { return latest.size(); }
        public void clear() { latest.clear(); }
    }
    /** A release leaves the pending success eligible. Abort and a newer press retire it. */
    public static final class InputNonce {
        private long counter, expected, completed; private boolean driven, held;
        public long press() { if (++counter <= 0) counter = 1; driven = held = true; completed = 0; return expected = counter; }
        public boolean accepts(long input) { return driven ? input > 0 && input == expected : input == 0; }
        public boolean acceptsSuccess(long input) { return accepts(input) || driven && input > 0 && input == completed; }
        public void release() { held = false; }
        public void abort() { held = false; expected = completed = 0; driven = true; }
        public boolean complete(long input) { if (!accepts(input)) return false; held = false; completed = expected; expected = 0; return true; }
        public long expected() { return expected; }
        public boolean held() { return held; }
        public void clear() { expected = completed = 0; driven = held = false; }
    }
    public static final class PauseClock {
        private long wall, elapsed, origin; private boolean initialized, wasRunning;
        public long update(long now, boolean running) { if (!initialized) origin = now; if (initialized && running && wasRunning) elapsed += Math.max(0, now - wall); wall = now; wasRunning = running; initialized = true; return elapsed; }
        public long elapsed() { return elapsed; }
        public long absolute() { return origin + elapsed; }
        public void clear() { wall = elapsed = origin = 0; initialized = wasRunning = false; }
    }

    /**
     * JAVA REFERENCE IMPLEMENTATION OF IMPROVED NOISE - COPYRIGHT 2002 KEN PERLIN.
     * From: http://mrl.nyu.edu/~perlin/noise/
     */
    private static final class Noise {
    
        public static double noise(double x, double y, double z) {
            int X = (int)Math.floor(x) & 255,                  // FIND UNIT CUBE THAT
                    Y = (int)Math.floor(y) & 255,                  // CONTAINS POINT.
                    Z = (int)Math.floor(z) & 255;
            x -= Math.floor(x);                                // FIND RELATIVE X,Y,Z
            y -= Math.floor(y);                                // OF POINT IN CUBE.
            z -= Math.floor(z);
            double u = fade(x),                                // COMPUTE FADE CURVES
                    v = fade(y),                                // FOR EACH OF X,Y,Z.
                    w = fade(z);
            int A = p[X  ]+Y, AA = p[A]+Z, AB = p[A+1]+Z,      // HASH COORDINATES OF
                    B = p[X+1]+Y, BA = p[B]+Z, BB = p[B+1]+Z;      // THE 8 CUBE CORNERS,
    
            return lerp(w, lerp(v, lerp(u, grad(p[AA  ], x  , y  , z   ),  // AND ADD
                    grad(p[BA  ], x-1, y  , z   )), // BLENDED
                    lerp(u, grad(p[AB  ], x  , y-1, z   ),  // RESULTS
                            grad(p[BB  ], x-1, y-1, z   ))),// FROM  8
                    lerp(v, lerp(u, grad(p[AA+1], x  , y  , z-1 ),  // CORNERS
                            grad(p[BA+1], x-1, y  , z-1 )), // OF CUBE
                            lerp(u, grad(p[AB+1], x  , y-1, z-1 ),
                                    grad(p[BB+1], x-1, y-1, z-1 ))));
        }
    
        public static double noise(double x, double y) { return noise(x, y, 0); }
    
        public static double noise(double x) { return noise(x, 0, 0); }
    
        static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
        static double lerp(double t, double a, double b) { return a + t * (b - a); }
        static double grad(int hash, double x, double y, double z) {
            int h = hash & 15;                      // CONVERT LO 4 BITS OF HASH CODE
            double u = h<8 ? x : y,                 // INTO 12 GRADIENT DIRECTIONS.
                    v = h<4 ? y : h==12||h==14 ? x : z;
            return ((h&1) == 0 ? u : -u) + ((h&2) == 0 ? v : -v);
        }
        static final int p[] = new int[512], permutation[] = { 151,160,137,91,90,15,
                131,13,201,95,96,53,194,233,7,225,140,36,103,30,69,142,8,99,37,240,21,10,23,
                190, 6,148,247,120,234,75,0,26,197,62,94,252,219,203,117,35,11,32,57,177,33,
                88,237,149,56,87,174,20,125,136,171,168, 68,175,74,165,71,134,139,48,27,166,
                77,146,158,231,83,111,229,122,60,211,133,230,220,105,92,41,55,46,245,40,244,
                102,143,54, 65,25,63,161, 1,216,80,73,209,76,132,187,208, 89,18,169,200,196,
                135,130,116,188,159,86,164,100,109,198,173,186, 3,64,52,217,226,250,124,123,
                5,202,38,147,118,126,255,82,85,212,207,206,59,227,47,16,58,17,182,189,28,42,
                223,183,170,213,119,248,152, 2,44,154,163, 70,221,153,101,155,167, 43,172,9,
                129,22,39,253, 19,98,108,110,79,113,224,232,178,185, 112,104,218,246,97,228,
                251,34,242,193,238,210,144,12,191,179,162,241, 81,51,145,235,249,14,239,107,
                49,192,214, 31,181,199,106,157,184, 84,204,176,115,121,50,45,127, 4,150,254,
                138,236,205,93,222,114,67,29,24,72,243,141,128,195,78,66,215,61,156,180
        };
        static { for (int i=0; i < 256 ; i++) p[256+i] = p[i] = permutation[i]; }
    }
}
