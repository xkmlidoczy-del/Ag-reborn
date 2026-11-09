/* AcademyCraft1.0.7 / LambdaLib1.2.3 source adaptation. AcademyCraft GPLv3;
 * LambdaLib MIT, Copyright Lambda Innovation. See project NOTICE. */
package cn.academy.port.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Source math and cosmetic lifecycle only. Dependency-free except the established starter math. */
public final class ClassicMeltdownerBeamTimeline {
    public static final String MELTDOWNER = "meltdowner", MINE = "mine_ray_basic";
    public static final String CHARGE_SOUND = "md.md_charge", PERFORM_SOUND = "md.meltdowner",
            MINE_LOOP_SOUND = "md.mine_loop", MINE_START_SOUND = "md.mine_basic_startup";
    public static final float CHARGE_VOLUME = 1F, PERFORM_VOLUME = .5F, MINE_LOOP_VOLUME = .3F, MINE_START_VOLUME = .4F;
    public static final int MIN_CHARGE = 20, MAX_CHARGE = 40, TOLERANCE = 100;
    public static final Spec MD = new Spec(50, 200, 700, 300, .17, .22, 1.5, .8, .8),
            BASIC = new Spec(233333, 200, 400, 300, .03, .045, .3, .5, .5);
    /** The client-only ray entity retains vanilla1.7's default1.8F entity height and85% eye height. */
    public static final double RAY_EYE_HEIGHT = (double)(1.8F * .85F);
    private static final float[] SINES = new float[65536];
    static { for (int i = 0; i < SINES.length; i++) SINES[i] = (float)Math.sin(i * Math.PI * 2 / 65536); }
    private static float sin(float value) { return SINES[(int)(value * 10430.378F) & 65535]; }
    private static float cos(float value) { return SINES[(int)(value * 10430.378F + 16384F) & 65535]; }
    private ClassicMeltdownerBeamTimeline() {}
    public record Spec(int lifeTicks, long blendIn, long blendOut, long shrink, double inner, double outer,
                       double glowWidth, double glowAlpha, double particleChance) {
        public long lifeMillis() { return lifeTicks * 50L; }
    }
    public record Point(double x, double y, double z) {
        public Point add(Point p) { return new Point(x + p.x, y + p.y, z + p.z); }
        public Point subtract(Point p) { return new Point(x - p.x, y - p.y, z - p.z); }
        public Point scale(double n) { return new Point(x * n, y * n, z * n); }
        public double length() { return Math.sqrt(x * x + y * y + z * z); }
        public Point normalize() { double n = length(); return n < 1E-4 ? new Point(0, 0, 0) : new Point(x / n, y / n, z / n); }
        public Point cross(Point p) { return new Point(y * p.z - z * p.y, z * p.x - x * p.z, x * p.y - y * p.x); }
    }
    public record Endpoints(Point from, Point to) {}
    public record Glow(Point from, Point mid1, Point mid2, Point to, Point up) {}
    public record CylinderFrame(Point origin, Point x, Point y, Point z) {
        public Point apply(ClassicMeltdownerStarterTimeline.Point p) {
            return origin.add(x.scale(p.x())).add(y.scale(p.y())).add(z.scale(p.z()));
        }
    }
    public static double lengthScale(Spec spec, long age) { return age < spec.blendIn ? (double)age / spec.blendIn : 1; }
    public static double alpha(Spec spec, long age) {
        long life = spec.lifeMillis();
        return age > life - spec.blendOut ? 1 - (double)(age + spec.blendOut - life) / spec.blendOut : 1;
    }
    public static double width(Spec spec, long age, double wiggle) {
        long life = spec.lifeMillis();
        return wiggle + (age > life - spec.shrink ? 1 - (double)(age + spec.shrink - life) / spec.shrink : 1);
    }
    /** RendererRayGlow multiplies getAlpha AND getGlowAlpha; the squared fade is original. */
    public static double glowAlpha(Spec spec, long age, double wiggle) {
        double a = alpha(spec, age); return spec.glowAlpha * a * (.9 + wiggle) * a;
    }
    public static float walkSpeed(int ticks) { return .1F - ticks * .001F; }
    /** Source rangei(2,3) consumes nextInt(1); Scala `2 to 0` has default+1 step and is empty. */
    public static int chargeParticleCount(Random random) { int from = 2 + random.nextInt(1); return from <= 0 ? 1 - from : 0; }
    public static int mineParticleCount(Random random) { return 1 + 2 + random.nextInt(1); }
    /** Kept to audit the dormant MDContextC charge-particle body, without inventing iterations. */
    public static Point chargeParticlePosition(Point feet, boolean local, double radius, double theta, double height) {
        Point legacyPosition = feet.add(new Point(0, local ? 1.6 : 0, 0));
        return chargeParticleSourcePosition(legacyPosition, local, radius, theta, height);
    }
    public static Point chargeParticleSourcePosition(Point sourcePosition, boolean local, double radius, double theta, double height) {
        return sourcePosition.add(new Point(radius * Math.sin(theta), (local ? 0 : 1.6) + height, radius * Math.cos(theta)));
    }
    public static Point mineParticlePosition(int x, int y, int z, double ox, double oy, double oz) {
        return new Point(x + ox, y + oy, z + oz); // Deliberately includes sentinel(-1,-1,-1).
    }
    public static Point particleStep(Point position, Point velocity, boolean mining) {
        return position.add(mining ? velocity.add(new Point(0, -.01, 0)) : velocity);
    }
    public static Point direction(float yaw, float pitch) {
        float y = yaw / 180F * (float)Math.PI, p = pitch / 180F * (float)Math.PI;
        return new Point(-sin(y) * cos(p), -sin(p), cos(y) * cos(p)).normalize();
    }
    public static float rayYaw(Point direction) { return (float)(-Math.atan2(direction.x, direction.z) * 180 / Math.PI); }
    public static Point entityDirection(Point raw) {
        float pitch = (float)(-Math.atan2(raw.y, Math.sqrt(raw.x * raw.x + raw.z * raw.z)) * 180 / Math.PI);
        return direction(rayYaw(raw), pitch); // Source setFromTo float rotation then Motion3D/MathHelper LUT.
    }
    public static Point mainOrigin(Point serverEye, boolean local) { return serverEye.add(new Point(local ? 0 : 1.6, 0, 0)); }
    public static Endpoints mineEndpoints(Point feet, double eyeHeight, float headYaw, float pitch, boolean local) {
        Point sourcePosition = feet.add(new Point(0, local ? 1.6 : 0, 0));
        double sourceEyeHeight = eyeHeight - (local ? 1.6 : 0);
        return mineSourceEndpoints(sourcePosition, sourceEyeHeight, headYaw, pitch, local);
    }
    public static Endpoints mineSourceEndpoints(Point sourcePosition, double sourceEyeHeight, float headYaw, float pitch, boolean local) {
        Point end = sourcePosition.add(new Point(local ? 0 : 1.6, sourceEyeHeight, 0)).add(direction(headYaw, pitch).scale(15));
        return new Endpoints(sourcePosition.add(new Point(0, local ? 0 : 1.6, 0)), end);
    }
    public static Point reflectionOrigin(Point casterEye, Point casterLook, Point reflectorEye) {
        return casterEye.add(casterLook.normalize().scale(reflectorEye.subtract(casterEye).length()));
    }
    /** Source Vec3.rotateAroundY(float radians), not an invented pitch-following hand offset. */
    public static Point viewFix(Point rawDirection, boolean firstPerson) {
        Point p = firstPerson ? new Point(-.05, -.25, .2) : new Point(.15, -.8, .23);
        float angle = (270F - rayYaw(rawDirection)) * (float)Math.PI / 180F;
        return rotateFix(p, angle);
    }
    private static Point rotateFix(Point p, float angle) {
        double c = cos(angle), s = sin(angle);
        return new Point(p.x * c + p.z * s, p.y, p.z * c - p.x * s);
    }
    /** Glow's original radians order differs from RendererRayBaseSimple's float MathUtils call. */
    public static Point glowViewFix(Point rawDirection, boolean firstPerson) {
        Point p = firstPerson ? new Point(-.05, -.25, .2) : new Point(.15, -.8, .23);
        return rotateFix(p, (float)((270F - rayYaw(rawDirection)) / 180F * Math.PI));
    }
    public static Glow glow(Point origin, Point rawDirection, double length, Point camera, boolean firstPerson, double textureLength) {
        return glow(origin, rawDirection, length, camera, firstPerson, textureLength, true);
    }
    public static Glow glow(Point origin, Point rawDirection, double length, Point camera, boolean firstPerson, double textureLength, boolean applyViewFix) {
        Point rayDirection = entityDirection(rawDirection), from = applyViewFix ? origin.add(glowViewFix(rawDirection, firstPerson)) : origin, to = origin.add(rayDirection.scale(length));
        Point look = to.subtract(from).normalize();
        Point up = firstPerson ? new Point(0, 1, -.5).normalize() : origin.subtract(camera).cross(rayDirection).normalize();
        // Texture heads remain source base width long, even at short/zero blend-in length.
        return new Glow(from, from.add(look.scale(textureLength)), to.subtract(look.scale(textureLength)), to, up);
    }
    public static CylinderFrame cylinderFrame(Point origin, Point rawDirection, double length, boolean firstPerson) {
        return cylinderFrame(origin, rawDirection, length, firstPerson, true);
    }
    public static CylinderFrame cylinderFrame(Point origin, Point rawDirection, double length, boolean firstPerson, boolean applyViewFix) {
        Point dir = entityDirection(rawDirection), start = applyViewFix ? origin.add(viewFix(rawDirection, firstPerson)) : origin;
        Point delta = origin.add(dir.scale(length)).subtract(start);
        double yaw = -Math.PI / 2 + Math.atan2(delta.x, delta.z), pitch = Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z));
        return new CylinderFrame(start, new Point(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), -Math.sin(yaw) * Math.cos(pitch)),
                new Point(-Math.cos(yaw) * Math.sin(pitch), Math.cos(pitch), Math.sin(yaw) * Math.sin(pitch)), new Point(Math.sin(yaw), 0, Math.cos(yaw)));
    }
    public static List<ClassicMeltdownerStarterTimeline.Quad> cylinder(double length, double radius, double headFix) {
        return ClassicMeltdownerStarterTimeline.cylinder(length, radius, headFix);
    }
    public record Caster(int entity, UUID uuid) {}
    /** Entity id+UUID is the identity; history never evicts replay tombstones within the session. */
    public static final class Tokens {
        private record State(long token, boolean ended) {}
        private final Map<Caster, State> history = new HashMap<>();
        private boolean capacity(Caster key) { return history.containsKey(key) || history.size() < 4096; }
        public boolean start(Caster key, long token) {
            State old = history.get(key);
            if (token <= 0 || !capacity(key) || old != null && token <= old.token) return false;
            history.put(key, new State(token, false)); return true;
        }
        public boolean active(Caster key, long token) { State s = history.get(key); return s != null && !s.ended && s.token == token; }
        public boolean end(Caster key, long token) {
            State old = history.get(key);
            if (token <= 0 || !capacity(key) || old != null && (token < old.token || token == old.token && old.ended)) return false;
            history.put(key, new State(token, true)); return true;
        }
        public void clear() { history.clear(); }
    }
    public static final class ParticleSequence {
        private int latest = -1;
        public boolean accept(int index) { if (index < 0 || index <= latest) return false; latest = index; return true; }
    }
}
