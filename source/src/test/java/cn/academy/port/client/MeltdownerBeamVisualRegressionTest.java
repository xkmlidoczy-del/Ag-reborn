/* Original-source formula/geometry/ordering differential checks; no Minecraft/GPU/audio execution. */
package cn.academy.port.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import static cn.academy.port.client.ClassicMeltdownerBeamTimeline.*;

public final class MeltdownerBeamVisualRegressionTest {
    private static long checks;
    private static final float[] SINES = new float[65536];
    static { for (int i = 0; i < SINES.length; i++) SINES[i] = (float)Math.sin(i * Math.PI * 2 / 65536); }
    private static float sin(float value) { return SINES[(int)(value * 10430.378F) & 65535]; }
    private static float cos(float value) { return SINES[(int)(value * 10430.378F + 16384F) & 65535]; }
    private static void ok(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static void near(double actual, double expected, double epsilon, String why) {
        checks++; if (Math.abs(actual - expected) > epsilon) throw new AssertionError(why + ": " + actual + " vs " + expected);
    }
    private static void near(Point a, Point e, double epsilon, String why) {
        near(a.x(), e.x(), epsilon, why + "X"); near(a.y(), e.y(), epsilon, why + "Y"); near(a.z(), e.z(), epsilon, why + "Z");
    }
    private static void curves() {
        ok(MD.lifeTicks() == 50 && BASIC.lifeTicks() == 233333, "exact entity lives");
        near(CHARGE_VOLUME, 1F, 0, "charge follows caster with source volume1");
        near(PERFORM_VOLUME, .5F, 0, "perform volume.5"); near(MINE_LOOP_VOLUME, .3F, 0, "mine loop volume.3");
        near(MINE_START_VOLUME, .4F, 0, "mine startup volume.4");
        near(MD.inner(), .17, 0, "MD inner radius"); near(MD.outer(), .22, 0, "MD outer radius");
        near(MD.glowWidth(), 1.5, 0, "MD glow width"); near(MD.glowAlpha(), .8, 0, "MD glow alpha");
        near(BASIC.inner(), .03, 0, "mining inner radius"); near(BASIC.outer(), .045, 0, "mining outer radius");
        near(BASIC.glowWidth(), .3, 0, "mining glow width"); near(BASIC.glowAlpha(), .5, 0, "mining glow alpha");
        for (Spec spec : List.of(MD, BASIC)) {
            for (long age = 0; age <= 2600; age++) checkCurve(spec, age);
            for (long age = spec.lifeMillis() - 1000; age <= spec.lifeMillis() + 100; age++) checkCurve(spec, age);
        }
        for (int ticks = 0; ticks <= 150; ticks++) near(walkSpeed(ticks), .1F - ticks * .001F, 0, "unclamped source walking speed");
        ok(walkSpeed(101) < 0, "negative source walking speed beyond100 preserved");
        ok(width(MD, MD.lifeMillis() + 1, 0) < 0 && alpha(MD, MD.lifeMillis() + 1) < 0, "base source formula not silently clamped");
    }
    private static void checkCurve(Spec s, long age) {
        long life = s.lifeTicks() * 50L;
        double expectedLength = age < 200 ? (double)age / 200 : 1;
        double expectedAlpha = age > life - s.blendOut() ? 1 - (double)(age + s.blendOut() - life) / s.blendOut() : 1;
        for (double wiggle : new double[] {0, .02, .05, .1}) {
            double expectedWidth = wiggle + (age > life - 300 ? 1 - (double)(age + 300 - life) / 300 : 1);
            near(lengthScale(s, age), expectedLength, 0, "length ramp200"); near(alpha(s, age), expectedAlpha, 0, "source alpha curve");
            near(width(s, age, wiggle), expectedWidth, 0, "base width wiggle+shrink300");
            near(glowAlpha(s, age, wiggle), s.glowAlpha() * expectedAlpha * ((1 - .1 + wiggle) * expectedAlpha), 1E-15, "double alpha glow modulation");
        }
    }
    private static Point originalDirection(float yaw, float pitch) {
        float y = yaw / 180F * (float)Math.PI, p = pitch / 180F * (float)Math.PI;
        double x = -sin(y) * cos(p), z = cos(y) * cos(p), h = -sin(p), n = Math.sqrt(x * x + h * h + z * z);
        return new Point(x / n, h / n, z / n);
    }
    private static Point originalFix(Point dir, boolean firstPerson, boolean glow) {
        float yaw = (float)(-Math.atan2(dir.x(), dir.z()) * 180 / Math.PI);
        float angle = glow ? (float)((270F - yaw) / 180F * Math.PI) : (270F - yaw) * (float)Math.PI / 180F;
        double x = firstPerson ? -.05 : .15, y = firstPerson ? -.25 : -.8, z = firstPerson ? .2 : .23;
        return new Point(x * cos(angle) + z * sin(angle), y, z * cos(angle) - x * sin(angle));
    }
    private static void coordinates() {
        Point feet = new Point(12, 64, -8), eye = feet.add(new Point(0, 1.62, 0)), camera = new Point(3, 72, 4);
        for (int yaw = -720; yaw <= 720; yaw += 7) for (int pitch = -90; pitch <= 90; pitch += 5) {
            Point dir = originalDirection(yaw, pitch);
            near(direction(yaw, pitch), dir, 3E-16, "Motion3D float sine look");
            float rayYaw = (float)(-Math.atan2(dir.x(), dir.z()) * 180 / Math.PI);
            float rayPitch = (float)(-Math.atan2(dir.y(), Math.sqrt(dir.x() * dir.x() + dir.z() * dir.z())) * 180 / Math.PI);
            near(entityDirection(dir), originalDirection(rayYaw, rayPitch), 3E-16, "EntityRayBase float angle roundtrip");
            for (boolean local : new boolean[] {false, true}) {
                near(mainOrigin(eye, local), eye.add(new Point(local ? 0 : 1.6, 0, 0)), 0, "main observer source+X quirk");
                Endpoints mine = mineEndpoints(feet, 1.62, yaw, pitch, local);
                near(mine.from(), feet.add(new Point(0, 1.6, 0)), 0, "legacy local eye/modern feet mining start bridge");
                near(mine.to(), eye.add(new Point(local ? 0 : 1.6, 0, 0)).add(dir.scale(15)), 3E-14, "mining Motion3D end15 vs server range10");
            }
            for (boolean fp : new boolean[] {false, true}) {
                near(viewFix(dir, fp), originalFix(dir, fp, false), 0, "simple view radians order");
                near(glowViewFix(dir, fp), originalFix(dir, fp, true), 0, "glow view radians order");
                for (double length : new double[] {0, .001, .15, .3, 1, 10, 15, 30}) for (Spec spec : List.of(MD, BASIC)) {
                    Point actualDir = originalDirection(rayYaw, rayPitch);
                    Point from = eye.add(originalFix(dir, fp, true)), to = eye.add(actualDir.scale(length)), look = to.subtract(from).normalize();
                    Point up = fp ? new Point(0, 1, -.5).normalize() : eye.subtract(camera).cross(actualDir).normalize();
                    Glow glow = glow(eye, dir, length, camera, fp, spec.glowWidth());
                    near(glow.from(), from, 0, "fixed start only"); near(glow.to(), to, 2E-14, "end remains authoritative direction");
                    near(glow.mid1(), from.add(look.scale(spec.glowWidth())), 2E-14, "front texture base width");
                    near(glow.mid2(), to.subtract(look.scale(spec.glowWidth())), 2E-14, "tail texture base width"); near(glow.up(), up, 2E-14, "literal billboard side vector");
                    CylinderFrame frame = cylinderFrame(eye, dir, length, fp);
                    Point start = eye.add(originalFix(dir, fp, false)), delta = eye.add(actualDir.scale(length)).subtract(start);
                    double npitch = Math.atan2(delta.y(), Math.sqrt(delta.x() * delta.x() + delta.z() * delta.z()));
                    double nyaw = -Math.PI / 2 + Math.atan2(delta.x(), delta.z());
                    Point p = new Point(2, .7, -.4);
                    // Independent literal glRotateY(nyaw)*glRotateZ(npitch) matrix multiplication.
                    double x1 = p.x() * Math.cos(npitch) - p.y() * Math.sin(npitch), y1 = p.x() * Math.sin(npitch) + p.y() * Math.cos(npitch);
                    Point expected = start.add(new Point(x1 * Math.cos(nyaw) + p.z() * Math.sin(nyaw), y1, -x1 * Math.sin(nyaw) + p.z() * Math.cos(nyaw)));
                    near(frame.apply(new ClassicMeltdownerStarterTimeline.Point(p.x(), p.y(), p.z())), expected, 3E-14, "source cylinder transform matrix");
                    near(frame.origin(), start, 0, "cylinder source hand start");
                }
            }
        }
        Point reflector = new Point(-4, 70, 8), look = new Point(1, 0, 0);
        near(reflectionOrigin(eye, look, reflector), eye.add(look.scale(reflector.subtract(eye).length())), 0, "reflected origin is projection by eye distance, not reflector coordinates");
        ok(!reflectionOrigin(eye, look, reflector).equals(reflector), "reflection source projection quirk");
    }
    private static void geometry() {
        for (double length : new double[] {0, .001, .1, .3, 1, 10, 15, 30}) for (double radius : new double[] {.03, .045, .17, .22}) for (double fix : new double[] {.98, 1}) {
            List<ClassicMeltdownerStarterTimeline.Quad> quads = cylinder(length, radius, fix); ok(quads.size() == 108, "12divisions/4sqrt head slices");
            for (int i = 0; i < quads.size(); i++) {
                var q = quads.get(i); int side = i < 48 ? i % 12 : i < 60 ? i - 48 : (i - 60) % 12;
                int slice = i < 48 ? i / 12 : i >= 60 ? (i - 60) / 12 : -1;
                double a = side * Math.PI * 2 / 12, b = (side + 1) * Math.PI * 2 / 12, x0, x1, r0, r1; boolean back = i >= 60;
                if (slice < 0) { x0 = radius; x1 = length; r0 = r1 = radius; }
                else {
                    double offset = radius * (1 - fix); r0 = radius * Math.sqrt(slice / 4.0); r1 = radius * Math.sqrt((slice + 1) / 4.0);
                    x0 = offset + radius * fix * slice / 4.0; x1 = offset + radius * fix * (slice + 1) / 4.0;
                    if (back) { x0 = length + radius - x0; x1 = length + radius - x1; }
                }
                Point[] expect = {ring(x0, r0, a, back), ring(x1, r1, a, back), ring(x1, r1, b, back), ring(x0, r0, b, back)};
                var actual = List.of(q.a(), q.b(), q.c(), q.d());
                for (int v = 0; v < 4; v++) { var p = actual.get(v).point(); near(new Point(p.x(), p.y(), p.z()), expect[v], 8E-15, "cap/body full vertex differential"); }
            }
        }
    }
    private static Point ring(double x, double r, double a, boolean back) { return new Point(x, r * Math.sin(a), r * Math.cos(a) * (back ? -1 : 1)); }
    private static void particlesAndRandom() {
        Random charge = new Random(52), expected = new Random(52), mining = new Random(91);
        for (int i = 0; i < 10000; i++) {
            ok(chargeParticleCount(charge) == 0, "Scala positive to0 charge range empty"); expected.nextInt(1);
            ok(charge.nextLong() == expected.nextLong(), "empty charge range still consumes rangei RNG");
            ok(mineParticleCount(mining) == 3, "Scala0 to2 mining inclusive3");
        }
        for (boolean local : new boolean[] {false, true}) {
            Point originalPosition = new Point(12, 64, -8);
            near(chargeParticleSourcePosition(originalPosition, local, .9, Math.PI / 3, -.7),
                    originalPosition.add(new Point(.9 * Math.sin(Math.PI / 3), (local ? 0 : 1.6) - .7, .9 * Math.cos(Math.PI / 3))), 0, "dormant charge source body placement");
            Endpoints originalMine = mineSourceEndpoints(originalPosition, .12, 0, 0, local);
            near(originalMine.from(), new Point(12, 64 + (local ? 0 : 1.6), -8), 0, "literal old entity mine start");
            near(originalMine.to(), new Point(12 + (local ? 0 : 1.6), 64.12, 7), 0, "literal old Motion3D mine end");
        }
        Point sentinel = mineParticlePosition(-1, -1, -1, -.2, 1.2, .7);
        near(sentinel, new Point(-1.2, .2, -.3), 1E-15, "literal sentinel particle location preserved");
        Point position = new Point(0, 0, 0), velocity = new Point(.06, .06, -.06);
        for (int tick = 1; tick <= 80; tick++) {
            position = particleStep(position, velocity, true); velocity = velocity.add(new Point(0, -.01, 0));
            near(position.y(), .06 * tick - .01 * tick * (tick + 1) / 2, 3E-13, "Rigidbody subtract gravity before movement");
        }
        for (int life = 25; life < 55; life++) for (int age = 0; age < life + 23; age++) {
            double expectedAlpha = age > life ? Math.max(0, 1 - (double)(age - life) / 20) : age < 5 ? (double)age / 5 : 1;
            near(ClassicMeltdownerStarterTimeline.particleAlpha(age, life), expectedAlpha, 0, "Md particle25..54/5fadein/20fadeout");
            ok(ClassicMeltdownerStarterTimeline.particleAlive(age, life) == (age <= life + 20), "zero alpha final tick still alive");
        }
        Random actualRng = new Random(104), originalRng = new Random(104); double width = 0, glow = 0; long last = 0;
        var wiggle = new ClassicMeltdownerStarterTimeline.RayWiggle();
        for (int frame = 0; frame < 10000; frame++) {
            long now = frame / 3 * 17L + 1;
            if (last != 0) {
                width += (now - last) * (-.4 + originalRng.nextDouble() * .8) / 1000;
                glow += (now - last) * (-.4 + originalRng.nextDouble() * .8) / 1000;
                width = Math.max(0, Math.min(.1, width)); glow = Math.max(0, Math.min(.1, glow));
            }
            last = now; wiggle.frame(now, actualRng);
            near(wiggle.width, width, 0, "source render width random walk"); near(wiggle.glow, glow, 0, "source render glow random walk");
        }
    }
    private static final Caster KEY = new Caster(7, new UUID(1, 2));
    private static void lifecycle() {
        permute(new int[] {0, 1, 2, 3, 4, 5}, 0);
        Tokens t = new Tokens(); Caster replacement = new Caster(7, new UUID(1, 3));
        ok(t.end(KEY, 99) && !t.start(KEY, 99), "end-before-start tombstone");
        ok(t.start(replacement, 1), "UUID replacement has distinct replay history");
        ok(!t.active(KEY, 1) && t.active(replacement, 1), "id alone cannot authorize replaced caster");
        for (int i = 0; i < 4094; i++) ok(t.end(new Caster(i + 100, new UUID(4, i)), 1), "history capacity");
        ok(!t.start(new Caster(10000, new UUID(5, 5)), 2), "capacity fail closed no tombstone eviction");
        ok(!t.start(KEY, 98), "old UUID tombstone survives capacity");
        t.clear(); ok(t.start(KEY, 1), "clear resets session replay history");
        ParticleSequence seq = new ParticleSequence(); ok(!seq.accept(-1) && seq.accept(0) && !seq.accept(0) && seq.accept(4) && !seq.accept(3), "particle monotonic dedupe");
        var input = new ClassicMeltdownerStarterTimeline.Input(); long a = input.press(); input.end();
        ok(input.accepts(a) && !input.mayShowStart(a), "early-release hidden terminal beam context");
        long b = input.press(); ok(!input.accepts(a) && input.accepts(b), "new press rejects previous ack");
        input.complete(a); ok(input.heldPending(), "stale end cannot end newer input");
        input.complete(b); ok(!input.heldPending() && !input.accepts(b), "server termination clears delegate active");
        input.clear(); ok(input.press() > b, "clear never recycles input nonces");
        var clock = new ClassicMeltdownerStarterTimeline.PauseClock(); clock.update(100, true); clock.update(200, true);
        clock.update(10000, false); clock.update(10020, true); ok(clock.elapsed() == 120, "pause time frozen");
        clock.update(9000, true); ok(clock.elapsed() == 120, "backward time ignored");
    }
    private static void permute(int[] events, int from) {
        if (from == events.length) {
            Tokens t = new Tokens(); long latest = 0; boolean ended = false;
            for (int event : events) {
                long token = event / 2 + 1; boolean end = event % 2 != 0;
                boolean wanted = token > latest || end && token == latest && !ended;
                ok((end ? t.end(KEY, token) : t.start(KEY, token)) == wanted, "720orders source-session gate oracle");
                if (wanted) { latest = token; ended = end; }
                for (long query = 1; query <= 3; query++) ok(t.active(KEY, query) == (query == latest && !ended), "all reordered active tokens");
            }
            return;
        }
        for (int i = from; i < events.length; i++) { int v = events[from]; events[from] = events[i]; events[i] = v; permute(events, from + 1); v = events[from]; events[from] = events[i]; events[i] = v; }
    }
    private static String sourceFixture(String name,String expected)throws Exception {
        try(var in=MeltdownerBeamVisualRegressionTest.class.getResourceAsStream("/classic-oracles/meltdowner-beams/"+name)) {
            if(in==null)throw new AssertionError("Missing canonical GPL-noticed source fixture "+name);
            byte[] bytes=in.readAllBytes();String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            ok(hash.equals(expected),"canonical source fixture SHA256 "+name);
            return new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    private static void sourceBindings() throws Exception {
        Path stage = Path.of(System.getProperty("academy.mdbeam.stage", ".staging/meltdowner-beams"));
        Path adapter = stage.resolve("src/main/java/cn/academy/port/client/ClassicMeltdownerBeamEffects.java");
        if (!Files.isRegularFile(adapter)) adapter = Path.of("src/main/java/cn/academy/port/client/ClassicMeltdownerBeamEffects.java");
        String source = Files.readString(adapter);
        for (String fragment : new String[] {"tag.hasUUID(\"entity_uuid\")", "current.nonce != nonce", "current.key.equals(key)", "callbackEpoch != epoch",
                "mc.player != local", "mc.getConnection() != connection", "input(skill).mayShowStart(nonce)", "current.particleSequence.accept(index)", "current.rayIndices.get(index)",
                "current.hide(mc)", "CastPoseClient.worldHandAnchor", "caster.getYHeadRot()", "setWalkingSpeed(walkSpeed(c.ticks))", "setWalkingSpeed(.1F)",
                "chargeParticleCount(RANDOM)", "mineParticleCount(RANDOM)", "tickRay(ray)", "ray.ticks <= ray.spec.lifeTicks()", "RAY_EYE_HEIGHT", "random(-.06, .06)", "random(-.03, .03)",
                "width(ray.spec, age, ray.wiggle.width)", "glowAlpha(ray.spec, age, ray.wiggle.glow)", "ray.spec.inner() * width, .98", "ray.spec.outer() * width, 1",
                "230 / 255.0 * alpha", "50 / 255.0 * alpha", "216, 248, 216", "106, 242, 106", "SoundSource.MASTER", "looping = loop", "pitch = 1F",
                "PERFORM_SOUND, PERFORM_VOLUME", "MINE_START_SOUND, MINE_START_VOLUME", "CHARGE_VOLUME, false", "MINE_LOOP_VOLUME, true",
                "ClassicMeltdownerStarterEffects.CutoffShader::get", "GameRenderer::getPositionTexColorShader", "COLOR_DEPTH_WRITE", "PARTICLES_TARGET", "buffers.endBatch(type)"})
            ok(source.contains(fragment), "actual adapter binding " + fragment);
        ok(!source.contains("PacketDistributor") && !source.contains("AbilityStorage") && !source.contains("hurt(") && !source.contains("addEffect(") && !source.contains("setBlock("), "no gameplay/network side effects");
        String md = sourceFixture("Meltdowner.scala.txt", "286a351ca7cfd9510281e7a3369a2c6cd379f5d73df69d4cb86c1af9b4b85d33");
        ok(md.contains("rangei(2, 3) to 0") && !md.contains("by -1"), "charge original empty ascending range");
        ok(md.contains("0.1f - ticks * 0.001f") && md.contains("setPlayerWalkSpeed(0.1f)"), "source local slowdown/restoration");
        ok(md.contains("VecUtils.entityHeadPos(ctx.player).distanceTo(VecUtils.entityHeadPos(reflector))") && md.contains("mo.setPosition(spawnPos.xCoord"), "reflection eye-projection source");
        String mine = sourceFixture("MineRaysBase.scala.txt", "39d791417af63ffeaaec1f57e4fbb79cc613474ba0353ffa277bb703a70922eb");
        ok(mine.contains("for(i <- 0 to max)") && mine.contains("rb.gravity = 0.01") && mine.contains("rb.blockFil = null") && mine.contains("ray.setDead()"), "source mine count/gravity/no collision/immediate death");
        String base = sourceFixture("EntityRayBase.java.txt", "c92031238e72442a0a484907336867eef1197013c5013588ede6a614173cdbd8");
        ok(base.contains("widthShrinkTime = 300") && base.contains("return widthWiggle +") && base.contains("viewOptimize = true"), "ray base source defaults");
    }
    public static void main(String[] args) throws Exception {
        curves(); coordinates(); geometry(); particlesAndRandom(); lifecycle(); sourceBindings();
        System.out.println("PASS Meltdowner/MineRayBasic source-differential visuals: " + checks + " assertions; no rendered-pixel/audio/runtime parity claim");
    }
}
