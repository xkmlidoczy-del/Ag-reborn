/* Original AcademyCraft1.0.7 / LambdaLib1.2.3 client adaptation. See NOTICE and MELTDOWNER_STARTER_VISUALS.md. */
package cn.academy.port.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Visual/audio-only hold effects. AcademyClient forwards the six kinds and calls the input/session
 * hooks documented beside this staged adapter. No damage/resource/cooldown/potion/network mutation.
 * Tokens prevent replay; local input nonces correlate physical presses without authorizing effects.
 * Geometry/assets/source quirks are regression checked. Runtime pixel/audio parity is unverified.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClassicMeltdownerStarterEffects {
    private static final String SCATTER = ClassicMeltdownerStarterTimeline.SCATTER;
    private static final String SHIELD = ClassicMeltdownerStarterTimeline.SHIELD;
    private static final ResourceLocation BALL_GLOW = texture("effects/mdball/glow");
    private static final ResourceLocation[] BALLS = sequence("effects/mdball/", 5);
    private static final ResourceLocation MD_PARTICLE = texture("effects/md_particle"), MD_SHIELD = texture("effects/mdshield");
    private static final ResourceLocation RAY_IN = texture("effects/mdray_small/blend_in"), RAY_TILE = texture("effects/mdray_small/tile"), RAY_OUT = texture("effects/mdray_small/blend_out");
    private static final ResourceLocation WHITE = texture("port/white");
    private static final Map<Integer, Context> SCATTERS = new HashMap<>(), SHIELDS = new HashMap<>();
    private static final ClassicMeltdownerStarterTimeline.Tokens SCATTER_TOKENS = new ClassicMeltdownerStarterTimeline.Tokens(), SHIELD_TOKENS = new ClassicMeltdownerStarterTimeline.Tokens();
    private static final ClassicMeltdownerStarterTimeline.Input SCATTER_INPUT = new ClassicMeltdownerStarterTimeline.Input(), SHIELD_INPUT = new ClassicMeltdownerStarterTimeline.Input();
    private static final ClassicMeltdownerStarterTimeline.PauseClock CLOCK = new ClassicMeltdownerStarterTimeline.PauseClock();
    private static final List<SmallRay> RAYS = new ArrayList<>();
    private static final List<MdParticle> PARTICLES = new ArrayList<>();
    private static final List<FollowingSound> ONE_SHOTS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static ClientLevel activeLevel;
    private static Entity activePlayer;
    private static Object activeConnection;
    private static volatile long callbackEpoch;
    private static MultiBufferSource.BufferSource buffers;
    private ClassicMeltdownerStarterEffects() {}

    /** Returns a correlation nonce for slot_press_token. Starts no speculative visuals/audio. */
    public static long startLocal(String skill) {
        if (!owns(skill)) return 0;
        Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc); updateClock(mc);
        Context old = mc.player == null ? null : contexts(skill).remove(mc.player.getId());
        if (old != null) { tokens(skill).end(old.caster.getId(), old.token); old.hide(mc); }
        return input(skill).press();
    }
    /**
     * Call for ordinary release AND abort. Stop held visuals immediately; preserve the pending nonce
     * so a matching late start can establish only a hidden terminal-ray context. A new press retires it.
     */
    public static void endLocal(String skill) {
        if (!owns(skill)) return;
        Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc); input(skill).end();
        Context current = mc.player == null ? null : contexts(skill).get(mc.player.getId());
        if (current != null) current.hide(mc);
    }
    public static int localTicks(String skill) {
        if (!owns(skill)) return -1;
        Minecraft mc = Minecraft.getInstance();
        Context context = mc.player == null ? null : contexts(skill).get(mc.player.getId());
        return context == null || !context.shown || !context.valid(mc) ? -1 : context.ticks;
    }
    /** SingleKeyDelegate ACTIVE begins with its pending held context and ends on server termination. */
    public static boolean delegateActive(String skill) {
        if (!owns(skill)) return false;
        Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc);
        if (mc.player == null || !mc.player.isAlive()) return false;
        Context context = contexts(skill).get(mc.player.getId());
        return input(skill).heldPending() || context != null && context.shown && context.valid(mc);
    }
    public static boolean owns(String skill) { return SCATTER.equals(skill) || SHIELD.equals(skill); }
    private static Map<Integer, Context> contexts(String skill) { return SCATTER.equals(skill) ? SCATTERS : SHIELDS; }
    private static ClassicMeltdownerStarterTimeline.Tokens tokens(String skill) { return SCATTER.equals(skill) ? SCATTER_TOKENS : SHIELD_TOKENS; }
    private static ClassicMeltdownerStarterTimeline.Input input(String skill) { return SCATTER.equals(skill) ? SCATTER_INPUT : SHIELD_INPUT; }

    /** Strict primitive tags copied before crossing threads; capture level/player/connection/epoch. */
    public static void receive(CompoundTag data) {
        if (data == null) return;
        CompoundTag tag = data.copy(); String kind = tag.getString("kind");
        boolean scatter = kind.startsWith("scatter_bomb_");
        if (!List.of("scatter_bomb_start", "scatter_bomb_ball", "scatter_bomb_ray", "scatter_bomb_end", "light_shield_start", "light_shield_end").contains(kind)
                || !tag.contains("entity", Tag.TAG_INT) || !tag.contains("token", Tag.TAG_LONG) || tag.getLong("token") <= 0) return;
        boolean start = kind.endsWith("_start"), end = kind.endsWith("_end");
        if ((start || end) && (!tag.contains("input", Tag.TAG_LONG) || tag.getLong("input") < 0)) return;
        if (!start && !end && (!tag.contains("index", Tag.TAG_INT) || tag.getInt("index") < 0 || tag.getInt("index") >= 7)) return;
        if (kind.endsWith("_ball") && (!tag.contains("life", Tag.TAG_INT) || tag.getInt("life") != ClassicMeltdownerStarterTimeline.BALL_LIFE
                || !vectorTags(tag, "x", "y", "z") || !vectorTags(tag, "offset_x", "offset_y", "offset_z")
                || vector(tag, "offset_x", "offset_y", "offset_z").lengthSqr() > 16)) return;
        if (kind.endsWith("_ray") && (!vectorTags(tag, "x", "y", "z") || !vectorTags(tag, "dx", "dy", "dz")
                || !tag.contains("length", Tag.TAG_DOUBLE) || !Double.isFinite(tag.getDouble("length"))
                || tag.getDouble("length") <= 0 || tag.getDouble("length") > 64 || vector(tag, "dx", "dy", "dz").lengthSqr() < 1E-12)) return;
        Minecraft mc = Minecraft.getInstance(); ClientLevel level = mc.level; Entity local = mc.player; Object connection = mc.getConnection(); long epoch = callbackEpoch;
        mc.execute(() -> {
            if (level == null || local == null || mc.level != level || mc.player != local || mc.getConnection() != connection || callbackEpoch != epoch) return;
            synchronizeSession(mc); updateClock(mc);
            String skill = scatter ? SCATTER : SHIELD;
            int id = tag.getInt("entity"); long token = tag.getLong("token"), nonce = tag.getLong("input");
            boolean localPacket = id == local.getId();
            // Reject stale local acknowledgement before replay history or a newer context is touched.
            if ((start || end) && localPacket && !input(skill).accepts(nonce)) return;
            Map<Integer, Context> contexts = contexts(skill); var tokens = tokens(skill); Context current = contexts.get(id);
            Entity entity = level.getEntity(id);
            if (end) {
                if (current == null && !validCaster(mc, entity) || !tokens.end(id, token)) return;
                if (localPacket) AcademyClient.acceptedSingleEnd(skill, nonce, token);
                if (current != null && current.token <= token) { contexts.remove(id); current.hide(mc); }
                if (localPacket) input(skill).complete(nonce);
                return;
            }
            if (start) {
                if (!validCaster(mc, entity) || contexts.size() >= 128 && current == null || !tokens.start(id, token)) return;
                if (current != null) current.hide(mc);
                Context next = new Context((Player)entity, level, connection, skill, token, !localPacket || input(skill).mayShowStart(nonce));
                contexts.put(id, next);
                if (localPacket) AcademyClient.acceptedSingleStart(skill, nonce, token);
                if (SHIELD.equals(skill) && next.shown) {
                    next.shieldPosition = shieldPosition(next.caster, mc.player);
                    next.previousShieldPosition = next.shieldPosition;
                    FollowingSound startup = new FollowingSound(next.caster, level, connection, ClassicMeltdownerStarterTimeline.STARTUP_SOUND, .5F, null);
                    if (ONE_SHOTS.size() < 128) { ONE_SHOTS.add(startup); mc.getSoundManager().play(startup); }
                    next.loop = new FollowingSound(next.caster, level, connection, ClassicMeltdownerStarterTimeline.LOOP_SOUND, 1F, next);
                    mc.getSoundManager().play(next.loop);
                }
                return;
            }
            // Ball/ray packets have no nonce: only their acknowledged active context token can accept them.
            if (current == null || current.token != token || !tokens.active(id, token) || !current.valid(mc)) return;
            int index = tag.getInt("index");
            if (kind.endsWith("_ball")) {
                if (!current.shown || current.balls.get(index) != null || current.rayIndices.get(index)) return;
                current.balls.put(index, new Ball(vector(tag, "offset_x", "offset_y", "offset_z"), CLOCK.elapsed()));
            } else if (kind.endsWith("_ray")) {
                if (current.rayIndices.get(index) || RAYS.size() >= 256) return;
                current.rayIndices.set(index); current.balls.remove(index);
                // The packet already contains authoritative modern feet-coordinate geometry. viewOptimize=false.
                Vec3 origin = vector(tag, "x", "y", "z"), direction = vector(tag, "dx", "dy", "dz").normalize();
                RAYS.add(new SmallRay(origin, direction, tag.getDouble("length"), CLOCK.elapsed()));
                level.playLocalSound(origin.x, origin.y, origin.z, sound(ClassicMeltdownerStarterTimeline.RAY_SOUND), SoundSource.MASTER, .5F, 1F, false);
            }
        });
    }
    /** AcademyClient calls on leave/death/player identity change; also self-check identity every event. */
    public static void clear() { callbackEpoch++; clearOwned(); }
    private static void clearOwned() {
        Minecraft mc = Minecraft.getInstance();
        for (Context context : SCATTERS.values()) context.hide(mc);
        for (Context context : SHIELDS.values()) context.hide(mc);
        for (FollowingSound sound : ONE_SHOTS) { sound.finish(); mc.getSoundManager().stop(sound); }
        SCATTERS.clear(); SHIELDS.clear(); RAYS.clear(); PARTICLES.clear(); ONE_SHOTS.clear();
        SCATTER_TOKENS.clear(); SHIELD_TOKENS.clear(); SCATTER_INPUT.clear(); SHIELD_INPUT.clear(); CLOCK.clear();
    }
    private static void synchronizeSession(Minecraft mc) {
        if (activeLevel == mc.level && activePlayer == mc.player && activeConnection == mc.getConnection()) return;
        clearOwned(); activeLevel = mc.level; activePlayer = mc.player; activeConnection = mc.getConnection();
    }
    private static void updateClock(Minecraft mc) { CLOCK.update(Util.getMillis(), mc.level != null && !mc.isPaused()); }
    private static boolean validCaster(Minecraft mc, Entity entity) { return entity instanceof Player && entity.level() == mc.level && !entity.isRemoved() && entity.isAlive() && !entity.isSpectator() && mc.level.getEntity(entity.getId()) == entity; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc); updateClock(mc);
        if (mc.level == null) return;
        if (mc.player == null || !mc.player.isAlive()) { clear(); return; }
        if (!mc.isPaused()) PARTICLES.removeIf(particle -> !particle.tick());
        for (String skill : List.of(SCATTER, SHIELD)) {
            var iterator = contexts(skill).values().iterator();
            while (iterator.hasNext()) {
                Context context = iterator.next();
                if (!context.valid(mc)) { iterator.remove(); tokens(skill).end(context.caster.getId(), context.token); context.hide(mc); }
                else if (!mc.isPaused()) {
                    context.ticks++;
                    if (SHIELD.equals(skill) && context.shown) {
                        context.previousShieldPosition = context.shieldPosition;
                        context.shieldPosition = shieldPosition(context.caster, mc.player);
                        if (RANDOM.nextFloat() < .3F) {
                            var center = ClassicMeltdownerStarterTimeline.shieldParticleCenter(point(context.caster.position()), context.caster.getEyeHeight(), context.caster.getYHeadRot(), context.caster.getXRot(), context.caster == mc.player);
                            addParticle(vec(center).add(random(-.5, .5), random(-.5, .5), random(-.5, .5)), new Vec3(random(-.02, .02), random(-.01, .05), random(-.02, .02)));
                        }
                    }
                }
            }
        }
        ONE_SHOTS.removeIf(sound -> !mc.getSoundManager().isActive(sound));
        RAYS.removeIf(ray -> CLOCK.elapsed() - ray.created >= ClassicMeltdownerStarterTimeline.RAY_MS);
        if (mc.isPaused()) return;
        for (SmallRay ray : RAYS) addParticle(ray.origin.add(ray.direction.scale(random(0, 10))), new Vec3(random(-.015, .015), random(-.015, .015), random(-.015, .015)));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc); updateClock(mc);
        if (mc.level == null || mc.player == null || !mc.player.isAlive()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        PoseStack poses = event.getPoseStack(); Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float[] priorColor = RenderSystem.getShaderColor().clone();
        poses.pushPose();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            for (Context context : SCATTERS.values()) if (context.shown && context.valid(mc)) for (Ball ball : context.balls.values()) renderBall(context, ball, mc, event, poses, camera);
            for (Context context : SHIELDS.values()) if (context.shown && context.valid(mc)) renderShield(context, poses, partial, camera);
            for (SmallRay ray : RAYS) renderRay(ray, camera, poses.last().pose());
            for (MdParticle particle : PARTICLES) {
                poses.pushPose(); Vec3 position = particle.previous.lerp(particle.position, partial); poses.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z); poses.mulPose(event.getCamera().rotation());
                drawQuad(Material.type(MD_PARTICLE, true, false), poses.last().pose(), ClassicMeltdownerStarterTimeline.particleQuad(particle.size), 255, 255, 255, particle.alpha * ClassicMeltdownerStarterTimeline.particleAlpha(particle.age, particle.life));
                poses.popPose();
            }
        } finally { poses.popPose(); RenderSystem.setShaderColor(priorColor[0], priorColor[1], priorColor[2], priorColor[3]); }
    }
    private static void renderBall(Context context, Ball ball, Minecraft mc, RenderLevelStageEvent event, PoseStack poses, Vec3 camera) {
        long age = CLOCK.elapsed() - ball.created;
        if (age >= ClassicMeltdownerStarterTimeline.BALL_LIFE * 50L) return;
        ball.wiggle.frame(CLOCK.elapsed() + 1, ball.random, RANDOM);
        var offset = ClassicMeltdownerStarterTimeline.modernBallOffset(point(ball.offset), context.caster == mc.player);
        // Source render-tick position follows latest caster translation with a fixed world-space offset.
        Vec3 position = context.caster.position().add(vec(offset)).add(vec(ClassicMeltdownerStarterTimeline.ballJitter(age)));
        double alpha = ClassicMeltdownerStarterTimeline.ballAlpha(ClassicMeltdownerStarterTimeline.BALL_LIFE, age);
        float size = ClassicMeltdownerStarterTimeline.ballSize(ClassicMeltdownerStarterTimeline.BALL_LIFE, age);
        poses.pushPose(); poses.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z); poses.mulPose(event.getCamera().rotation());
        drawQuad(Material.type(BALL_GLOW, false, false), poses.last().pose(), ClassicMeltdownerStarterTimeline.billboard(.7F * size, false), 255, 255, 255, alpha * (.3 + .7 * ball.wiggle.alpha));
        drawQuad(Material.type(BALLS[ball.wiggle.texture], false, false), poses.last().pose(), ClassicMeltdownerStarterTimeline.billboard(.5F * size, false), 255, 255, 255, alpha * (.8 + .2 * ball.wiggle.alpha));
        poses.popPose();
    }
    private static void renderShield(Context context, PoseStack poses, float partial, Vec3 camera) {
        long now = CLOCK.elapsed() + 1, dt = context.lastRender == 0 ? 0 : now - context.lastRender;
        context.rotation = ClassicMeltdownerStarterTimeline.shieldRotation(context.rotation, context.ticks, dt); context.lastRender = now;
        var frame = ClassicMeltdownerStarterTimeline.shieldFrame(context.ticks);
        Vec3 position = context.previousShieldPosition.lerp(context.shieldPosition, partial);
        poses.pushPose(); poses.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
        poses.mulPose(Axis.YP.rotationDegrees(-context.caster.getYHeadRot())); poses.mulPose(Axis.XP.rotationDegrees(context.caster.getXRot())); poses.mulPose(Axis.ZP.rotationDegrees(context.rotation));
        // Source computes frame.unusedAlpha() but NEVER binds it. Full-white modulation is deliberate.
        drawQuad(Material.type(MD_SHIELD, true, false), poses.last().pose(), ClassicMeltdownerStarterTimeline.billboard(frame.size(), true), 255, 255, 255, 1);
        poses.popPose();
    }
    private static void renderRay(SmallRay ray, Vec3 camera, Matrix4f matrix) {
        long age = CLOCK.elapsed() - ray.created;
        if (age >= ClassicMeltdownerStarterTimeline.RAY_MS) return;
        ray.wiggle.frame(CLOCK.elapsed() + 1, RANDOM);
        double width = ClassicMeltdownerStarterTimeline.rayWidth(age), length = ray.length * ClassicMeltdownerStarterTimeline.rayLengthScale(age);
        double alpha = ClassicMeltdownerStarterTimeline.rayAlpha(age), glow = ClassicMeltdownerStarterTimeline.rayGlowAlpha(age, ray.wiggle.glow);
        // No first-person view optimization. Degenerate zero-length boards retain source endpoints.
        Vec3 origin = ray.origin.subtract(camera), up = origin.cross(ray.direction).normalize();
        Vec3 end = origin.add(ray.direction.scale(length)), mid1 = origin.add(ray.direction.scale(.3)), mid2 = end.subtract(ray.direction.scale(.3));
        board(Material.type(RAY_IN, true, false), matrix, origin, mid1, up, .3 * width, glow);
        board(Material.type(RAY_TILE, true, false), matrix, mid1, mid2, up, .3 * width, glow);
        board(Material.type(RAY_OUT, true, false), matrix, mid2, end, up, .3 * width, glow);
        double yaw = -Math.PI / 2 + Math.atan2(ray.direction.x, ray.direction.z), pitch = Math.atan2(ray.direction.y, Math.sqrt(ray.direction.x * ray.direction.x + ray.direction.z * ray.direction.z));
        Vec3 axisY = new Vec3(-Math.cos(yaw) * Math.sin(pitch), Math.cos(pitch), Math.sin(yaw) * Math.sin(pitch));
        Vec3 axisZ = new Vec3(Math.sin(yaw), 0, Math.cos(yaw));
        cylinder(ray, origin, matrix, axisY, axisZ, length, .03 * width, .98, 216, 248, 216, 230 / 255.0 * alpha);
        cylinder(ray, origin, matrix, axisY, axisZ, length, .045 * width, 1, 106, 242, 106, 50 / 255.0 * alpha);
    }
    private static void cylinder(SmallRay ray, Vec3 origin, Matrix4f matrix, Vec3 y, Vec3 z, double length, double radius, double headFix, int r, int g, int b, double alpha) {
        RenderType type = Material.type(WHITE, false, true); VertexConsumer out = buffers.getBuffer(type);
        for (var quad : ClassicMeltdownerStarterTimeline.cylinder(length, radius, headFix)) for (var vertex : List.of(quad.a(), quad.b(), quad.c(), quad.d())) {
            var p = vertex.point(); Vec3 position = origin.add(ray.direction.scale(p.x())).add(y.scale(p.y())).add(z.scale(p.z()));
            vertex(out, matrix, position, vertex.u(), vertex.v(), r, g, b, alpha);
        }
        buffers.endBatch(type);
    }
    private static void board(RenderType type, Matrix4f matrix, Vec3 from, Vec3 to, Vec3 up, double width, double alpha) {
        Vec3 half = up.scale(width / 2); VertexConsumer out = buffers.getBuffer(type);
        vertex(out, matrix, from.add(half), 0, 1, 255, 255, 255, alpha); vertex(out, matrix, from.subtract(half), 0, 0, 255, 255, 255, alpha);
        vertex(out, matrix, to.subtract(half), 1, 0, 255, 255, 255, alpha); vertex(out, matrix, to.add(half), 1, 1, 255, 255, 255, alpha); buffers.endBatch(type);
    }
    private static void drawQuad(RenderType type, Matrix4f matrix, ClassicMeltdownerStarterTimeline.Quad quad, int r, int g, int b, double alpha) {
        VertexConsumer out = buffers.getBuffer(type);
        for (var vertex : List.of(quad.a(), quad.b(), quad.c(), quad.d())) vertex(out, matrix, vec(vertex.point()), vertex.u(), vertex.v(), r, g, b, alpha);
        buffers.endBatch(type); // Preserve source per-effect/layer order; do not sort through depth-writing layers.
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 p, float u, float v, int r, int g, int b, double alpha) {
        out.addVertex(matrix, (float)p.x, (float)p.y, (float)p.z).setUv(u, v).setColor(r, g, b, Mth.clamp((int)Math.round(alpha * 255), 0, 255));
    }
    private static final class Context {
        final Player caster; final ClientLevel level; final Object connection; final String skill; final long token;
        final Map<Integer, Ball> balls = new LinkedHashMap<>(); final BitSet rayIndices = new BitSet(7);
        boolean shown; int ticks; float rotation; long lastRender; Vec3 shieldPosition = Vec3.ZERO, previousShieldPosition = Vec3.ZERO; FollowingSound loop;
        Context(Player caster, ClientLevel level, Object connection, String skill, long token, boolean shown) { this.caster = caster; this.level = level; this.connection = connection; this.skill = skill; this.token = token; this.shown = shown; }
        boolean valid(Minecraft mc) { return level == mc.level && connection == mc.getConnection() && validCaster(mc, caster); }
        void hide(Minecraft mc) { shown = false; balls.clear(); if (loop != null) { loop.finish(); mc.getSoundManager().stop(loop); loop = null; } }
    }
    private static final class Ball {
        final Vec3 offset; final long created; final Random random = new Random(); final ClassicMeltdownerStarterTimeline.BallWiggle wiggle = new ClassicMeltdownerStarterTimeline.BallWiggle();
        Ball(Vec3 offset, long created) { this.offset = offset; this.created = created; }
    }
    private static final class SmallRay {
        final Vec3 origin, direction; final double length; final long created; final ClassicMeltdownerStarterTimeline.RayWiggle wiggle = new ClassicMeltdownerStarterTimeline.RayWiggle();
        SmallRay(Vec3 origin, Vec3 direction, double length, long created) { this.origin = origin; this.direction = direction; this.length = length; this.created = created; }
    }
    private static final class MdParticle {
        Vec3 position, previous; final Vec3 velocity; final int life; final double alpha; final float size; int age;
        MdParticle(Vec3 position, Vec3 velocity) { this.position = previous = position; this.velocity = velocity; life = RANDOM.nextInt(30) + 25; alpha = random(.3, .6); size = .05F + RANDOM.nextFloat() * .02F; }
        boolean tick() { age++; previous = position; position = position.add(velocity); return ClassicMeltdownerStarterTimeline.particleAlive(age, life); }
    }
    private static final class FollowingSound extends AbstractTickableSoundInstance {
        final Entity caster; final ClientLevel level; final Object connection; final Context owner; boolean finished;
        FollowingSound(Entity caster, ClientLevel level, Object connection, String path, float volume, Context owner) {
            super(sound(path), SoundSource.MASTER, RandomSource.create()); this.caster = caster; this.level = level; this.connection = connection; this.owner = owner;
            this.volume = volume; pitch = 1F; looping = owner != null; delay = 0; tick();
        }
        void finish() { finished = true; stop(); }
        @Override public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (finished || level != mc.level || connection != mc.getConnection() || !validCaster(mc, caster) || owner != null && !owner.shown) { finish(); return; }
            x = caster.getX(); y = caster.getY(); z = caster.getZ();
        }
    }
    private static void addParticle(Vec3 position, Vec3 velocity) { if (PARTICLES.size() < 4096) PARTICLES.add(new MdParticle(position, velocity)); }
    private static Vec3 shieldPosition(Player caster, Player local) { return vec(ClassicMeltdownerStarterTimeline.shieldPosition(point(caster.position()), caster.getEyeHeight(), caster.getYHeadRot(), caster.getXRot(), caster == local)); }
    private static ClassicMeltdownerStarterTimeline.Point point(Vec3 p) { return new ClassicMeltdownerStarterTimeline.Point(p.x, p.y, p.z); }
    private static Vec3 vec(ClassicMeltdownerStarterTimeline.Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private static double random(double from, double to) { return ClassicMeltdownerStarterTimeline.ranged(RANDOM, from, to); }
    private static Vec3 vector(CompoundTag tag, String x, String y, String z) { return new Vec3(tag.getDouble(x), tag.getDouble(y), tag.getDouble(z)); }
    private static boolean vectorTags(CompoundTag tag, String x, String y, String z) { return tag.contains(x, Tag.TAG_DOUBLE) && tag.contains(y, Tag.TAG_DOUBLE) && tag.contains(z, Tag.TAG_DOUBLE) && Double.isFinite(tag.getDouble(x)) && Double.isFinite(tag.getDouble(y)) && Double.isFinite(tag.getDouble(z)); }
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    private static ResourceLocation[] sequence(String path, int count) { ResourceLocation[] result = new ResourceLocation[count]; for (int i = 0; i < count; i++) result[i] = texture(path + i); return result; }
    private static SoundEvent sound(String path) { return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", path)); }

    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class CutoffShader {
        private static ShaderInstance shader, zero;
        private CutoffShader() {}
        @SubscribeEvent public static void register(RegisterShadersEvent event) {
            shader = zero = null;
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("academy", "classic_meltdowner_cutoff05"), DefaultVertexFormat.POSITION_TEX_COLOR), value -> shader = value);
                event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("academy", "classic_meltdowner_gt0"), DefaultVertexFormat.POSITION_TEX_COLOR), value -> zero = value);
            }
            catch (IOException failure) { LogUtils.getLogger().error("Classic Meltdowner .05 alpha-cutoff shader unavailable; fallback is visually inexact", failure); }
        }
        static ShaderInstance get() { return shader == null ? GameRenderer.getPositionTexColorShader() : shader; }
        static ShaderInstance zero() { return zero == null ? ClassicSkillAlphaShader.get() : zero; }
    }
    private static final class Material extends RenderType {
        private record Key(ResourceLocation texture, boolean cutoff, boolean cull) {}
        private static final Map<Key, RenderType> TYPES = new HashMap<>();
        private Material(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean cr, boolean sort, Runnable setup, Runnable clear) { super(n, f, m, s, cr, sort, setup, clear); }
        static RenderType type(ResourceLocation texture, boolean cutoff, boolean cull) {
            return TYPES.computeIfAbsent(new Key(texture, cutoff, cull), key -> create("academy_classic_meltdowner_" + key.texture.getPath() + "_" + cutoff + "_" + cull,
                    DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, false, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(cull ? GameRenderer::getPositionTexColorShader : cutoff ? CutoffShader::get : CutoffShader::zero))
                            .setTextureState(new TextureStateShard(texture, false, false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(cull ? CULL : NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                            .setWriteMaskState(COLOR_DEPTH_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
    }
}
