/* AcademyCraft1.0.7 BodyIntensify/CurrentChargingHUD/EntityIntensifyEffect adapter. GPLv3; see NOTICE. */
package cn.academy.port.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Client-only visuals/audio. Forward body_intensify_start/end tags from the client packet switch.
 * Start/end require entity:int, token:long>0; end additionally requires performed:boolean.
 * Local physical-input packets require input:long matching startLocal()'s positive nonce.
 * Internal non-input-driven local contexts accept input0; observers need no matching input nonce.
 * The loop/HUD are local-only; successful end broadcasts create one following activation sound
 * and one fifteen-tick thin-arc effect per accepted token, including observers without a start.
 * No potion, CP, hunger, cooldown, experience, movement, or network mutation occurs here.
 * The original texture/sound/timing inputs are retained. CPU ribbon geometry is source-derived,
 * replacing legacy randomized GL display lists; rendered-pixel and audible parity are unverified.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClassicBodyIntensifyEffects {
    private static final ResourceLocation MASK = resource(ClassicBodyIntensifyTimeline.MASK_TEXTURE);
    private static final ResourceLocation WHITE = resource("textures/port/white.png");
    private static final ResourceLocation ARC_TEXTURE = resource("textures/effects/arc/line_segment.png");
    private static final List<ResourceLocation> HUD_ARCS = hudTextures();
    private static final SoundEvent LOOP = sound(ClassicBodyIntensifyTimeline.LOOP_SOUND);
    private static final SoundEvent ACTIVATE = sound(ClassicBodyIntensifyTimeline.ACTIVATE_SOUND);
    private static final Map<Integer, Context> CONTEXTS = new HashMap<>();
    private static final List<Activation> ACTIVATIONS = new ArrayList<>();
    private static final List<FollowingSound> ONE_SHOTS = new ArrayList<>();
    private static final ClassicBodyIntensifyTimeline.Tokens TOKENS = new ClassicBodyIntensifyTimeline.Tokens();
    private static final ClassicBodyIntensifyTimeline.InputNonce LOCAL_INPUT = new ClassicBodyIntensifyTimeline.InputNonce();
    private static final ClassicBodyIntensifyTimeline.PauseClock CLOCK = new ClassicBodyIntensifyTimeline.PauseClock();
    private static final List<Pattern> THIN = thinPatterns();
    private static ClientLevel activeLevel;
    private static Entity activePlayer;
    private static Object activeConnection;
    private static MultiBufferSource.BufferSource buffers;
    private static ClassicBodyIntensifyTimeline.Hud hud;
    private static volatile long callbackEpoch;

    private ClassicBodyIntensifyEffects() {}

    /** Physical-press hook returns the nonce to include in slot_press_token; no speculative audio. */
    public static long startLocal() {
        Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc);
        Context prior = mc.player == null ? null : CONTEXTS.remove(mc.player.getId());
        if (prior != null) { TOKENS.rememberAbort(prior.caster.getId(), prior.token); prior.close(mc); }
        hud = null;
        return LOCAL_INPUT.press();
    }

    /** GUI/category/ability/key-abort hook; ordinary key release must await the server performed flag. */
    public static void abortLocal() {
        Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc); updateClock(mc);
        LOCAL_INPUT.abort();
        if (mc.player == null) return;
        Context current = CONTEXTS.remove(mc.player.getId());
        if (current != null) { TOKENS.rememberAbort(current.caster.getId(), current.token); current.close(mc); }
        if (hud != null) hud.startBlend(CLOCK.elapsed(), false, false);
    }

    /** Client HUD bridge: -1 means absent; elapsed ticks are visual-only and never authorize effects. */
    public static int localTicks() {
        Minecraft mc = Minecraft.getInstance();
        Context current = mc.player == null ? null : CONTEXTS.get(mc.player.getId());
        return current == null || !current.valid(mc) ? -1 : current.ticks;
    }

    /** Copy before thread crossing; level, player and connection identity isolate queued old sessions. */
    public static void receive(CompoundTag data) {
        if (data == null) return;
        CompoundTag tag = data.copy();
        String kind = tag.getString("kind");
        if (!"body_intensify_start".equals(kind) && !"body_intensify_end".equals(kind)) return;
        if (!tag.contains("entity", Tag.TAG_INT) || !tag.contains("token", Tag.TAG_LONG)
                || "body_intensify_end".equals(kind) && !tag.contains("performed", Tag.TAG_BYTE)) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level; Entity local = mc.player; Object connection = mc.getConnection();
        long epoch = callbackEpoch;
        mc.execute(() -> {
            if (level == null || local == null || mc.level != level || mc.player != local
                    || mc.getConnection() != connection || callbackEpoch != epoch) return;
            synchronizeSession(mc); updateClock(mc);
            int id = tag.getInt("entity"); long token = tag.getLong("token");
            if (token <= 0) return;
            // Match physical input before token history/context/HUD mutations. An old ack for
            // press A can neither revive A nor end a newer press B, regardless of server token.
            long input = tag.getLong("input");
            boolean localPacket = id == local.getId();
            if (localPacket && (!tag.contains("input", Tag.TAG_LONG) || !LOCAL_INPUT.accepts(input))) return;
            Context current = CONTEXTS.get(id);
            if ("body_intensify_end".equals(kind)) {
                if (!TOKENS.acceptEnd(id, token)) return;
                if (localPacket) AcademyClient.acceptedSingleEnd("body_intensify", input, token);
                if (localPacket) LOCAL_INPUT.complete(input);
                if (current != null && current.token <= token) {
                    CONTEXTS.remove(id); current.close(mc);
                    if (current.caster == local && hud != null) {
                        hud.startBlend(CLOCK.elapsed(), tag.getBoolean("performed"), mc.options.getCameraType().isFirstPerson());
                    }
                }
                Entity caster = level.getEntity(id);
                if (!tag.getBoolean("performed") || !validCaster(mc, caster)) return;
                if (ACTIVATIONS.size() < 128) ACTIVATIONS.add(new Activation(caster, level));
                ONE_SHOTS.removeIf(existing -> !mc.getSoundManager().isActive(existing));
                if (ONE_SHOTS.size() < 128) {
                    FollowingSound activationSound = new FollowingSound(caster, level, connection, ACTIVATE, null);
                    ONE_SHOTS.add(activationSound); mc.getSoundManager().play(activationSound);
                }
                return;
            }
            Entity caster = level.getEntity(id);
            if (!validCaster(mc, caster) || current != null && current.token >= token
                    || current == null && CONTEXTS.size() >= 128 || !TOKENS.acceptStart(id, token)) return;
            if (current != null) current.close(mc);
            Context next = new Context(caster, level, token);
            CONTEXTS.put(id, next);
            if (localPacket) AcademyClient.acceptedSingleStart("body_intensify", input, token);
            if (caster == local) {
                hud = new ClassicBodyIntensifyTimeline.Hud(CLOCK.elapsed(), new Random().nextLong());
                next.loop = new FollowingSound(caster, level, connection, LOOP, next);
                mc.getSoundManager().play(next.loop);
            }
        });
    }

    /** Stops every owned sound and removes HUD, world effects and replay history on session replacement. */
    public static void clear() {
        callbackEpoch++;
        clearOwned();
    }

    private static void clearOwned() {
        Minecraft mc = Minecraft.getInstance();
        for (Context context : CONTEXTS.values()) context.close(mc);
        for (FollowingSound sound : ONE_SHOTS) { sound.finish(); mc.getSoundManager().stop(sound); }
        CONTEXTS.clear(); ACTIVATIONS.clear(); ONE_SHOTS.clear(); TOKENS.clear(); CLOCK.clear();
        hud = null; LOCAL_INPUT.clear();
    }

    private static void synchronizeSession(Minecraft mc) {
        if (activeLevel == mc.level && activePlayer == mc.player && activeConnection == mc.getConnection()) return;
        // The captured level/player/connection reject old sessions. Do not invalidate queued valid
        // packets just because this is the first lazy synchronization of their current session.
        clearOwned(); activeLevel = mc.level; activePlayer = mc.player; activeConnection = mc.getConnection();
    }
    private static void updateClock(Minecraft mc) { CLOCK.update(Util.getMillis(), mc.level != null && !mc.isPaused()); }

    @SubscribeEvent public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc); updateClock(mc);
        if (mc.level == null) return;
        var iterator = CONTEXTS.values().iterator();
        while (iterator.hasNext()) {
            Context context = iterator.next();
            if (!context.valid(mc)) {
                iterator.remove(); TOKENS.rememberAbort(context.caster.getId(), context.token); context.close(mc);
                if (context.caster == mc.player) {
                    LOCAL_INPUT.abort();
                    if (hud != null) hud.startBlend(CLOCK.elapsed(), false, false);
                }
            } else if (!mc.isPaused()) context.ticks++;
        }
        ONE_SHOTS.removeIf(sound -> !mc.getSoundManager().isActive(sound));
        ACTIVATIONS.removeIf(effect -> !effect.valid(mc));
        if (hud != null && hud.disposed(CLOCK.elapsed())) hud = null;
        if (mc.isPaused()) return;
        if (hud != null) hud.tick();
        ACTIVATIONS.removeIf(effect -> !effect.tick());
    }

    /** HIGH runs this background before the port's NORMAL-priority ability HUD at the same stage. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc); updateClock(mc);
        if (mc.player == null || mc.level == null || mc.options.hideGui || hud == null) return;
        if (hud.disposed(CLOCK.elapsed())) { hud = null; return; }
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.flush();
        try (GpuState ignored = new GpuState()) {
            // MC1.21.1 NO_DEPTH_TEST is a no-op. Disable the actual GL test explicitly.
            RenderSystem.disableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
            RenderSystem.enableBlend(); RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            double width = graphics.guiWidth(), height = graphics.guiHeight();
            double alpha = hud.maskAlpha(CLOCK.elapsed());
            rect(graphics, WHITE, 0, 0, width, height, 0, 0, 0, ClassicBodyIntensifyTimeline.BLACK_MASK_ALPHA * alpha);
            rect(graphics, MASK, 0, 0, width, height, 1, 1, 1, alpha);
            for (var sprite : hud.sprites()) if (!sprite.dead && sprite.shown)
                rect(graphics, HUD_ARCS.get(sprite.template), sprite.left(width), sprite.top(height),
                        sprite.size, sprite.size, 1, 1, 1, hud.arcAlpha());
        }
    }

    @SubscribeEvent public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc);
        if (mc.level == null || ACTIVATIONS.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        var type = BodyRenderType.TYPE;
        var poses = event.getPoseStack(); Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix(); poses.pushPose();
        try (GpuState ignored = new GpuState()) {
            // The stage's PoseStack is independent of its camera matrix. Scope the explicit event
            // model-view for both Fast/Fancy and Fabulous paths, avoiding inherited shader state.
            modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(1, 1, 1, 1); RenderSystem.enableDepthTest();
            VertexConsumer out = buffers.getBuffer(type);
            for (Activation effect : ACTIVATIONS) if (effect.valid(mc)) {
                Vec3 center = position(effect.caster, partial).subtract(camera);
                float yaw = (float) Math.toRadians(-(effect.caster instanceof LivingEntity living
                        ? Mth.rotLerp(partial, living.yHeadRotO, living.getYHeadRot())
                        : Mth.rotLerp(partial, effect.caster.yRotO, effect.caster.getYRot())));
                for (WorldArc arc : effect.arcs) if (!arc.dead && arc.shown) {
                    Pattern pattern = THIN.get(arc.template);
                    Vec3 direction = arc.direction.yRot(yaw), u = arc.u.yRot(yaw), v = arc.v.yRot(yaw);
                    Vec3 origin = center.add(arc.position.yRot(yaw))
                            .subtract(direction.scale(pattern.length * ClassicBodyIntensifyTimeline.WORLD_SCALE / 2));
                    paths(out, poses.last().pose(), origin, direction, u, v, pattern.paths);
                }
            }
            // Private buffers and a single owned type never flush another mod's pending geometry.
            buffers.endBatch(type);
        } finally { poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); }
    }

    private static final class Context {
        final Entity caster; final ClientLevel level; final long token;
        FollowingSound loop; int ticks; boolean closed;
        Context(Entity caster, ClientLevel level, long token) { this.caster = caster; this.level = level; this.token = token; }
        boolean valid(Minecraft mc) { return !closed && mc.level == level && validCaster(mc, caster); }
        void close(Minecraft mc) { if (closed) return; closed = true; if (loop != null) { loop.finish(); mc.getSoundManager().stop(loop); } }
    }

    /** Both source sounds are FollowEntitySound; its inherited default volume is .5. */
    private static final class FollowingSound extends AbstractTickableSoundInstance {
        final Entity caster; final ClientLevel level; final Object connection; final Context context;
        FollowingSound(Entity caster, ClientLevel level, Object connection, SoundEvent sound, Context context) {
            super(sound, SoundSource.MASTER, RandomSource.create());
            this.caster = caster; this.level = level; this.connection = connection; this.context = context;
            volume = ClassicBodyIntensifyTimeline.SOUND_VOLUME; pitch = 1; looping = context != null; delay = 0; updatePosition();
        }
        @Override public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != level || mc.getConnection() != connection || !validCaster(mc, caster)
                    || context != null && (!context.valid(mc) || CONTEXTS.get(caster.getId()) != context)) { stop(); return; }
            updatePosition();
        }
        private void updatePosition() { x = caster.getX(); y = caster.getY(); z = caster.getZ(); }
        void finish() { stop(); }
    }

    private static final class Activation {
        final Entity caster; final ClientLevel level; final Random random = new Random();
        final List<WorldArc> arcs = new ArrayList<>(); int ticks;
        Activation(Entity caster, ClientLevel level) { this.caster = caster; this.level = level; }
        boolean valid(Minecraft mc) { return mc.level == level && validCaster(mc, caster); }
        boolean tick() {
            // One effect, seven source callback heights; doGenerate is disabled, so no regeneration.
            for (var wave : ClassicBodyIntensifyTimeline.waves()) if (wave.tick() == ticks) {
                int count = ClassicBodyIntensifyTimeline.rangedCount(random,
                        ClassicBodyIntensifyTimeline.WORLD_COUNT_FROM, ClassicBodyIntensifyTimeline.WORLD_COUNT_TO);
                for (int i = 0; i < count; i++) {
                    double radius = ClassicBodyIntensifyTimeline.ranged(random,
                            ClassicBodyIntensifyTimeline.WORLD_RADIUS_FROM, ClassicBodyIntensifyTimeline.WORLD_RADIUS_TO);
                    double theta = random.nextDouble() * Math.PI * 2;
                    arcs.add(new WorldArc(new Vec3(radius * Math.sin(theta), wave.height(), radius * Math.cos(theta)), random));
                }
            }
            var iterator = arcs.iterator();
            while (iterator.hasNext()) { var arc = iterator.next(); if (arc.dead) iterator.remove(); else arc.tick(random); }
            return ++ticks < ClassicBodyIntensifyTimeline.WORLD_LIFE;
        }
    }

    private static final class WorldArc {
        final Vec3 position, direction, u, v; int template, age; boolean shown, dead;
        WorldArc(Vec3 position, Random random) {
            this.position = position; template = random.nextInt(THIN.size());
            float x = (float) (random.nextDouble() * Math.PI * 2), y = (float) (random.nextDouble() * Math.PI * 2),
                    z = (float) (random.nextDouble() * Math.PI * 2);
            // Legacy GL multiplies Rz*Ry*Rx. Modern Vec3's X/Z rotation signs are opposite.
            direction = new Vec3(1, 0, 0).xRot(-x).yRot(y).zRot(-z);
            u = new Vec3(0, 1, 0).xRot(-x).yRot(y).zRot(-z); v = new Vec3(0, 0, 1).xRot(-x).yRot(y).zRot(-z);
        }
        void tick(Random random) {
            if (ClassicBodyIntensifyTimeline.replaceTemplate(ClassicBodyIntensifyTimeline.WORLD_FRAME_RATE, random.nextDouble())) template = random.nextInt(THIN.size());
            age = ClassicBodyIntensifyTimeline.subArcAge(age, random.nextDouble());
            if (age == ClassicBodyIntensifyTimeline.WORLD_ARC_LIFE) dead = true;
            shown = ClassicBodyIntensifyTimeline.subArcShown(shown, ClassicBodyIntensifyTimeline.WORLD_SWITCH_RATE, random.nextDouble());
        }
    }

    private record Pattern(double length, List<List<ClassicArcGeometry.Segment>> paths) {}
    private static List<Pattern> thinPatterns() {
        Random random = new Random(); List<Pattern> result = new ArrayList<>();
        for (int i = 0; i < ClassicBodyIntensifyTimeline.WORLD_TEMPLATES; i++) {
            double length = ClassicBodyIntensifyTimeline.ranged(random, ClassicBodyIntensifyTimeline.WORLD_LENGTH_FROM, ClassicBodyIntensifyTimeline.WORLD_LENGTH_TO);
            result.add(new Pattern(length, ClassicArcGeometry.generate(random, length, ClassicBodyIntensifyTimeline.WORLD_PASSES,
                    ClassicBodyIntensifyTimeline.WORLD_WIDTH, ClassicBodyIntensifyTimeline.WORLD_OFFSET,
                    ClassicBodyIntensifyTimeline.WORLD_BRANCH, ClassicBodyIntensifyTimeline.WORLD_SHRINK)));
        }
        return result;
    }
    private static void paths(VertexConsumer out, Matrix4f matrix, Vec3 origin, Vec3 direction, Vec3 u, Vec3 v,
                              List<List<ClassicArcGeometry.Segment>> paths) {
        double scale = ClassicBodyIntensifyTimeline.WORLD_SCALE;
        for (var path : paths) {
            Vec3 previous = null;
            for (var segment : path) {
                Vec3 start = arcPoint(origin, direction, u, v, segment.start().position().scale(scale));
                Vec3 end = arcPoint(origin, direction, u, v, segment.end().position().scale(scale));
                Vec3 up = end.subtract(start).cross(v).normalize(); if (up.lengthSqr() < 1E-12) up = u;
                if (previous == null) previous = up;
                int alpha = Mth.clamp((int) Math.round(255 * segment.alpha()), 0, 255);
                vertex(out, matrix, start.add(previous.scale(segment.start().width() * scale)), 0, 0, alpha);
                vertex(out, matrix, start.subtract(previous.scale(segment.start().width() * scale)), 0, 1, alpha);
                vertex(out, matrix, end.subtract(up.scale(segment.end().width() * scale)), 1, 1, alpha);
                vertex(out, matrix, end.add(up.scale(segment.end().width() * scale)), 1, 0, alpha); previous = up;
            }
        }
    }
    private static Vec3 arcPoint(Vec3 origin, Vec3 direction, Vec3 u, Vec3 v, Vec3 point) { return origin.add(direction.scale(point.x)).add(u.scale(point.y)).add(v.scale(point.z)); }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 point, float u, float v, int alpha) { out.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z).setUv(u, v).setColor(255, 255, 255, alpha); }
    private static Vec3 position(Entity caster, float partial) { return new Vec3(Mth.lerp(partial, caster.xo, caster.getX()), Mth.lerp(partial, caster.yo, caster.getY()), Mth.lerp(partial, caster.zo, caster.getZ())); }
    private static boolean validCaster(Minecraft mc, Entity caster) { return caster != null && mc.level != null && caster.isAlive() && !caster.isRemoved() && caster.level() == mc.level && mc.level.getEntity(caster.getId()) == caster; }
    private static SoundEvent sound(String path) { return SoundEvent.createVariableRangeEvent(resource(path)); }
    private static ResourceLocation resource(String path) { return ResourceLocation.fromNamespaceAndPath("academy", path); }
    private static List<ResourceLocation> hudTextures() { var result = new ArrayList<ResourceLocation>(); for (int i = 0; i < ClassicBodyIntensifyTimeline.HUD_TEMPLATES; i++) result.add(resource(ClassicBodyIntensifyTimeline.ARC_PREFIX + i + ".png")); return result; }

    private static void rect(GuiGraphics graphics, ResourceLocation texture, double x, double y, double width,
                             double height, float red, float green, float blue, double alpha) {
        if (alpha <= 0) return;
        RenderSystem.setShader(ClassicSkillAlphaShader::get); RenderSystem.setShaderTexture(0, texture);
        BufferBuilder out = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();
        out.addVertex(matrix, (float) x, (float) (y + height), 0).setUv(0, 1).setColor(red, green, blue, (float) alpha);
        out.addVertex(matrix, (float) (x + width), (float) (y + height), 0).setUv(1, 1).setColor(red, green, blue, (float) alpha);
        out.addVertex(matrix, (float) (x + width), (float) y, 0).setUv(1, 0).setColor(red, green, blue, (float) alpha);
        out.addVertex(matrix, (float) x, (float) y, 0).setUv(0, 0).setColor(red, green, blue, (float) alpha);
        BufferUploader.drawWithShader(out.buildOrThrow());
    }

    /** Same modern buffer pattern as ClassicRenderTypes, with original blend factors/insertion order. */
    private static final class BodyRenderType extends RenderType {
        private BodyRenderType() { super("unused", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
                4096, false, false, () -> {}, () -> {}); }
        private static final TransparencyStateShard SOURCE_ALPHA = new TransparencyStateShard("academy_body_source_alpha", () -> {
            RenderSystem.enableBlend(); RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }, () -> { RenderSystem.disableBlend(); RenderSystem.defaultBlendFunc(); });
        static final RenderType TYPE = RenderType.create("academy_body_intensify", DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 65536, false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new TextureStateShard(ARC_TEXTURE, false, false))
                        .setTransparencyState(SOURCE_ALPHA).setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                        .setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false));
    }

    /** Restore the caller's actual GL state, not assumed vanilla defaults; all setters update MC caches. */
    private static final class GpuState implements AutoCloseable {
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC), srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final ShaderInstance shader = RenderSystem.getShader();
        private final float[] color = RenderSystem.getShaderColor().clone();
        private final int texture = RenderSystem.getShaderTexture(0);
        @Override public void close() {
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(depthWrite); RenderSystem.depthFunc(depthFunction);
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.setShader(() -> shader); RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        }
    }
}
