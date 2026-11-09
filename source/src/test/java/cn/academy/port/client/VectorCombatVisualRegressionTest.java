package cn.academy.port.client;

import cn.academy.port.client.vectororacle.CubicCurve;
import cn.academy.port.client.vectororacle.ImprovedNoise;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

/** Compiled upstream curve/noise differential plus pinned random/geometry/lifecycle inputs. No native rendering. */
public final class VectorCombatVisualRegressionTest {
    private static int assertions;
    private static final Path ROOT = Path.of(System.getProperty("academy.vector.root", "."));
    private static final Path ORACLE = ROOT.resolve("src/test/resources/classic-oracles/vector-combat-progression");
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static void near(double actual, double expected, String name) { check(Double.isFinite(actual) && Math.abs(actual - expected) < 1E-11, name + ": " + actual + " != " + expected); }
    private static String source(String project, String suffix) throws Exception {
        Path path;
        try (var files = Files.walk(ORACLE.resolve("visual-source/" + project))) {
            path = files.filter(p -> p.toString().endsWith("/" + suffix + ".txt")).findFirst().orElseThrow();
        }
        return Files.readString(path);
    }
    private static CubicCurve curve(String text, String name) {
        CubicCurve result = new CubicCurve(); var matcher = Pattern.compile(Pattern.quote(name) + "\\.addPoint\\(([-.0-9]+),\\s*([-.0-9]+)\\)").matcher(text);
        while (matcher.find()) result.addPoint(Double.parseDouble(matcher.group(1)), Double.parseDouble(matcher.group(2)));
        check(result.pointCount() >= 2, "source curve has parsed independent knots: " + name); return result;
    }
    public static void main(String[] args) throws Exception {
        sourceAndMedia(); waveAndHands(); tornado(); blood(); replay(); input(); clock(); wiring();
        System.out.println("PASS " + assertions + " vector combat source/mesh/lifecycle assertions; native pixels and audio unverified");
    }
    private static void sourceAndMedia() throws Exception {
        String manifest = Files.readString(ORACLE.resolve("visual-source-manifest.json"));
        check(manifest.contains("00d19ec0cf538f61c1095c9292f5ee6863db4521"), "AcademyCraft source commit pinned");
        // Verify every complete raw source and media file without consulting .reference.
        for (String name : List.of("visual-source-manifest.json", "visual-media-manifest.json")) {
            String json = Files.readString(ORACLE.resolve(name));
            var records = Pattern.compile("\\{[^{}]*\\\"fixture\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[^{}]*\\\"sha256\\\"\\s*:\\s*\\\"([0-9a-f]{64})\\\"[^{}]*}", Pattern.DOTALL).matcher(json);
            int count = 0;
            while (records.find()) { count++; Path fixture = ORACLE.resolve(records.group(1));
                check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(fixture))).equals(records.group(2)), "raw fixture SHA: " + records.group(1));
                if (name.contains("media")) {
                    String production = records.group(1).replace("visual-media/", "src/main/resources/assets/academy/");
                    check(Files.mismatch(fixture, ROOT.resolve(production)) == -1, "runtime original media bytes retained: " + production);
                }
            }
            check(count == (name.contains("source") ? 45 : 24), "complete raw fixture manifest count");
        }
        check(Files.size(ORACLE.resolve("GPLv3-LICENSE.txt")) > 30000 && Files.size(ORACLE.resolve("LAMBDALIB-MIT-LICENSE.txt")) > 900, "license copies retained");
        String sounds = source("academycraft", "sounds.json");
        Path runtimeSounds = Path.of(System.getProperty("academy.vector.productionRoot", ".")).resolve("src/main/resources/assets/academy/sounds.json");
        String registry = Files.readString(runtimeSounds);
        for (String name : List.of("directed_blast", "storm_wing", "blood_retro")) check(sounds.contains("vecmanip." + name) && sounds.contains("vecmanip/" + name), "source sound registry entry " + name);
        for (String name : List.of("directed_blast", "storm_wing", "blood_retro")) check(registry.contains("vecmanip." + name) && registry.contains("vecmanip/" + name), "existing shared runtime sound registry " + name);
        check(source("lambdalib", "MeshUtils.java").contains("{ x1, y1, 0 }"), "billboard source arguments are absolute endpoints");
    }
    private static void waveAndHands() throws Exception {
        String source = source("academycraft", "WaveEffect.scala"); CubicCurve alpha = curve(source, "alphaCurve"), size = curve(source, "sizeCurve");
        check(source.contains("val life = 15") && source.contains("rangei(8, 12)") && source.contains("idx * 2 + RandUtils.rangei(-1, 1)"), "source wave lifetimes/delays");
        check(source.contains("createBillboard(mesh, -.5, -.5, 1, 1)"), "asymmetrical original glow quad");
        for (int seed = 0; seed < 1000; seed++) {
            var actual = ClassicVectorCombatTimeline.waveRings(new Random(seed)); Random original = new Random(seed); int count = 2 + original.nextInt(1);
            check(actual.size() == count && count == 2, "source upper-exclusive blast ring count");
            for (int index = 0; index < count; index++) {
                int life = 8 + original.nextInt(4); double offset = index * 1.5 + ranged(original, -.3, .3), ringSize = ranged(original, .8, 1.2); int delay = index * 2 - 1 + original.nextInt(2);
                var ring = actual.get(index); check(ring.life() == life && ring.timeOffset() == delay, "source random life/delay order"); near(ring.offset(), offset, "source depth draw"); near(ring.size(), ringSize, "source size draw");
                for (int tick = 0; tick <= 15; tick++) {
                    near(ClassicVectorCombatTimeline.waveAlpha(tick, ring), .7 * Math.min(clamp(alpha.valueAt(tick / 15.0)), clamp(alpha.valueAt((tick - delay) / (double) life))), "compiled original wave curve");
                    near(ClassicVectorCombatTimeline.waveSize(tick, ring), ringSize * size.valueAt(Math.max(0, Math.min(1.62, tick / 20.0))), "compiled original ring size curve");
                    near(ClassicVectorCombatTimeline.waveDepth(tick, ring), tick / 40.0 + offset, "source ring forward travel");
                }
            }
        }
        check(ClassicVectorCombatTimeline.waveAlive(14) && !ClassicVectorCombatTimeline.waveAlive(15), "source wave dies at tick15");
        String presets = source("academycraft", "AnimPresets.scala"); int punchIndex = presets.indexOf("def createPunchAnim");
        String prepare = presets.substring(0, punchIndex), punch = presets.substring(punchIndex);
        CubicCurve px = curve(prepare, "curvex"), py = curve(prepare, "curvey"), pz = curve(prepare, "curvez"), prx = curve(prepare, "curverx");
        CubicCurve qx = curve(punch, "curvex"), qy = curve(punch, "curvey"), qz = curve(punch, "curvez"), qrx = curve(punch, "curverx"), qry = curve(punch, "curvery");
        for (int millis = 0; millis <= 10000; millis += 7) {
            double t = Math.min(2, millis / 150.0); var pose = ClassicVectorCombatTimeline.prepare(millis);
            near(pose.x(), px.valueAt(t), "source prepare X"); near(pose.y(), py.valueAt(t), "source prepare Y"); near(pose.z(), pz.valueAt(t), "source prepare Z"); near(pose.rotationX(), prx.valueAt(t), "source prepare rotation");
            double u = millis / 300.0; var hit = ClassicVectorCombatTimeline.punch(millis);
            near(hit.x(), qx.valueAt(u), "source punch X"); near(hit.y(), qy.valueAt(u), "source punch Y"); near(hit.z(), qz.valueAt(u), "source punch Z"); near(hit.rotationX(), qrx.valueAt(u), "source punch X rotation"); near(hit.rotationY(), qry.valueAt(u), "source punch Y rotation");
        }
    }
    private static void tornado() throws Exception {
        String originalSource = source("academycraft", "TornadoEffect.scala");
        check(originalSource.contains("val divide = 40") && originalSource.contains("val div = 20") && originalSource.contains("timeOffest = RNG.nextDouble() * 20"), "source mesh/timer inputs");
        for (int seed = 0; seed < 500; seed++) {
            Random random = new Random(seed); double offset = random.nextDouble() * 20, accum = 0, step = 2 / 40.0;
            var expected = new ArrayList<ClassicVectorCombatTimeline.TornadoRing>();
            while (accum < 2) { accum += step * (1 + random.nextGaussian() * .2); if (random.nextDouble() < 1) {
                expected.add(new ClassicVectorCombatTimeline.TornadoRing(accum, step * ranged(random, 1.8, 2.2), random.nextDouble() * 360, ranged(random, .9, 1.2)));
                if (random.nextDouble() < .35) expected.add(new ClassicVectorCombatTimeline.TornadoRing(accum, step * ranged(random, 1.8, 2.2), random.nextDouble() * 360, ranged(random, 1.2, 1.7)));
            }}
            var actual = ClassicVectorCombatTimeline.tornado(new Random(seed)); near(actual.timeOffset(), offset, "source tornado time draw"); check(actual.rings().equals(expected), "source Gaussian ring/double-layer random draw order");
            for (var ring : actual.rings()) {
                double ny = ring.y() / 2, time = seed / 7.0 - offset, drift = .3 + Math.pow(ny * 2, 1.4); var sample = ClassicVectorCombatTimeline.tornadoSample(ring, time);
                near(sample.dx(), ImprovedNoise.noise(ny, time * .1) * drift * .16 * 2, "compiled original Perlin dx");
                near(sample.dz(), ImprovedNoise.noise(ny, time * .1, 1) * drift * .16 * 2, "compiled original Perlin dz");
                near(sample.radius(), ((.5 + .3 * ImprovedNoise.noise(ny, .2 * time)) + .5 * Math.pow(1.5 * ny, 2) + ImprovedNoise.noise(ny)) * .16 * ring.sizeScale(), "compiled original Perlin radius");
                near(sample.rotation(), .1 * (1 + .5 * ny) * time + ring.phase(), "source unconverted UV phase");
            }
        }
        check(ClassicVectorCombatTimeline.wingTransforms().size() == 4, "four sub-tornadoes");
        near(ClassicVectorCombatTimeline.wingAlpha(false, 35, 70, -1), .35, "half charge alpha before material .7");
        near(ClassicVectorCombatTimeline.wingAlpha(true, 0, 70, -1), .7, "active wing alpha");
        near(ClassicVectorCombatTimeline.wingAlpha(true, 0, 70, 15), 0, "active15-tick fade endpoint");
        near(ClassicVectorCombatTimeline.wingAlpha(false, 35, 70, 10), .35, "source charge branch precedes cancellation fade");
        check(ClassicVectorCombatTimeline.wingAlive(15) && !ClassicVectorCombatTimeline.wingAlive(16), "source termination strict boundary");
        for (int seed = 0; seed < 2000; seed++) {
            Random random = new Random(seed); double theta = ranged(random, 0, Math.PI * 2), phi = ranged(random, -Math.PI, Math.PI), radius = ranged(random, 3, 8), rzx = radius * Math.sin(phi);
            var dust = ClassicVectorCombatTimeline.wingDust(new Random(seed));
            near(dust.x(), rzx * Math.cos(theta), "source dust X"); near(dust.y(), radius * Math.cos(phi), "source dust Y"); near(dust.z(), rzx * Math.sin(theta), "source dust Z");
            near(dust.vx(), Math.sin(theta) * .7F, "source dust X velocity"); near(dust.vy(), ranged(random, -.01F, .05F), "source dust Y velocity"); near(dust.vz(), -Math.cos(theta) * .7F, "source dust Z velocity");
        }
    }
    private static void blood() throws Exception {
        String skill = source("academycraft", "BloodRetrograde.scala"), spray = source("academycraft", "BloodSprayEffect.scala"), splash = source("academycraft", "EntityBloodSplash.java");
        check(skill.contains("List(0, 30, 45, 60, 80, -30, -45, -60, -80)") && skill.contains("rangei(6, 10)") && skill.contains("rangei(2, 3)"), "source blood counts/ray angles");
        check(spray.contains("isWall = dir == ForgeDirection.UP || dir == ForgeDirection.DOWN"), "source inverted wall folder selection");
        check(splash.contains("setColor4i(213, 29, 29, 200)") && splash.contains("++frame == SPLASH.length"), "source splash tint/frame expiry");
        check(source("lambdalib", "RenderIcon.java").contains("float f6 = 0.25F"), "source splash asymmetric anchor");
        for (int seed = 0; seed < 1000; seed++) {
            Random random = new Random(seed); int count = 6 + random.nextInt(4); var splashes = ClassicVectorCombatTimeline.splashes(new Random(seed), .6, 1.8);
            check(splashes.size() == count, "source splash count");
            for (var actual : splashes) { random.nextFloat(); double size = 1.4F + random.nextFloat() * (1.8F - 1.4F);
                near(actual.size(), size, "source splash constructor/override float order"); near(actual.x(), ranged(random, -1, 1) * .6, "source splash X"); near(actual.y(), ranged(random, 0, 1) * 1.8, "source splash Y"); near(actual.z(), ranged(random, -1, 1) * .6, "source splash Z"); }
            for (int side = 0; side < 6; side++) {
                Random rng = new Random(seed); var actual = ClassicVectorCombatTimeline.spray(new Random(seed), side);
                check(actual.texture() == rng.nextInt(10) && actual.texture() >= 0 && actual.texture() < 10, "source upper-exclusive texture ID");
                near(actual.size(), ranged(rng, 1.1, 1.4) * (side <= 1 ? 1 : .8), "source face size multiplier"); near(actual.rotation(), ranged(rng, 0, 360), "source spray rotation");
                near(actual.offsetX(), rng.nextGaussian() * .15, "source plane Gaussian X distribution"); near(actual.offsetY(), rng.nextGaussian() * .15, "source plane Gaussian Y distribution");
                check(ClassicVectorCombatTimeline.sprayUsesWall(side) == (side <= 1), "source folder inversion retained");
            }
        }
        check(ClassicVectorCombatTimeline.splashAlive(9) && !ClassicVectorCombatTimeline.splashAlive(10), "source ten splash frames");
        check(ClassicVectorCombatTimeline.sprayAlive(1200, false) && !ClassicVectorCombatTimeline.sprayAlive(1201, false) && !ClassicVectorCombatTimeline.sprayAlive(1, true), "source spray1200/air deletion");
    }
    private static void replay() {
        var tokens = new ClassicVectorCombatTimeline.Tokens(); String skill = "storm_wing";
        check(!tokens.start(skill, 1, 0), "reject nonpositive token"); check(tokens.start(skill, 1, 10) && !tokens.start(skill, 1, 10), "duplicate start");
        check(tokens.state(skill, 1, 10, 0) && !tokens.state(skill, 1, 10, 0) && tokens.state(skill, 1, 10, 1), "state index replay fence");
        check(tokens.end(skill, 1, 10, false) && !tokens.state(skill, 1, 10, 2) && !tokens.start(skill, 1, 10), "end fences state and start");
        check(tokens.start(skill, 1, 20) && !tokens.end(skill, 1, 10, false), "old end cannot retire newer toggle"); tokens.abort(skill, 1, 20);
        check(!tokens.perform(skill, 1, 20), "aborted hold cannot perform");
        check(tokens.perform("dir_blast", 1, 21) && !tokens.perform("dir_blast", 1, 21), "observer-first blast delivered once");
        check(tokens.end("dir_blast", 1, 21, false) && !tokens.perform("dir_blast", 1, 21), "post-action context termination retains action tombstone");
        check(!tokens.end("dir_blast", 1, 21, false), "duplicate post-perform end fenced");
        check(tokens.end("blood_retro", 1, 30, true) && tokens.perform("blood_retro", 1, 30) && !tokens.perform("blood_retro", 1, 30), "successful end before perform delivers once");
        check(tokens.end("blood_retro", 1, 40, false) && !tokens.perform("blood_retro", 1, 40), "failed blood end cancels action");
        check(tokens.start("dir_blast", 2, 100), "entity isolation"); tokens.abort("dir_blast", 2, 90); check(tokens.perform("dir_blast", 2, 100), "old abort cannot overwrite newer hold");
        check(tokens.start("blood_retro", 2, 100), "skill isolation");
        for (int i = 0; i < 2000; i++) check(tokens.start(skill, i + 100, i + 100), "bounded high-cardinality history");
        check(tokens.size() == 1024, "history bounded"); tokens.clear(); check(tokens.size() == 0 && tokens.start(skill, 1, 1), "new session resets server token namespace");
    }
    private static void input() {
        var input = new ClassicVectorCombatTimeline.InputNonce(); check(input.accepts(0) && !input.accepts(1), "internal nonce0 before physical input");
        long a = input.press(); input.release(); check(!input.held() && input.accepts(a), "release preserves pending acknowledgement");
        check(input.complete(a) && !input.accepts(a) && input.acceptsSuccess(a), "successful end permits reordered matching action");
        long b = input.press(); check(b > a && !input.acceptsSuccess(a) && input.accepts(b), "new press supersedes completed input");
        input.abort(); check(!input.accepts(b) && !input.acceptsSuccess(b) && !input.accepts(0), "abort cancels pre-ack and late success");
        input.clear(); check(input.accepts(0), "session resets input driving"); long c = input.press(); check(c > b, "nonce counters survive session clear");
        input.release(); check(input.complete(c), "matching release succeeds"); input.abort(); check(!input.acceptsSuccess(c), "explicit abort retires completed action authorization");
        long owner = input.press(), stopping = input.press(); input.release();
        check(input.complete(stopping) && !input.accepts(owner), "second toggle nonce retires independently of original context owner");
        long third = input.press(); check(!input.complete(stopping) && input.accepts(third), "delayed original end cannot retire third press");
    }
    private static void clock() {
        var clock = new ClassicVectorCombatTimeline.PauseClock(); check(clock.update(1000, true) == 0 && clock.update(1200, true) == 200, "running millisecond clock"); near(clock.absolute(), 1200, "source absolute tornado timer");
        check(clock.update(2000, false) == 200 && clock.update(3000, false) == 200 && clock.update(3010, true) == 200, "pause freezes hand/tornado clock with no jump");
        check(clock.update(3060, true) == 250 && clock.update(3050, true) == 250, "backward time bounded"); clock.clear(); check(clock.update(9999, true) == 0, "session clock reset");
    }
    private static void wiring() throws Exception {
        String adapter = Files.readString(ROOT.resolve("src/main/java/cn/academy/port/client/ClassicVectorCombatEffects.java"));
        check(adapter.contains("value = Dist.CLIENT") && adapter.contains("CompoundTag tag = data.copy()"), "client-only packet copy");
        check(adapter.contains("mc.level != level || mc.player != local") && adapter.contains("mc.getConnection() != connection") && adapter.contains("callbackEpoch != epoch") && adapter.contains("callbackEpoch++"), "queued callback session/explicit-clear fence");
        check(adapter.indexOf("input(skill).acceptsSuccess(nonce)") < adapter.indexOf("TOKENS.end(skill, id, token, successful)"), "local input gates replay history mutation");
        for (String hook : List.of("startLocal(String skill)", "releaseLocal(String skill)", "abortLocal(String skill)", "localNonce(String skill)", "anyActive()", "active(String skill)", "stormActive()", "localTicks(String skill)", "consumptionHint()", "void clear()")) check(adapter.contains(hook), "shared client hook " + hook);
        for (String kind : List.of("dir_blast_prepare", "dir_blast_abort", "dir_blast_perform", "storm_wing_start", "storm_wing_state", "storm_wing_end", "blood_retro_start", "blood_retro_end", "blood_retro_perform")) check(adapter.contains(kind), "recognized effect event " + kind);
        check(adapter.contains("RenderHandEvent") && adapter.contains("lastHandStack == event.getPoseStack()") && adapter.contains("ClassicVectorCombatTimeline.punch(age)"), "source hand transform uses port one-stack convention");
        check(adapter.contains("modelView.set(event.getModelViewMatrix())") && adapter.contains("modelView.popMatrix()") && adapter.contains("buffers.endBatch(type)"), "private world batches and scoped model-view");
        check(adapter.contains("RenderSystem.disableDepthTest()") && adapter.contains("if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();"), "actual wave depth disable/restoration");
        check(adapter.contains("gravity = .02F; scale(.5F)") && adapter.contains("xd = dust.vx(); yd = dust.vy(); zd = dust.vz()"), "source dirt velocity/gravity/scale after vanilla initialization");
        check(adapter.contains("ClassicSkillAlphaShader.get()") && adapter.contains("startsWith(\"textures/effects/blood_splash/\")"), "source no-alpha-test tornado and splash alpha>0 use existing no-cutoff shader");
        check(adapter.contains("wing.terminateTicks < 0) wing.stateTicks++") && adapter.contains("wing.terminateTicks = 0"), "cancelled charge freezes source stateTick but termination progresses");
        check(adapter.contains("ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE") && adapter.contains("head.subtract(ray.scale(.5))") && adapter.contains("head.add(ray.scale(5))"), "source collision rays exclude fluids");
        check(adapter.contains("pendingStormStop.ownerInput == nonce") && adapter.contains("input(skill).complete(pendingStormStop.stoppingInput)") && adapter.contains("if (!old.wing.active)"), "toggle-stop original owner acknowledgement and one active-state transition");
        check(adapter.contains("-.5, -.5, 1, 1") && adapter.contains("-.5, -.25, .5, .75") && adapter.contains("213, 29, 29, 200 / 255.0"), "original glow/splash geometry and tint");
        check(!adapter.contains("PacketDistributor") && !adapter.contains(".consume(") && !adapter.contains("setDeltaMovement(") && !adapter.contains("setPlayerWalkSpeed("), "visual adapter never mutates gameplay authority");
    }
    private static double ranged(Random random, double from, double to) { return from + random.nextDouble() * (to - from); }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
}
