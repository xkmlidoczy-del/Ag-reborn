/* Standalone source-differential tests. Geometry/parameters, not GPU screenshots. */
package cn.academy.port.client;

import cn.academy.port.testing.CheckpointSourceFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import static cn.academy.port.client.ClassicMeltdownerStarterTimeline.*;

public final class MeltdownerStarterVisualRegressionTest {
    private static long checks;
    private static final float[] SINES = new float[65536];
    static { for (int i = 0; i < 65536; i++) SINES[i] = (float)Math.sin((double)i * Math.PI * 2 / 65536); }
    private static float sin(float n) { return SINES[(int)(n * 10430.378F) & 65535]; }
    private static float cos(float n) { return SINES[(int)(n * 10430.378F + 16384F) & 65535]; }
    private static void ok(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    private static void near(double actual, double expected, double tolerance, String reason) { ok(Math.abs(actual - expected) <= tolerance, reason + ": " + actual + " vs " + expected); }
    private static void near(Point actual, Point expected, double tolerance, String reason) { near(actual.x(), expected.x(), tolerance, reason + "X"); near(actual.y(), expected.y(), tolerance, reason + "Y"); near(actual.z(), expected.z(), tolerance, reason + "Z"); }
    private static double originalBallAlpha(int life, long dt) {
        int lifeMS = life * 50; final int blendTime = 150; long burstTime = 400;
        if (dt > lifeMS - blendTime) return Math.max(0, 1F + (0F - 1F) * ((float)(dt - (lifeMS - blendTime)) / blendTime));
        if (dt > lifeMS - burstTime) return .6 + (1 - .6) * ((double)(dt - (lifeMS - burstTime)) / (burstTime - blendTime));
        if (dt < 300) return 0 + (.6 - 0) * ((double)dt / 300);
        return .6;
    }
    private static float originalBallSize(int life, long dt) {
        int lifeMS = life * 50;
        if (dt > lifeMS - 100) return Math.max(0, 1.5F + (0F - 1.5F) * ((float)(dt - (lifeMS - 100)) / 100));
        if (dt > lifeMS - 300) return 1F + (1.5F - 1F) * ((float)(dt - (lifeMS - 300)) / 200);
        return 1;
    }
    private static void balls() {
        for (int life : new int[] {5, 20, 50, BALL_LIFE}) {
            for (long age = 0; age <= 1200; age++) {
                near(ballAlpha(life, age), originalBallAlpha(life, age), 0, "ball branch order alpha");
                near(ballSize(life, age), originalBallSize(life, age), 0, "ball branch order size");
            }
            for (long age = life * 50L - 1000; age <= life * 50L + 1000; age++) {
                near(ballAlpha(life, age), originalBallAlpha(life, age), 1E-15, "ball terminal alpha");
                near(ballSize(life, age), originalBallSize(life, age), 0, "ball terminal size");
            }
        }
        for (long age = 0; age < 200_000; age += 7) {
            float phase = age / 300F;
            near(ballJitter(age), new Point(.03 * sin(phase), .04 * cos((float)(phase * 1.4 + Math.PI / 3.5)), .03 * cos(phase)), 0, "source lookup jitter");
        }
        for (boolean local : new boolean[] {false, true}) for (double y = .4; y <= 1.8; y += .02) {
            Point server = new Point(.8, y, .3);
            near(sourceBallOffset(server, local), new Point(.8, local ? y - 1.6 : y, .3), 0, "identity-based source correction");
            near(modernBallOffset(server, local), server, 2E-16, "modern feet/eye bridge");
        }
        for (double size : new double[] {.01, .5, .7, 1, 2}) {
            Quad orb = billboard(size, false), shield = billboard(size, true), particle = particleQuad(size);
            near(orb.a().point(), new Point(-size / 2, -size / 4, 0), 0, "RenderIcon lower");
            near(orb.c().point(), new Point(size / 2, size * .75, 0), 0, "RenderIcon upper");
            near(shield.a().point(), new Point(-size / 2, -size / 2, 0), 0, "MeshUtils lower");
            near(shield.c().point(), new Point(size / 2, size / 2, 0), 0, "MeshUtils upper");
            ok(orb.a().v() == 1 && orb.c().v() == 0, "orb conventional UV");
            ok(shield.a().v() == 0 && shield.c().v() == 1, "shield source flipped UV");
            ok(particle.a().v() == 1 && particle.c().v() == 0, "centered particle conventional UV");
        }
    }
    private static void shield() {
        for (int ticks = 0; ticks <= 300; ticks++) {
            ShieldFrame frame = shieldFrame(ticks);
            float expectedSize = 1.8F * (.2F + (1F - .2F) * Math.min(ticks / 15F, 1F));
            float expectedSpeed = .8F + (2F - .8F) * Math.min(ticks / 30F, 1F);
            near(frame.size(), expectedSize, 0, "shield size15 ticks");
            near(frame.unusedAlpha(), Math.min(ticks / 6F, 1), 0, "computed but unused shield alpha");
            near(frame.rotationSpeed(), expectedSpeed, 0, "rotation30 ticks");
            for (long dt : new long[] {0, 1, 16, 50, 200, 1000, 5000}) {
                float expected = 350F; expected += expectedSpeed * dt; if (expected >= 360) expected -= 360;
                near(shieldRotation(350, ticks, dt), expected, 0, "single subtract360 spin");
            }
        }
        ok(shieldRotation(0, 30, 1000) == 1640F, "long-frame spin intentionally exceeds360");
        Point feet = new Point(12, 64, -8);
        for (int yaw = -720; yaw <= 720; yaw += 7) for (int pitch = -90; pitch <= 90; pitch += 5) for (boolean local : new boolean[] {true, false}) {
            float y = yaw / 180F * (float)Math.PI, p = pitch / 180F * (float)Math.PI;
            double dx = -sin(y) * cos(p), dy = -sin(p), dz = cos(y) * cos(p), len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            Point expected = new Point(12 + (local ? 0 : 1.6) + dx / len, 64 + 1.62 - .5 + dy / len, -8 + dz / len);
            near(shieldPosition(feet, 1.62, yaw, pitch, local), expected, 1.5E-14, "Motion3D literal center");
            near(shieldParticleCenter(feet, 1.62, yaw, pitch, local), expected.add(new Point(0, .5, 0)), 1.5E-14, "shield particle center");
        }
        near(shieldPosition(feet, 1.62, 0, 0, false).x() - shieldPosition(feet, 1.62, 0, 0, true).x(), 1.6, 1E-15, "observer X quirk");
        near(STARTUP_VOLUME, .5, 0, "source startup volume"); near(LOOP_VOLUME, 1, 0, "MovingSound default loop volume");
    }
    private static void ray() {
        for (long age = 0; age <= 700; age++) {
            double length = age < 200 ? (double)age / 200 : 1;
            double alpha = age > 700 - 400 ? 1 - (double)(age + 400 - 700) / 400 : 1;
            double width = age > 700 - 500 ? 1 - Math.max(0, Math.min(1, (double)(age - (700 - 500)) / 500)) : 1;
            near(rayLengthScale(age), length, 0, "ray blend-in200"); near(rayAlpha(age), alpha, 0, "ray fade400"); near(rayWidth(age), width, 0, "ray shrink500 override");
            for (double wiggle : new double[] {0, .05, .1}) near(rayGlowAlpha(age, wiggle), .5 * alpha * (1 - .1 + wiggle) * alpha, 0, "glow alpha squared");
            for (double radius : new double[] {.03 * width, .045 * width}) for (double fix : new double[] {.98, 1}) {
                List<Quad> quads = cylinder(15 * length, radius, fix); ok(quads.size() == 108, "12body+48front+48back");
                for (int i = 0; i < quads.size(); i++) {
                    Quad quad = quads.get(i); int side = i < 48 ? i % 12 : i < 60 ? i - 48 : (i - 60) % 12;
                    int slice = i < 48 ? i / 12 : i >= 60 ? (i - 60) / 12 : -1;
                    double angle0 = side * Math.PI * 2 / 12, angle1 = (side + 1) * Math.PI * 2 / 12;
                    double x0, x1, r0, r1; boolean rear = i >= 60;
                    if (slice < 0) { x0 = radius; x1 = 15 * length; r0 = r1 = radius; }
                    else { r0 = radius * Math.sqrt(slice / 4.0); r1 = radius * Math.sqrt((slice + 1) / 4.0); double offset = radius * (1 - fix); x0 = offset + radius * fix * (slice / 4.0); x1 = offset + radius * fix * ((slice + 1) / 4.0); if (rear) { x0 = 15 * length + radius - x0; x1 = 15 * length + radius - x1; } }
                    Point[] expected = {ringPoint(x0, r0, angle0, rear), ringPoint(x1, r1, angle0, rear), ringPoint(x1, r1, angle1, rear), ringPoint(x0, r0, angle1, rear)};
                    Vertex[] actual = {quad.a(), quad.b(), quad.c(), quad.d()};
                    for (int v = 0; v < 4; v++) near(actual[v].point(), expected[v], 3E-15, "every cylinder vertex including mirrored cap");
                }
            }
        }
    }
    private static Point ringPoint(double x, double r, double a, boolean rear) { return new Point(x, r * Math.sin(a), r * Math.cos(a) * (rear ? -1 : 1)); }
    private static void randomFrames() {
        Random entity = new Random(74), global = new Random(1997), originalEntity = new Random(74), originalGlobal = new Random(1997);
        BallWiggle ball = new BallWiggle(); double alpha = .8, acceleration = 0; int texture = 0; long last = 0;
        RayWiggle ray = new RayWiggle(); Random rayRandom = new Random(291), originalRay = new Random(291); double width = 0, glow = 0; long rayLast = 0;
        for (int frame = 0; frame < 10000; frame++) {
            long now = frame / 3 * 17L; // Include same-time frames and source lastTime0 sentinel.
            if (last != 0) { if (originalEntity.nextInt(8) < 3) acceleration = -4 + originalGlobal.nextDouble() * 8; alpha += acceleration * (now - last) / 1000; if (alpha > 1) alpha = 1; if (alpha < 0) alpha = 0; }
            if (originalEntity.nextInt(8) < 2) texture = originalEntity.nextInt(5); last = now;
            ball.frame(now, entity, global); near(ball.alpha, alpha, 0, "ball random frame alpha"); near(ball.acceleration, acceleration, 0, "ball random frame acceleration"); ok(ball.texture == texture, "ball random frame texture");
            if (rayLast != 0) { width += (now - rayLast) * (-.4 + originalRay.nextDouble() * .8) / 1000; width = Math.max(0, Math.min(.1, width)); glow += (now - rayLast) * (-.4 + originalRay.nextDouble() * .8) / 1000; glow = Math.max(0, Math.min(.1, glow)); } rayLast = now;
            ray.frame(now, rayRandom); near(ray.width, width, 0, "inherited width random consumed even though unused"); near(ray.glow, glow, 0, "ray glow random");
        }
    }
    private static void particles() {
        for (int life = 25; life < 55; life++) for (int age = 0; age <= life + 22; age++) {
            double alpha = age > life ? Math.max(0, 1 - (double)(age - life) / 20) : age < 5 ? (double)age / 5 : 1;
            near(particleAlpha(age, life), alpha, 0, "particle source fade"); ok(particleAlive(age, life) == (age <= life + 20), "particle zero-alpha last tick");
        }
    }
    private static void inputOrdering() {
        Input input = new Input(); ok(!input.heldPending(), "no physical pending context"); ok(input.accepts(0) && input.mayShowStart(0), "internal input0"); long a = input.press(); ok(input.heldPending(), "pending SingleKeyDelegate ACTIVE before ack"); ok(a > 0 && input.accepts(a) && !input.accepts(0), "physical press"); input.end(); ok(!input.heldPending(), "early release retires pending ACTIVE"); ok(input.accepts(a) && !input.mayShowStart(a), "early release only terminal correlation"); long b = input.press(); ok(b > a && !input.accepts(a), "new press replaces pending old"); input.complete(a); ok(input.accepts(b), "old end cannot complete new press"); input.complete(b); ok(!input.heldPending(), "authoritative end clears ACTIVE despite held physical key"); ok(!input.accepts(b) && !input.accepts(0), "completed physical context"); input.clear(); long c = input.press(); ok(c > b && !input.accepts(b), "teardown does not reuse nonce");
        Tokens tokens = new Tokens(); ok(tokens.end(1, 10), "end before start allowed"); ok(!tokens.start(1, 10) && !tokens.start(1, 9), "terminated token cannot reanimate"); ok(tokens.start(1, 11) && tokens.active(1, 11), "new token accepted"); ok(!tokens.end(1, 10) && tokens.active(1, 11), "stale end cannot kill newer token"); ok(!tokens.start(1, 11), "duplicate start rejected"); ok(tokens.end(1, 11) && !tokens.active(1, 11) && !tokens.end(1, 11), "one terminal event");
        // Exhaust all6! event orders for three tokens and compare to an independent highest-token oracle.
        permute(new int[] {0, 1, 2, 3, 4, 5}, 0);
        for (int id = 2; id <= 4096; id++) ok(tokens.end(id, 1), "history fill");
        ok(!tokens.start(5000, 2) && !tokens.end(5000, 2), "history cap fails closed"); ok(!tokens.start(1, 10), "cap never evicts old tombstone"); tokens.clear(); ok(tokens.start(1, 1), "session clear");
        PauseClock clock = new PauseClock(); clock.update(100, true); clock.update(200, true); clock.update(10000, false); clock.update(10020, true); ok(clock.elapsed() == 120, "pause does not accumulate huge lag"); clock.update(9000, true); ok(clock.elapsed() == 120, "backward clock ignored"); clock.clear(); ok(clock.elapsed() == 0, "session clock reset");
    }
    private static void permute(int[] events, int offset) {
        if (offset == events.length) {
            Tokens actual = new Tokens(); long highest = 0; boolean ended = false;
            for (int event : events) {
                long token = event / 2 + 1; boolean end = event % 2 == 1;
                boolean accepted = token > highest || end && token == highest && !ended;
                boolean result = end ? actual.end(7, token) : actual.start(7, token);
                ok(result == accepted, "all reordered start/end acceptance"); if (accepted) { highest = token; ended = end; }
                for (long query = 1; query <= 3; query++) ok(actual.active(7, query) == (query == highest && !ended), "all reordered active token");
            }
            return;
        }
        for (int i = offset; i < events.length; i++) { int swap = events[offset]; events[offset] = events[i]; events[i] = swap; permute(events, offset + 1); swap = events[offset]; events[offset] = events[i]; events[i] = swap; }
    }
    private static void sourceBindings() throws Exception {
        // Verify CURRENT source from the shipped project, not a frozen or duplicated modern fixture.
        Path sourceRoot = Path.of(System.getProperty("academy.md.sourceRoot", "src/main/java"));
        String source = Files.readString(sourceRoot.resolve("cn/academy/port/client/ClassicMeltdownerStarterEffects.java"));
        for (String fragment : new String[] {"callbackEpoch != epoch", "mc.player != local", "mc.getConnection() != connection", "input(skill).mayShowStart(nonce)", "current.rayIndices.get(index)", "current.balls.remove(index)", "current.hide(mc)", "caster.getYHeadRot()", "COLOR_DEPTH_WRITE", "CutoffShader::zero", "CutoffShader::get", "PARTICLES_TARGET", "buffers.endBatch(type)", "SoundSource.MASTER", "looping = owner != null", "pitch = 1F", "230 / 255.0 * alpha", "50 / 255.0 * alpha", "216, 248, 216", "106, 242, 106", ".03 * width, .98", ".045 * width, 1", ".3 * width, glow"}) ok(source.contains(fragment), "runtime binding " + fragment);
        ok(!source.contains("PacketDistributor") && !source.contains("AbilityStorage") && !source.contains("hurt(") && !source.contains("addEffect("), "client cannot mutate gameplay");
        ok(source.contains("ClassicMeltdownerStarterTimeline.particleQuad(particle.size)"), "particle exact UV binding");
        String fragment = CheckpointSourceFixtures.resourceText("/assets/academy/shaders/core/classic_meltdowner_cutoff05.fsh"); ok(fragment.contains("color.a <= 0.05") && fragment.contains("discard"), "strict source cutoff");
        String zero = CheckpointSourceFixtures.resourceText("/assets/academy/shaders/core/classic_meltdowner_gt0.fsh"); ok(zero.contains("color.a <= 0.0") && zero.contains("discard"), "strict source ball alphaGT0 avoids transparent depth writes");
        ok(source.contains("Material.type(MD_PARTICLE, true, false)"), "source particle GT.05 material");
        ok(source.contains("cull ? GameRenderer::getPositionTexColorShader"), "cylinder restored GE.1 alpha threshold");
        String shield = CheckpointSourceFixtures.pinnedText("academycraft-1.0.7/RenderMdShield.java.txt");
        ok(shield.contains("float alpha =") && !shield.contains("glColor") && !shield.contains("color.a"), "source shield alpha genuinely unused");
        ok(shield.contains("entity.rotation -= 360f") && !shield.contains("% 360"), "source single rotation wrap");
        String scatter = CheckpointSourceFixtures.pinnedText("academycraft-1.0.7/ScatterBomb.scala.txt"); ok(scatter.contains("viewOptimize = false"), "source ray view optimization disabled");
        String motion = CheckpointSourceFixtures.pinnedText("lambdalib-1.2.3/Motion3D.java.txt"); ok(motion.contains("px += 1.6"), "observer X quirk source provenance");
    }
    public static void main(String[] args) throws Exception { balls(); shield(); ray(); randomFrames(); particles(); inputOrdering(); sourceBindings(); System.out.println("PASS Meltdowner starter source-differential visuals: " + checks + " assertions; no GPU/client/runtime parity claim"); }
}
