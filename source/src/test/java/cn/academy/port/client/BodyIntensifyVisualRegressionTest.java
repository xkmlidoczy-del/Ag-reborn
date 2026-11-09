package cn.academy.port.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Headless source-input/state/renderer-wiring regression. Never asserts rendered pixels or audible parity. */
public final class BodyIntensifyVisualRegressionTest {
    private static int assertions;
    private static void check(boolean condition, String name) { assertions++; if (!condition) throw new AssertionError(name); }
    private static void close(double actual, double expected, String name) { check(Math.abs(actual - expected) < 1E-10, name + ": " + actual + " != " + expected); }

    public static void main(String[] args) throws Exception {
        parameters(); hud(); tokens(); inputNonces(); clock(); renderer();
        System.out.println("PASS " + assertions + " BodyIntensify source-input/state/renderer-wiring assertions; pixel/audible parity unverified");
    }

    private static void parameters() {
        check(ClassicBodyIntensifyTimeline.LOOP_SOUND.equals("em.intensify_loop") && ClassicBodyIntensifyTimeline.ACTIVATE_SOUND.equals("em.intensify_activate"), "source sound identifiers");
        check(ClassicBodyIntensifyTimeline.SOUND_VOLUME == .5F, "FollowEntitySound default and activation volume");
        check(ClassicBodyIntensifyTimeline.MASK_TEXTURE.equals("textures/effects/em_intensify_mask.png") && ClassicBodyIntensifyTimeline.ARC_PREFIX.equals("textures/effects/arcs/"), "original texture identifiers");
        check(ClassicBodyIntensifyTimeline.HUD_TEMPLATES == 10 && ClassicBodyIntensifyTimeline.WORLD_TEMPLATES == 10, "source template counts");
        check(ClassicBodyIntensifyTimeline.WORLD_LIFE == 15 && ClassicBodyIntensifyTimeline.WORLD_ARC_LIFE == 3, "source effect/subarc lifetimes");
        int[] ticks = {0, 1, 3, 4, 6, 7, 8}; double[] heights = {2, 1.8, 1.5, 1, .5, 0, -.1};
        check(ClassicBodyIntensifyTimeline.waves().size() == 7, "one effect has exactly seven scheduled waves");
        for (int i = 0; i < 7; i++) { var wave = ClassicBodyIntensifyTimeline.waves().get(i); check(wave.tick() == ticks[i], "source callback delay"); close(wave.height(), heights[i], "source callback height"); }
        check(ClassicBodyIntensifyTimeline.WORLD_PASSES == 3 && ClassicBodyIntensifyTimeline.WORLD_WIDTH == .2
                && ClassicBodyIntensifyTimeline.WORLD_OFFSET == .8 && ClassicBodyIntensifyTimeline.WORLD_BRANCH == .7
                && ClassicBodyIntensifyTimeline.WORLD_SHRINK == .9 && ClassicBodyIntensifyTimeline.WORLD_SCALE == .3, "source THIN ArcFactory parameters and transform scale");
        Random random = new Random(42);
        for (int i = 0; i < 10000; i++) {
            check(ClassicBodyIntensifyTimeline.rangedCount(random, 3, 4) == 3, "LambdaLib rangei upper-exclusive world count");
            double sample = random.nextDouble(); boolean shown = random.nextBoolean();
            double rate = random.nextDouble(); int age = random.nextInt(29);
            check(ClassicBodyIntensifyTimeline.subArcAge(age, sample) == (sample < .9 ? age + 1 : age), "SubArc/SubArc2D age source differential");
            check(ClassicBodyIntensifyTimeline.subArcShown(shown, rate, sample) == (sample < (shown ? .4 : .3) * rate ? !shown : shown), "SubArc/SubArc2D flicker source differential");
            check(ClassicBodyIntensifyTimeline.replaceTemplate(rate, sample) == (sample < .5 * rate), "source template-rate differential");
        }
        check(!ClassicBodyIntensifyTimeline.replaceTemplate(.3, .15) && ClassicBodyIntensifyTimeline.replaceTemplate(.3, Math.nextDown(.15)), "strict template threshold");
        check(ClassicBodyIntensifyTimeline.subArcAge(0, .9) == 0, "strict lifetime chance threshold");
        check(ClassicBodyIntensifyTimeline.subArcShown(true, 0, 0), "held switchRate0 remains visible even at sample0");
    }

