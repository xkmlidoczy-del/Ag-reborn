/* AcademyCraft 1.0.7 WaveEffect/StormWingEffect/BloodRetroContextC adaptation. GPLv3; see NOTICE. */
package cn.academy.port.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Client-only visuals/audio for directed blastwave, storm wing and blood retrograde.
 * Forward dir_blast_prepare/abort/perform, storm_wing_start/state/end, blood_retro_start/end/perform.
 * Every event requires entity:int, token:long>0; local events additionally require input:long.
 * Directed perform: x/y/z:double endpoint, yaw/pitch:float. Storm start: charge:number>0, state:int.
 * Storm state: state:int (0/1), index:int increasing. Blood perform: x/y/z:double target base,
 * width/height:float, dx/dy/dz:double caster look, yaw:float; end: performed:boolean.
 * No CP, damage, cooldown, inventory, flight, walking-speed or network mutation occurs here.
 * Original media, random mesh algorithms, animation curves and tick lifetimes are retained.
 * NeoForge hand/dirt/light/framebuffer substitutions still need native pixel and audible review.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClassicVectorCombatEffects {
    private static final Set<String> SKILLS = Set.of("dir_blast", "storm_wing", "blood_retro");
    private static final Map<String, ClassicVectorCombatTimeline.InputNonce> INPUTS = new HashMap<>();
    private static final Map<Key, Context> CONTEXTS = new HashMap<>();
    private static Stop pendingStormStop;
    private record Stop(long token, long ownerInput, long stoppingInput) {}
    private static final List<Wave> WAVES = new ArrayList<>();
    private static final List<Wing> WINGS = new ArrayList<>();
    private static final List<Splash> SPLASHES = new ArrayList<>();
    private static final List<Spray> SPRAYS = new ArrayList<>();
    private static final List<TerrainParticle> DUST = new ArrayList<>();
    private static final List<FollowingSound> SOUNDS = new ArrayList<>();
    private static final ClassicVectorCombatTimeline.Tokens TOKENS = new ClassicVectorCombatTimeline.Tokens();
    private static final ClassicVectorCombatTimeline.PauseClock CLOCK = new ClassicVectorCombatTimeline.PauseClock();
    private static final Random RANDOM = new Random();
    private static ClientLevel activeLevel;
    private static Entity activePlayer;
    private static Object activeConnection;
    private static volatile long callbackEpoch;
    private static MultiBufferSource.BufferSource buffers;
    private static PoseStack lastHandStack;
    private ClassicVectorCombatEffects() {}
    private record Key(String skill, int entity) {}

    public static boolean owns(String skill) { return SKILLS.contains(skill); }
    private static ClassicVectorCombatTimeline.InputNonce input(String skill) { return INPUTS.computeIfAbsent(skill, key -> new ClassicVectorCombatTimeline.InputNonce()); }
    public static long startLocal(String skill) {
        if (!owns(skill)) return 0; Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc); updateClock(mc);
        if ("storm_wing".equals(skill) && mc.player != null) {
            Context current = CONTEXTS.remove(new Key(skill, mc.player.getId()));
            if (current != null && current.token > 0) {
                long ownerInput = input(skill).expected(), nonce = input(skill).press();
                pendingStormStop = new Stop(current.token, ownerInput, nonce); current.close(mc); return nonce;
            }
        }
        abortContext(mc, skill); long nonce = input(skill).press();
        if ("dir_blast".equals(skill) && validCaster(mc, mc.player)) CONTEXTS.put(new Key(skill, mc.player.getId()), new Context(skill, mc.player, 0, CLOCK.elapsed()));
        return nonce;
    }
    public static boolean active(String skill) {
        Minecraft mc = Minecraft.getInstance(); Context context = mc.player == null ? null : CONTEXTS.get(new Key(skill, mc.player.getId()));
        return context != null && context.valid(mc);
    }
    public static boolean anyActive() { for (String skill : SKILLS) if (active(skill)) return true; return false; }
    public static boolean stormActive() {
        Minecraft mc = Minecraft.getInstance(); Context context = mc.player == null ? null : CONTEXTS.get(new Key("storm_wing", mc.player.getId()));
        return context != null && context.valid(mc) && context.wing != null && context.wing.active;
    }
    public static long localNonce(String skill) { return owns(skill) ? input(skill).expected() : 0; }
    /** Release waits for the authoritative success or cancellation, so a fast release cannot suppress its punch. */
    public static void releaseLocal(String skill) { if (owns(skill)) input(skill).release(); }
    public static void abortLocal(String skill) {
        if (!owns(skill)) return; Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc); updateClock(mc);
        input(skill).abort(); abortContext(mc, skill); if ("storm_wing".equals(skill)) pendingStormStop = null;
    }
    private static void abortContext(Minecraft mc, String skill) {
        if (mc.player == null) return;
        Context old = CONTEXTS.remove(new Key(skill, mc.player.getId()));
        if (old != null) { TOKENS.abort(skill, old.caster.getId(), old.token); old.close(mc); }
    }
    public static int localTicks(String skill) {
        Minecraft mc = Minecraft.getInstance(); Context context = mc.player == null ? null : CONTEXTS.get(new Key(skill, mc.player.getId()));
        return context == null || !context.valid(mc) ? -1 : context.ticks;
    }
    public static float consumptionHint() {
        Minecraft mc = Minecraft.getInstance(); Context context = mc.player == null ? null : CONTEXTS.get(new Key("dir_blast", mc.player.getId()));
        return context == null || !context.valid(mc) ? 0 : context.consumption;
    }

    /** Copy before client-thread dispatch. Explicit clear/session replacement fences queued old callbacks. */
    public static void receive(CompoundTag data) {
        if (data == null) return; CompoundTag tag = data.copy(); String kind = tag.getString("kind");
        String skill = skillFor(kind); if (skill == null || !tag.contains("entity", Tag.TAG_INT) || !tag.contains("token", Tag.TAG_LONG)) return;
        Minecraft mc = Minecraft.getInstance(); ClientLevel level = mc.level; Entity local = mc.player; Object connection = mc.getConnection(); long epoch = callbackEpoch;
        mc.execute(() -> {
            if (level == null || local == null || mc.level != level || mc.player != local || mc.getConnection() != connection || callbackEpoch != epoch) return;
            synchronizeSession(mc); updateClock(mc); int id = tag.getInt("entity"); long token = tag.getLong("token");
            if (token <= 0) return; boolean localPacket = id == local.getId(); long nonce = tag.getLong("input");
            boolean perform = kind.endsWith("_perform"), ending = kind.endsWith("_end") || kind.endsWith("_abort");
            boolean stoppedStorm = localPacket && "storm_wing_end".equals(kind) && pendingStormStop != null
                    && pendingStormStop.token == token && pendingStormStop.ownerInput == nonce;
            if (localPacket && (!tag.contains("input", Tag.TAG_LONG) || !(stoppedStorm || (perform || ending ? input(skill).acceptsSuccess(nonce) : input(skill).accepts(nonce))))) return;
            Key key = new Key(skill, id); Context old = CONTEXTS.get(key);
            if (ending) {
                if ("blood_retro_end".equals(kind) && !tag.contains("performed", Tag.TAG_BYTE)) return;
                boolean successful = "blood_retro_end".equals(kind) && tag.getBoolean("performed");
                if (kind.endsWith("_abort")) TOKENS.abort(skill, id, token);
                else if (!TOKENS.end(skill, id, token, successful)) return;
                boolean deferred=localPacket&&"storm_wing".equals(skill)&&AcademyClient.acceptedContextEnd(skill,nonce,token);
                if (!deferred && old != null && old.token <= token) { CONTEXTS.remove(key); old.close(mc); }
                if (localPacket && !"storm_wing".equals(skill)) AcademyClient.acceptedSingleEnd(skill,nonce,token);
                if (stoppedStorm) { input(skill).complete(pendingStormStop.stoppingInput); pendingStormStop = null; }
                else if (localPacket) input(skill).complete(nonce);
                return;
            }
            Entity caster = level.getEntity(id); if (!validCaster(mc, caster)) return;
            if ("storm_wing_state".equals(kind)) {
                if (!tag.contains("state", Tag.TAG_INT) || !tag.contains("index", Tag.TAG_INT) || tag.getInt("state") != 1
                        || old == null || old.token != token || !TOKENS.state(skill, id, token, tag.getInt("index"))) return;
                if (!old.wing.active) { old.wing.active = true; old.wing.stateTicks = 0; }
                if(localPacket) AcademyClient.acceptedContextActive(skill,nonce,token); return;
            }
            if (perform) {
                if (!validPosition(tag, "x", "y", "z") || point(tag).distanceToSqr(caster.position()) > 1024) return;
                if ("blood_retro".equals(skill) && (!finiteNumber(tag, "width") || !finiteNumber(tag, "height")
                        || tag.getDouble("width") <= 0 || tag.getDouble("width") > 32 || tag.getDouble("height") <= 0 || tag.getDouble("height") > 32
                        || !finiteNumber(tag, "yaw") || !validDirection(tag))) return;
                if ("dir_blast".equals(skill) && (!finiteNumber(tag, "yaw") || !finiteNumber(tag, "pitch"))) return;
                if (!TOKENS.perform(skill, id, token)) return;
                if ("dir_blast".equals(skill)) {
                    if (WAVES.size() < 128) WAVES.add(new Wave(level, caster, tag));
                    play(mc, new FollowingSound(caster, level, connection, ClassicVectorCombatTimeline.BLAST_SOUND,
                            ClassicVectorCombatTimeline.BLAST_VOLUME, null, point(tag)));
                    if (localPacket) {
                        Context next = old != null && (old.token == 0 || old.token == token) ? old : new Context(skill, caster, token, CLOCK.elapsed());
                        next.token = token; next.punch = true; next.created = CLOCK.elapsed(); next.punchTicks = 0; CONTEXTS.put(key, next);
                    } else if (old != null && old.token <= token) { CONTEXTS.remove(key); old.close(mc); }
                } else {
                    blood(mc, level, caster, tag);
                    play(mc, new FollowingSound(caster, level, connection, ClassicVectorCombatTimeline.BLOOD_SOUND, ClassicVectorCombatTimeline.BLOOD_VOLUME, null, null));
                    if (old != null && old.token <= token) { CONTEXTS.remove(key); old.close(mc); }
                }
                if (localPacket) {AcademyClient.acceptedSingleEnd(skill,nonce,token);input(skill).complete(nonce);} return;
            }
            if ("storm_wing".equals(skill) && (!finiteNumber(tag, "charge") || tag.getDouble("charge") <= 0 || tag.getDouble("charge") > 200
                    || !tag.contains("state", Tag.TAG_INT) || tag.getInt("state") < 0 || tag.getInt("state") > 1)) return;
            if (old != null && old.token >= token || old == null && CONTEXTS.size() >= 128 || !TOKENS.start(skill, id, token)) return;
            if(localPacket && "storm_wing".equals(skill) && AcademyClient.contextStartCancelled(skill,nonce,token))return;
            Context next;
            if (old != null && old.token == 0 && "dir_blast".equals(skill)) { next = old; next.token = token; }
            else { if (old != null) old.close(mc); next = new Context(skill, caster, token, CLOCK.elapsed()); }
            next.consumption = finiteNumber(tag, "cp") ? (float) tag.getDouble("cp") : 0;
            CONTEXTS.put(key, next);
            if(localPacket && !"storm_wing".equals(skill))AcademyClient.acceptedSingleStart(skill,nonce,token);
            if ("storm_wing".equals(skill)) {
                next.wing = new Wing(caster, level, tag.getDouble("charge"), tag.getInt("state") == 1);
                if (WINGS.size() < 128) WINGS.add(next.wing);
                next.loop = new FollowingSound(caster, level, connection, ClassicVectorCombatTimeline.WING_SOUND, ClassicVectorCombatTimeline.WING_VOLUME, next, null);
                play(mc, next.loop);
                if(localPacket) AcademyClient.acceptedContextStart(skill,nonce,token,next.wing.active);
            }
        });
    }
    private static String skillFor(String kind) {
        return switch (kind) { case "dir_blast_prepare", "dir_blast_abort", "dir_blast_perform" -> "dir_blast";
            case "storm_wing_start", "storm_wing_state", "storm_wing_end" -> "storm_wing";
            case "blood_retro_start", "blood_retro_end", "blood_retro_perform" -> "blood_retro"; default -> null; };
    }
    private static boolean finiteNumber(CompoundTag tag, String name) { return tag.contains(name, Tag.TAG_ANY_NUMERIC) && Double.isFinite(tag.getDouble(name)); }
    private static boolean validPosition(CompoundTag tag, String x, String y, String z) {
        return finiteNumber(tag, x) && finiteNumber(tag, y) && finiteNumber(tag, z) && Math.abs(tag.getDouble(x)) <= 30_000_000
                && Math.abs(tag.getDouble(z)) <= 30_000_000 && Math.abs(tag.getDouble(y)) <= 2048;
    }
    private static boolean validDirection(CompoundTag tag) {
        if (!finiteNumber(tag, "dx") || !finiteNumber(tag, "dy") || !finiteNumber(tag, "dz")) return false;
        double length = direction(tag).lengthSqr(); return length >= .99 && length <= 1.01;
    }
    private static Vec3 point(CompoundTag tag) { return new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")); }
    private static Vec3 direction(CompoundTag tag) { return new Vec3(tag.getDouble("dx"), tag.getDouble("dy"), tag.getDouble("dz")); }
    private static void play(Minecraft mc, FollowingSound sound) { SOUNDS.removeIf(s -> !mc.getSoundManager().isActive(s)); if (SOUNDS.size() < 256) { SOUNDS.add(sound); mc.getSoundManager().play(sound); } }
    public static void clear() { callbackEpoch++; clearOwned(); }
    private static void clearOwned() {
        Minecraft mc = Minecraft.getInstance();
        for (Context context : CONTEXTS.values()) context.close(mc);
        for (FollowingSound sound : SOUNDS) { sound.finish(); mc.getSoundManager().stop(sound); }
        for (TerrainParticle particle : DUST) particle.remove();
        CONTEXTS.clear(); WAVES.clear(); WINGS.clear(); SPLASHES.clear(); SPRAYS.clear(); DUST.clear(); SOUNDS.clear(); TOKENS.clear(); CLOCK.clear();
        for (var nonce : INPUTS.values()) nonce.clear(); lastHandStack = null; pendingStormStop = null;
    }
    private static void synchronizeSession(Minecraft mc) { if (activeLevel == mc.level && activePlayer == mc.player && activeConnection == mc.getConnection()) return;
        clearOwned(); activeLevel = mc.level; activePlayer = mc.player; activeConnection = mc.getConnection(); }
    private static void updateClock(Minecraft mc) { CLOCK.update(Util.getMillis(), mc.level != null && !mc.isPaused()); }
    private static boolean validCaster(Minecraft mc, Entity caster) { return caster != null && mc.level != null && caster.isAlive() && !caster.isRemoved() && caster.level() == mc.level && mc.level.getEntity(caster.getId()) == caster; }
    private static Vec3 position(Entity caster, float partial) { return new Vec3(Mth.lerp(partial, caster.xo, caster.getX()), Mth.lerp(partial, caster.yo, caster.getY()), Mth.lerp(partial, caster.zo, caster.getZ())); }

    @SubscribeEvent public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc); updateClock(mc); if (mc.level == null || mc.player == null) return;
        var contexts = CONTEXTS.entrySet().iterator();
        while (contexts.hasNext()) {
            var entry = contexts.next(); Context context = entry.getValue();
            if (!context.valid(mc)) { TOKENS.abort(context.skill, context.caster.getId(), context.token); contexts.remove(); context.close(mc); if (context.caster == mc.player) input(context.skill).abort(); continue; }
            if (mc.isPaused()) continue;
            context.ticks++;
            if (context.punch && ++context.punchTicks > 6) { contexts.remove(); context.close(mc); }
            else if ("dir_blast".equals(context.skill) && context.ticks >= 200) { contexts.remove(); context.close(mc); TOKENS.abort(context.skill, context.caster.getId(), context.token); if (context.caster == mc.player) input(context.skill).abort(); }
            if (context.wing != null && !context.closed) dust(mc, context.wing);
        }
        SOUNDS.removeIf(sound -> !mc.getSoundManager().isActive(sound)); DUST.removeIf(particle -> !particle.isAlive());
        WINGS.removeIf(wing -> !validCaster(mc, wing.caster) || mc.level != wing.level);
        WAVES.removeIf(wave -> mc.level != wave.level); SPLASHES.removeIf(splash -> mc.level != splash.level);
        SPRAYS.removeIf(spray -> mc.level != spray.level || !loaded(spray.level, spray.block) || !ClassicVectorCombatTimeline.sprayAlive(spray.ticks, spray.level.getBlockState(spray.block).isAir()));
        if (mc.isPaused()) return;
        WAVES.removeIf(wave -> !ClassicVectorCombatTimeline.waveAlive(++wave.ticks));
        SPLASHES.removeIf(splash -> !ClassicVectorCombatTimeline.splashAlive(++splash.ticks));
        SPRAYS.removeIf(spray -> !ClassicVectorCombatTimeline.sprayAlive(++spray.ticks, false));
        WINGS.removeIf(wing -> { if (wing.terminateTicks < 0) wing.stateTicks++; if (wing.terminateTicks >= 0) wing.terminateTicks++; return !ClassicVectorCombatTimeline.wingAlive(wing.terminateTicks); });
    }
    private static void dust(Minecraft mc, Wing wing) {
        for (int i = 0; i < ClassicVectorCombatTimeline.WING_DUST_PER_TICK && DUST.size() < 2048; i++) {
            var sample = ClassicVectorCombatTimeline.wingDust(RANDOM); Vec3 base = wing.caster.position();
            WingDust particle = new WingDust(wing.level, base.add(sample.x(), sample.y(), sample.z()), sample);
            DUST.add(particle); mc.particleEngine.add(particle);
        }
    }
    private static final class WingDust extends TerrainParticle {
        WingDust(ClientLevel level, Vec3 at, ClassicVectorCombatTimeline.Dust dust) {
            super(level, at.x, at.y, at.z, dust.vx(), dust.vy(), dust.vz(), Blocks.DIRT.defaultBlockState());
            // Legacy EntityBlockDustFX sets the supplied motion after randomized vanilla initialization.
            xd = dust.vx(); yd = dust.vy(); zd = dust.vz(); gravity = .02F; scale(.5F);
        }
    }

    @SubscribeEvent public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc); updateClock(mc); if (mc.player == null) return;
        Context context = CONTEXTS.get(new Key("dir_blast", mc.player.getId()));
        if (context == null || !context.valid(mc) || lastHandStack == event.getPoseStack()) return;
        lastHandStack = event.getPoseStack(); long age = CLOCK.elapsed() - context.created;
        var transform = context.punch ? ClassicVectorCombatTimeline.punch(age) : ClassicVectorCombatTimeline.prepare(age);
        // One transform per shared first-person stack, retaining the port's native vanilla arm/item path.
        event.getPoseStack().translate(transform.x(), transform.y(), transform.z());
        event.getPoseStack().mulPose(Axis.XP.rotationDegrees((float) transform.rotationX()));
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees((float) transform.rotationY()));
    }

    private static void blood(Minecraft mc, ClientLevel level, Entity caster, CompoundTag tag) {
        Vec3 base = point(tag), look = direction(tag);
        for (var sample : ClassicVectorCombatTimeline.splashes(RANDOM, tag.getDouble("width"), tag.getDouble("height")))
            if (SPLASHES.size() < 1024) SPLASHES.add(new Splash(level, base.add(sample.x(), sample.y(), sample.z()).add(look.scale(.2)), sample.size()));
        Vec3 head = base.add(0, tag.getDouble("height") * .6, 0);
        var rays = new ArrayList<Vec3>();
        for (int pitch : ClassicVectorCombatTimeline.BLOOD_PITCHES) {
            float yaw = tag.getFloat("yaw") + (-20 + RANDOM.nextFloat() * 40);
            rays.add(look(yaw, pitch));
        }
        // The source maps all nine randomized looks before tracing/creating sprays.
        for (Vec3 ray : rays) {
            BlockHitResult result = level.clip(new ClipContext(head.subtract(ray.scale(.5)), head.add(ray.scale(5)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (result.getType() != HitResult.Type.BLOCK || !loaded(level, result.getBlockPos())) continue;
            int count = 2 + RANDOM.nextInt(1);
            for (int i = 0; i < count && SPRAYS.size() < 2048; i++) SPRAYS.add(new Spray(level, result.getBlockPos(), result.getDirection(), RANDOM));
        }
    }
    private static Vec3 look(double yaw, double pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch), c = Math.cos(p);
        return new Vec3(-Math.sin(y) * c, -Math.sin(p), Math.cos(y) * c);
    }
    private static boolean loaded(ClientLevel level, BlockPos position) { return level.getChunkSource().getChunk(position.getX() >> 4, position.getZ() >> 4, false) != null; }

    @SubscribeEvent public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc); updateClock(mc);
        if (mc.level == null || WAVES.isEmpty() && WINGS.isEmpty() && SPLASHES.isEmpty() && SPRAYS.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        PoseStack poses = event.getPoseStack(); Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix(); poses.pushPose();
        try (GpuState ignored = new GpuState()) {
            modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(1, 1, 1, 1); RenderSystem.enableDepthTest();
            poses.translate(-camera.x, -camera.y, -camera.z);
            // Each original WaveEffect disables depth testing. NO_DEPTH_TEST alone is a modern no-op.
            for (Wave wave : WAVES) {
                poses.pushPose();
                try {
                    poses.translate(wave.position.x, wave.position.y, wave.position.z);
                    poses.mulPose(Axis.YP.rotationDegrees(-wave.yaw)); poses.mulPose(Axis.XP.rotationDegrees(wave.pitch));
                    RenderType type = VectorRenderType.type(texture(ClassicVectorCombatTimeline.WAVE_TEXTURE), true);
                    RenderSystem.disableDepthTest(); VertexConsumer out = buffers.getBuffer(type);
                    for (var ring : wave.rings) {
                        double alpha = ClassicVectorCombatTimeline.waveAlpha(wave.ticks, ring); if (alpha <= 0) continue;
                        poses.pushPose();
                        poses.translate(0, 0, ClassicVectorCombatTimeline.waveDepth(wave.ticks, ring));
                        double size = ClassicVectorCombatTimeline.waveSize(wave.ticks, ring);
                        poses.scale((float) size, (float) size, 1);
                        // MeshUtils arguments are endpoints, not width/height: the classic quad is asymmetrical.
                        quad(out, poses.last().pose(), -.5, -.5, 1, 1, 255, 255, 255, alpha, false);
                        poses.popPose();
                    }
                    buffers.endBatch(type); RenderSystem.enableDepthTest();
                } finally { poses.popPose(); }
            }
            RenderType tornado = VectorRenderType.type(texture(ClassicVectorCombatTimeline.TORNADO_TEXTURE), false);
            VertexConsumer tornadoOut = buffers.getBuffer(tornado);
            for (Wing wing : WINGS) {
                poses.pushPose();
                try {
                    Vec3 center = position(wing.caster, partial).add(0, 1.6, 0); poses.translate(center.x, center.y, center.z);
                    float bodyYaw = wing.caster instanceof LivingEntity living ? Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot) : Mth.rotLerp(partial, wing.caster.yRotO, wing.caster.getYRot());
                    poses.mulPose(Axis.YP.rotationDegrees(-bodyYaw)); poses.mulPose(Axis.XP.rotationDegrees(wing.caster.getXRot() * .2F));
                    poses.mulPose(Axis.XP.rotationDegrees(-70)); poses.translate(0, .2, -.5);
                    double alpha = ClassicVectorCombatTimeline.wingAlpha(wing.active, wing.stateTicks, wing.charge, wing.terminateTicks) * .7;
                    for (int i = 0; i < wing.tornadoes.size(); i++) {
                        var transform = ClassicVectorCombatTimeline.wingTransforms().get(i); var mesh = wing.tornadoes.get(i);
                        poses.pushPose();
                        poses.translate(transform.x(), transform.y(), transform.z());
                        poses.mulPose(Axis.YP.rotationDegrees((float) transform.rotationY())); poses.mulPose(Axis.ZP.rotationDegrees((float) transform.rotationZ()));
                        double time = CLOCK.absolute() / 250.0 - mesh.timeOffset();
                        for (var ring : mesh.rings()) tornadoRing(tornadoOut, poses.last().pose(), ring, time, alpha);
                        poses.popPose();
                    }
                } finally { poses.popPose(); }
            }
            buffers.endBatch(tornado);
            for (Splash splash : SPLASHES) {
                poses.pushPose();
                try {
                    poses.translate(splash.position.x, splash.position.y, splash.position.z); poses.mulPose(event.getCamera().rotation());
                    poses.scale((float) splash.size, (float) splash.size, (float) splash.size);
                    RenderType type = VectorRenderType.type(texture("textures/effects/blood_splash/" + ClassicVectorCombatTimeline.splashFrame(splash.ticks) + ".png"), false);
                    // RenderIcon has a quarter-height lower anchor and inverted V compared with MeshUtils.
                    quad(buffers.getBuffer(type), poses.last().pose(), -.5, -.25, .5, .75, 213, 29, 29, 200 / 255.0, true);
                    buffers.endBatch(type);
                } finally { poses.popPose(); }
            }
            for (Spray spray : SPRAYS) {
                poses.pushPose();
                try {
                    poses.translate(spray.position.x, spray.position.y, spray.position.z);
                    double yaw = switch (spray.side) { case EAST -> 90; case WEST -> -90; case NORTH -> 180; default -> 0; };
                    double pitch = spray.side == Direction.DOWN ? 90 : spray.side == Direction.UP ? -90 : 0;
                    poses.mulPose(Axis.YP.rotationDegrees((float) -yaw)); poses.mulPose(Axis.XP.rotationDegrees((float) -pitch));
                    poses.translate(spray.sample.offsetX(), spray.sample.offsetY(), 0);
                    poses.scale((float) spray.sample.size(), (float) spray.sample.size(), (float) spray.sample.size());
                    poses.mulPose(Axis.ZP.rotationDegrees((float) spray.sample.rotation()));
                    String folder = ClassicVectorCombatTimeline.sprayUsesWall(spray.side.get3DDataValue()) ? "wall" : "grnd";
                    RenderType type = VectorRenderType.type(texture("textures/effects/blood_spray/" + folder + "/" + ClassicVectorCombatTimeline.sprayFrame(spray.sample.texture()) + ".png"), false);
                    quad(buffers.getBuffer(type), poses.last().pose(), -.5, -.5, .5, .5, 255, 255, 255, 1, false);
                    buffers.endBatch(type);
                } finally { poses.popPose(); }
            }
        } finally { poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); }
    }
    private static void tornadoRing(VertexConsumer out, Matrix4f matrix, ClassicVectorCombatTimeline.TornadoRing ring, double time, double alpha) {
        var sample = ClassicVectorCombatTimeline.tornadoSample(ring, time);
        for (int i = 0; i < ClassicVectorCombatTimeline.TORNADO_SEGMENTS; i++) {
            double a = i * Math.PI * 2 / ClassicVectorCombatTimeline.TORNADO_SEGMENTS;
            double b = ((i + 1) % ClassicVectorCombatTimeline.TORNADO_SEGMENTS) * Math.PI * 2 / ClassicVectorCombatTimeline.TORNADO_SEGMENTS;
            double x0 = Math.sin(a) * sample.radius() + sample.dx(), z0 = Math.cos(a) * sample.radius() + sample.dz();
            double x1 = Math.sin(b) * sample.radius() + sample.dx(), z1 = Math.cos(b) * sample.radius() + sample.dz();
            double top = ring.y() + ring.width() / 2, bottom = ring.y() - ring.width() / 2;
            float u0 = (float) (i / 20.0 - sample.rotation()), u1 = (float) (i / 20.0 - sample.rotation() + 1 / 20.0);
            vertex(out, matrix, x0, top, z0, u0, 0, 255, 255, 255, alpha);
            vertex(out, matrix, x0, bottom, z0, u0, 1, 255, 255, 255, alpha);
            vertex(out, matrix, x1, bottom, z1, u1, 1, 255, 255, 255, alpha);
            vertex(out, matrix, x1, top, z1, u1, 0, 255, 255, 255, alpha);
        }
    }
    private static void quad(VertexConsumer out, Matrix4f matrix, double x0, double y0, double x1, double y1,
                             int red, int green, int blue, double alpha, boolean invertV) {
        float low = invertV ? 1 : 0, high = invertV ? 0 : 1;
        vertex(out, matrix, x0, y0, 0, 0, low, red, green, blue, alpha); vertex(out, matrix, x1, y0, 0, 1, low, red, green, blue, alpha);
        vertex(out, matrix, x1, y1, 0, 1, high, red, green, blue, alpha); vertex(out, matrix, x0, y1, 0, 0, high, red, green, blue, alpha);
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, double x, double y, double z, float u, float v, int red, int green, int blue, double alpha) {
        out.addVertex(matrix, (float) x, (float) y, (float) z).setUv(u, v).setColor(red, green, blue, Mth.clamp((int) Math.round(alpha * 255), 0, 255));
    }
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", path); }

    private static final class Context {
        final String skill; final Entity caster; final ClientLevel level; long token, created; int ticks, punchTicks; float consumption; boolean punch, closed;
        Wing wing; FollowingSound loop;
        Context(String skill, Entity caster, long token, long created) { this.skill = skill; this.caster = caster; level = (ClientLevel) caster.level(); this.token = token; this.created = created; }
        boolean valid(Minecraft mc) { return !closed && mc.level == level && validCaster(mc, caster); }
        void close(Minecraft mc) { if (closed) return; closed = true; if (wing != null && wing.terminateTicks < 0) wing.terminateTicks = 0;
            if (loop != null) { loop.finish(); mc.getSoundManager().stop(loop); } }
    }
    private static final class Wave {
        final ClientLevel level; final Vec3 position; final float yaw, pitch; final List<ClassicVectorCombatTimeline.WaveRing> rings; int ticks;
        Wave(ClientLevel level, Entity caster, CompoundTag tag) {
            this.level = level; rings = ClassicVectorCombatTimeline.waveRings(RANDOM);
            position = caster.getEyePosition().lerp(point(tag), .7);
            yaw = tag.getFloat("yaw") + (-20 + RANDOM.nextFloat() * 40); pitch = tag.getFloat("pitch") + (-10 + RANDOM.nextFloat() * 20);
        }
    }
    private static final class Wing {
        final Entity caster; final ClientLevel level; final double charge; final List<ClassicVectorCombatTimeline.Tornado> tornadoes = new ArrayList<>();
        boolean active; int stateTicks, terminateTicks = -1;
        Wing(Entity caster, ClientLevel level, double charge, boolean active) { this.caster = caster; this.level = level; this.charge = charge; this.active = active;
            for (int i = 0; i < 4; i++) tornadoes.add(ClassicVectorCombatTimeline.tornado(RANDOM)); }
    }
    private static final class Splash { final ClientLevel level; final Vec3 position; final double size; int ticks;
        Splash(ClientLevel level, Vec3 position, double size) { this.level = level; this.position = position; this.size = size; } }
    private static final class Spray {
        final ClientLevel level; final BlockPos block; final Direction side; final Vec3 position; final ClassicVectorCombatTimeline.Spray sample; int ticks;
        Spray(ClientLevel level, BlockPos block, Direction side, Random random) {
            this.level = level; this.block = block.immutable(); this.side = side; sample = ClassicVectorCombatTimeline.spray(random, side.get3DDataValue());
            var shape = level.getBlockState(block).getShape(level, block); var bounds = shape.isEmpty() ? new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
            position = new Vec3(block.getX() + (bounds.minX + bounds.maxX) / 2 + side.getStepX() * .51 * (bounds.maxX - bounds.minX),
                    block.getY() + (bounds.minY + bounds.maxY) / 2 + side.getStepY() * .51 * (bounds.maxY - bounds.minY),
                    block.getZ() + (bounds.minZ + bounds.maxZ) / 2 + side.getStepZ() * .51 * (bounds.maxZ - bounds.minZ));
        }
    }
    /** Static blast endpoint; entity-following blood one-shot and storm loop. */
    private static final class FollowingSound extends AbstractTickableSoundInstance {
        final Entity caster; final ClientLevel level; final Object connection; final Context context; final Vec3 staticPosition;
        FollowingSound(Entity caster, ClientLevel level, Object connection, String name, float volume, Context context, Vec3 staticPosition) {
            super(SoundEvent.createVariableRangeEvent(texture(name)), SoundSource.MASTER, RandomSource.create());
            this.caster = caster; this.level = level; this.connection = connection; this.context = context; this.staticPosition = staticPosition;
            this.volume = volume; pitch = 1; looping = context != null; delay = 0; updatePosition();
        }
        @Override public void tick() { Minecraft mc = Minecraft.getInstance(); if (mc.level != level || mc.getConnection() != connection
                || staticPosition == null && !validCaster(mc, caster) || context != null && (!context.valid(mc) || CONTEXTS.get(new Key(context.skill, caster.getId())) != context)) { stop(); return; } updatePosition(); }
        private void updatePosition() { Vec3 at = staticPosition == null ? caster.position() : staticPosition; x = at.x; y = at.y; z = at.z; }
        void finish() { stop(); }
    }
    private static final class VectorRenderType extends RenderType {
        private record TypeKey(ResourceLocation texture, boolean noDepth) {}
        private static final Map<TypeKey, RenderType> TYPES = new HashMap<>();
        private VectorRenderType() { super("unused", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, false, () -> {}, () -> {}); }
        private static final TransparencyStateShard SOURCE_ALPHA = new TransparencyStateShard("academy_vector_source_alpha", () -> {
            RenderSystem.enableBlend(); RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }, () -> { RenderSystem.disableBlend(); RenderSystem.defaultBlendFunc(); });
        static RenderType type(ResourceLocation texture, boolean noDepth) { return TYPES.computeIfAbsent(new TypeKey(texture, noDepth), key -> RenderType.create("academy_vector_combat",
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 65536, false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> key.texture.getPath().equals(ClassicVectorCombatTimeline.TORNADO_TEXTURE)
                                ? (ShaderPackState.active() ? GameRenderer.getPositionTexColorShader() : ClassicSkillAlphaShader.get())
                                : key.texture.getPath().startsWith("textures/effects/blood_splash/")
                                  ? ClassicSkillAlphaShader.get() : GameRenderer.getPositionTexColorShader()))
                        .setTextureState(new TextureStateShard(key.texture, false, false))
                        .setTransparencyState(SOURCE_ALPHA).setDepthTestState(key.noDepth ? NO_DEPTH_TEST : LEQUAL_DEPTH_TEST).setCullState(NO_CULL)
                        .setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false))); }
    }
    private static final class GpuState implements AutoCloseable {
        final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC), srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final ShaderInstance shader = RenderSystem.getShader(); final float[] color = RenderSystem.getShaderColor().clone(); final int texture = RenderSystem.getShaderTexture(0);
        @Override public void close() { if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest(); RenderSystem.depthMask(depthWrite); RenderSystem.depthFunc(depthFunction);
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull(); RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha); if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.setShader(() -> shader); RenderSystem.setShaderTexture(0, texture); RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]); }
    }
}