    private static void hud() {
        close(ClassicBodyIntensifyTimeline.maskAlpha(0, -1), 0, "initial HUD alpha");
        close(ClassicBodyIntensifyTimeline.maskAlpha(250, -1), .5, "500ms linear blend-in midpoint");
        close(ClassicBodyIntensifyTimeline.maskAlpha(500, -1), 1, "blend-in endpoint");
        close(ClassicBodyIntensifyTimeline.maskAlpha(5000, -1), 1, "held mask stays full");
        close(ClassicBodyIntensifyTimeline.maskAlpha(10, 0), 1, "end starts at full alpha even on early cancellation, as source");
        close(ClassicBodyIntensifyTimeline.maskAlpha(0, 100), .5, "200ms linear blend-out midpoint");
        close(ClassicBodyIntensifyTimeline.maskAlpha(0, 200), 0, "mask disappears200ms after end");
        close(ClassicBodyIntensifyTimeline.maskAlpha(0, 900), 0, "arcs may remain when mask already transparent");
        check(!ClassicBodyIntensifyTimeline.disposed(1000) && ClassicBodyIntensifyTimeline.disposed(1001), "source disposal strictly after1000ms");
        for (int seed = 0; seed < 1000; seed++) {
            var hud = new ClassicBodyIntensifyTimeline.Hud(1000, seed);
            check(!hud.blending() && hud.sprites().size() >= 5 && hud.sprites().size() < 7, "held5–6 count, upper-exclusive");
            close(hud.arcAlpha(), .3, "held arc alpha");
            for (var sprite : hud.sprites()) {
                sprite(sprite, .84, .96, 25, 30, 233333, 0);
                close(sprite.left(1280) + sprite.size / 2, 640 + sprite.x * 640, "screen-relative X scale/center");
                close(sprite.top(720) + sprite.size / 2, 360 + sprite.y * 360, "screen-relative Y scale/center");
            }
            hud.tick();
            for (var sprite : hud.sprites()) check(sprite.shown && !sprite.dead, "held sprites do not flicker off at switchRate0");
            hud.startBlend(1250, true, true);
            check(hud.blending() && hud.sprites().size() >= 10 && hud.sprites().size() < 15, "successful first-person10–14 count");
            close(hud.arcAlpha(), .4, "blend arc alpha does not follow mask fade");
            for (var sprite : hud.sprites()) sprite(sprite, .6, 1, 35, 40, 25, .2);
            close(hud.maskAlpha(1350), .5, "blend HUD delegates exact time");
            check(!hud.disposed(2250) && hud.disposed(2251), "HUD exact disposal boundary");
            int count = hud.sprites().size();
            hud.startBlend(9999, true, true);
            check(hud.sprites().size() == count && hud.disposed(2251), "duplicate blend does not regenerate/reset lifetime");
            for (int tick = 0; tick < 200; tick++) hud.tick();
            check(hud.sprites().isEmpty(), "burst arcs expire without regeneration");
            for (boolean performed : new boolean[]{false, true}) {
                var third = new ClassicBodyIntensifyTimeline.Hud(0, seed);
                third.startBlend(0, performed, false);
                check(third.sprites().isEmpty(), "third-person/cancel clears all held arcs");
            }
            var cancelled = new ClassicBodyIntensifyTimeline.Hud(0, seed);
            cancelled.startBlend(0, false, true);
            check(cancelled.sprites().isEmpty(), "unsuccessful first-person end generates no burst");
        }
        // Differential with source random draw order and upper-exclusive rangei.
        for (int seed = 0; seed < 200; seed++) {
            var random = new Random(seed); var hud = new ClassicBodyIntensifyTimeline.Hud(0, seed);
            int count = 5 + random.nextInt(2); check(hud.sprites().size() == count, "source generation count differential");
            for (var actual : hud.sprites()) {
                double radius = .84 + random.nextDouble() * (.96 - .84), angle = random.nextDouble() * Math.PI * 2;
                double size = 25 + random.nextDouble() * 5; int template = random.nextInt(10);
                close(actual.x, radius * Math.sin(angle), "source sprite x differential"); close(actual.y, radius * Math.cos(angle), "source sprite y differential");
                close(actual.size, size, "source sprite size differential"); check(actual.template == template, "source sprite initial template differential");
            }
        }
    }
    private static void sprite(ClassicBodyIntensifyTimeline.Sprite sprite, double from, double to,
                               double sizeFrom, double sizeTo, int life, double switchRate) {
        double radius = Math.hypot(sprite.x, sprite.y);
        check(radius >= from - 1E-15 && radius < to + 1E-15, "source sprite radial bounds");
        check(sprite.size >= sizeFrom && sprite.size < sizeTo, "source GUI-pixel size bounds");
        check(sprite.template >= 0 && sprite.template < 10, "original ten texture frames");
        check(sprite.life == life && sprite.switchRate == switchRate, "source sprite lifetime and switching");
    }

    private static void tokens() {
        var tokens = new ClassicBodyIntensifyTimeline.Tokens();
        check(!tokens.acceptStart(1, 0) && !tokens.acceptEnd(1, -1), "nonpositive packet tokens rejected");
        check(tokens.acceptStart(1, 10) && !tokens.acceptStart(1, 10), "duplicate start rejected");
        check(tokens.acceptEnd(1, 10) && !tokens.acceptEnd(1, 10), "exact completion delivered once");
        check(!tokens.acceptStart(1, 10), "completion tombstone suppresses late start");
        check(tokens.acceptStart(1, 20) && !tokens.acceptEnd(1, 10), "stale end cannot finish newer context");
        tokens.rememberAbort(1, 20);
        check(!tokens.acceptStart(1, 20) && !tokens.acceptEnd(1, 20), "local abort suppresses same-token late start/success");
        check(tokens.acceptEnd(2, 30) && !tokens.acceptEnd(2, 30) && !tokens.acceptStart(2, 30), "observer success without preceding start delivered once");
        check(tokens.acceptEnd(1, 25), "newer context completion accepted even without its start");
        tokens.rememberAbort(1, 20); check(!tokens.acceptEnd(1, 25), "old abort cannot overwrite newer tombstone");
        for (int entity = 0; entity < 10000; entity++) check(tokens.acceptStart(entity, 100 + entity), "many-entity increasing token history");
        check(tokens.size() == 1024, "replay history has explicit memory bound");
        tokens.clear(); check(tokens.size() == 0 && tokens.acceptStart(1, 1), "new connection clears old token namespace");
    }
    private static void clock() {
        var clock = new ClassicBodyIntensifyTimeline.PauseClock();
        check(clock.update(1000, true) == 0 && clock.update(1250, true) == 250, "real-millisecond blend clock");
        check(clock.update(6250, false) == 250 && clock.update(6500, false) == 250, "GUI and effect clocks freeze while paused");
        check(clock.update(6510, true) == 260, "clock resumes without pause jump");
        check(clock.update(6000, true) == 260, "backward wall-clock bounded");
        clock.clear(); check(clock.update(9000, true) == 0, "session clock reset");
    }
    private static void inputNonces() {
        var input = new ClassicBodyIntensifyTimeline.InputNonce();
        check(!input.driven() && !input.held() && input.expected() == 0, "fresh session has no physical input");
        check(input.accepts(0) && !input.accepts(1) && !input.accepts(-1), "only native/internal nonce0 accepted before input driving");
        long first = input.press();
        check(first > 0 && input.driven() && input.held() && input.expected() == first, "press returns positive expected nonce");
        check(input.accepts(first) && !input.accepts(0) && !input.accepts(first + 1), "physical acknowledgement must match exact input");
        input.abort();
        check(!input.held() && input.expected() == 0 && !input.accepts(first) && !input.accepts(0), "abort retires expected nonce, including pre-ack");
        long second = input.press();
        check(second > first && input.accepts(second) && !input.accepts(first), "rapid new press invalidates old acknowledgement");
        check(!input.complete(first) && input.held() && input.expected() == second, "old end cannot retire newer press");
        check(input.complete(second) && !input.held() && input.expected() == 0, "matching end retires its input exactly once");
        check(!input.complete(second) && !input.accepts(second) && !input.accepts(0), "late duplicate/nonce0 cannot revive completed physical context");
        long third = input.press(), fourth = input.press();
        check(fourth > third && !input.accepts(third) && input.accepts(fourth), "new press supersedes prior even without explicit abort");
        input.clear();
        check(!input.driven() && input.accepts(0) && !input.accepts(fourth), "clear resets physical session; old positive nonce stays invalid");
        long fifth = input.press();
        check(fifth > fourth, "clear preserves monotonic counter and never reuses previous-session nonce");

        // Apply the same input-before-server-token ordering as the actual receive adapter. A
        // rejected older press must not poison authoritative token history for the current one.
        var tokens = new ClassicBodyIntensifyTimeline.Tokens();
        var local = new ClassicBodyIntensifyTimeline.InputNonce(); long old = local.press();
        check(local.accepts(old) && tokens.acceptStart(1, 100), "first press binds independent server token");
        local.abort(); tokens.rememberAbort(1, 100); long newer = local.press();
        check(!(local.accepts(old) && tokens.acceptStart(1, 1000)), "old start rejected before touching replay history");
        check(local.accepts(newer) && tokens.acceptStart(1, 200), "matching new start not poisoned by rejected high old token");
        check(!(local.accepts(old) && tokens.acceptEnd(1, 1001)), "old completion rejected before touching replay history");
        check(!local.complete(old) && local.expected() == newer, "old completion cannot abort expected newer input");
        check(local.accepts(newer) && tokens.acceptEnd(1, 200) && local.complete(newer), "matching server token/new nonce complete independently");
        var observer = new ClassicBodyIntensifyTimeline.Tokens();
        check(observer.acceptEnd(1, 1001) && !observer.acceptEnd(1, 1001), "observer uses server tokens without local input-nonce coupling");
        for (int i = 0; i < 10000; i++) {
            long nonce = input.press();
            check(nonce > fifth && input.accepts(nonce) && !input.accepts(nonce - 1), "monotonic rapid-press exact matching sweep");
            input.abort();
            check(!input.accepts(nonce), "abort prevents late matching packets in sweep");
            fifth = nonce;
        }
    }
    private static void renderer() throws Exception {
        Path source = Path.of(System.getProperty("academy.body.sourceRoot", "src/main/java")).resolve("cn/academy/port/client/ClassicBodyIntensifyEffects.java");
        String adapter = Files.readString(source);
        check(adapter.contains("value = Dist.CLIENT"), "adapter subscriber client-only");
        check(adapter.contains("CompoundTag tag = data.copy()") && adapter.contains("mc.level != level || mc.player != local") && adapter.contains("mc.getConnection() != connection"), "queued packet ownership/session isolation");
        check(adapter.contains("callbackEpoch != epoch") && adapter.contains("callbackEpoch++"), "explicit clear invalidates callbacks even in unchanged connection");
        check(adapter.contains("body_intensify_start") && adapter.contains("body_intensify_end") && adapter.contains("tag.contains(\"performed\", Tag.TAG_BYTE)"), "explicit protocol discriminator and typed performed flag");
        check(adapter.contains("TOKENS.acceptEnd(id, token)") && adapter.contains("TOKENS.rememberAbort") && adapter.contains("LOCAL_INPUT.abort()"), "renderer uses tested completion/abort cancellation decisions");
        check(adapter.contains("localPacket && (!tag.contains(\"input\", Tag.TAG_LONG) || !LOCAL_INPUT.accepts(input))"), "typed exact nonce match applies only to local packets");
        check(adapter.indexOf("!LOCAL_INPUT.accepts(input)") < adapter.indexOf("TOKENS.acceptEnd(id, token)")
                && adapter.indexOf("!LOCAL_INPUT.accepts(input)") < adapter.indexOf("TOKENS.acceptStart(id, token)"), "local input identity gates token history before any stale mutation");
        check(adapter.contains("if (localPacket) LOCAL_INPUT.complete(input)") && adapter.contains("return LOCAL_INPUT.press()") && adapter.contains("LOCAL_INPUT.clear()"), "actual lifecycle consumes tested nonce helper");
        check(adapter.contains("if (caster == local)") && adapter.contains("next.loop = new FollowingSound"), "loop and charging HUD local-only");
        check(adapter.contains("new FollowingSound(caster, level, connection, ACTIVATE, null)") && adapter.contains("new Activation(caster, level)"), "successful observer broadcast creates following sound and one effect");
        check(adapter.contains("public static long startLocal()") && adapter.contains("public static void abortLocal()") && adapter.contains("public static int localTicks()") && adapter.contains("public static void clear()"), "nonce-returning physical input, abort, tick and session bridges");
        check(adapter.contains("RenderSystem.disableDepthTest()") && adapter.contains("depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)") && adapter.contains("if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();"), "explicit GL depth disable and exact depth restoration, not NO_DEPTH_TEST no-op");
        check(adapter.contains("ShaderInstance shader = RenderSystem.getShader()") && adapter.contains("RenderSystem.setShader(() -> shader)") && adapter.contains("color = RenderSystem.getShaderColor().clone()"), "previous shader/color restored");
        check(adapter.contains("Stage.AFTER_PARTICLES") && adapter.contains("modelView.set(event.getModelViewMatrix())") && adapter.contains("modelView.popMatrix()") && adapter.contains("RenderSystem.applyModelViewMatrix()"), "world stage matrix scoped and restored with actual1.21.1 APIs");
        check(adapter.contains("priority = EventPriority.HIGH") && adapter.contains("onRenderGui(RenderGuiEvent.Post"), "background GUI stage precedes NORMAL ability HUD");
        check(adapter.contains("graphics.flush()") && adapter.contains("buffers.endBatch(type)"), "isolated immediate HUD and private world batches");
        check(adapter.contains("RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)") && adapter.contains("65536, false, false, CompositeState.builder()"), "source alpha blend and unsorted world insertion order");
        check(adapter.contains("ClassicBodyIntensifyTimeline.waves()") && adapter.contains("return ++ticks < ClassicBodyIntensifyTimeline.WORLD_LIFE"), "renderer consumes tested wave schedule/lifetime without arbitrary charge timeout");
        check(!adapter.contains("addEffect(") && !adapter.contains(".consume(") && !adapter.contains("PacketDistributor") && !adapter.contains("setDeltaMovement("), "no client gameplay mutation or authority");
    }
}
