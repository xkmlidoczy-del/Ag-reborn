/*
 * AcademyCraft 1.0.7 ThunderClap/EntitySurroundArc/EntityRippleMark client adapter.
 * Copyright (c) Lambda Innovation, 2013-2016. GPLv3; see NOTICE.
 */
package cn.academy.port.client;

import cn.academy.port.skill.ClassicRaytrace;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Client-only source-style BOLD surround and local gray ripple. Forward thunder_clap_start/end
 * tags containing int entity, positive long token and echoed long input nonce. No CP, damage, world mutation, aim upload,
 * or custom audio is performed here; accepted discharge uses the server's vanilla LightningBolt.
 * ClassicArcGeometry is modern CPU geometry, not pixel-identical legacy display-list rendering.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClassicThunderClapEffects {
    private static final ResourceLocation ARC_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "academy", "textures/effects/arc/line_segment.png");
    private static final ResourceLocation RIPPLE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "academy", "textures/effects/ripple.png");
    private static final Map<Integer, Charge> CONTEXTS = new HashMap<>();
    private static final List<Charge> TAILS = new ArrayList<>();
    private static final ClassicThunderClapTimeline.Tokens TOKENS = new ClassicThunderClapTimeline.Tokens();
    private static final ClassicThunderClapTimeline.InputGate INPUT = new ClassicThunderClapTimeline.InputGate();
    private static final ClassicThunderClapTimeline.PauseClock CLOCK = new ClassicThunderClapTimeline.PauseClock();
    private static final List<Pattern> BOLD_PATTERNS = boldPatterns();
    private static final int MAX_CONTEXTS = 128, MAX_TAILS = 128;
    private static ClientLevel activeLevel;
    private static Entity activePlayer;
    private static Object activeConnection;
    private static LocalPlayer slowedPlayer;
    private static long lifecycleEpoch;
    private static MultiBufferSource.BufferSource buffers;

    private ClassicThunderClapEffects() {}

    /**
     * Physical press hook. Return its positive nonce in the authenticated slot_press_token request;
     * return0 when no valid press is possible. No effects or slowdown precede server acceptance.
     */
    public static long startLocal() {
        Minecraft minecraft = Minecraft.getInstance();
        synchronizeSession(minecraft);
        if (minecraft.level != null && minecraft.player != null && minecraft.player.isAlive()
                && minecraft.screen == null) {
            // A fresh physical press owns the local context even if its previous input teardown
            // was interrupted. Old acknowledgement/end nonces cannot touch this newer press.
            abortLocal();
            return INPUT.press();
        }
        return 0;
    }

    /** Key-up/abort, screen opening, preset, category or activation cancellation. Release never fires. */
    public static void abortLocal() {
        Minecraft minecraft = Minecraft.getInstance();
        INPUT.abort();
        if (minecraft.player != null) {
            Charge charge = CONTEXTS.get(minecraft.player.getId());
            if (charge != null && charge.local) {
                CONTEXTS.remove(minecraft.player.getId());
                TOKENS.rememberEnd(charge.caster.getId(), charge.token);
                close(charge, true);
            }
        }
        restoreSpeed();
    }

    /** Copies packet ownership; replaced world/player/connection or an explicit clear drops queued callbacks. */
    public static void receive(CompoundTag data) {
        if (data == null) return;
        CompoundTag tag = data.copy();
        if (!tag.contains("entity", Tag.TAG_INT) || !tag.contains("token", Tag.TAG_LONG)) return;
        String kind = tag.getString("kind");
        if (!"thunder_clap_start".equals(kind) && !"thunder_clap_end".equals(kind)) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel target = minecraft.level;
        Entity localPlayer = minecraft.player;
        Object connection = minecraft.getConnection();
        long epoch = lifecycleEpoch, inputGeneration = INPUT.capture();
        minecraft.execute(() -> {
            if (target == null || minecraft.level != target || minecraft.player != localPlayer
                    || minecraft.getConnection() != connection || lifecycleEpoch != epoch) return;
            synchronizeSession(minecraft);
            updateClock(minecraft);
            int id = tag.getInt("entity");
            long token = tag.getLong("token");
            if (token <= 0) return;
            boolean local = localPlayer != null && id == localPlayer.getId();
            long inputNonce = tag.contains("input", Tag.TAG_LONG) ? tag.getLong("input") : -1;
            // Observers use server tokens alone. Local input must match the echoed physical-press
            // nonce before changing token history, speed, mark, context, or the current input gate.
            if (local && !INPUT.acceptPacket(inputNonce)) return;
            Charge current = CONTEXTS.get(id);
            if ("thunder_clap_end".equals(kind)) {
                TOKENS.rememberEnd(id, token);
                if (current != null && (current.local
                        ? ClassicThunderClapTimeline.matchingLocalEnd(current.token, current.inputNonce, token, inputNonce)
                        : ClassicThunderClapTimeline.matchingEnd(current.token, token))) {
                    CONTEXTS.remove(id);
                    if (current.local) AcademyClient.acceptedSingleEnd("thunder_clap", inputNonce, token);
                    if (current.local) INPUT.end(inputNonce);
                    close(current, true);
                } else if (local && current == null) INPUT.end(inputNonce);
                if (local && current == null) AcademyClient.acceptedSingleEnd("thunder_clap", inputNonce, token);
                return;
            }
            Entity caster = target.getEntity(id);
            if (caster == null || !caster.isAlive() || caster.isRemoved()
                    || current != null && current.token >= token
                    || current == null && CONTEXTS.size() >= MAX_CONTEXTS) return;
            if (caster == localPlayer && (minecraft.screen != null || !INPUT.acceptStart(inputNonce, inputGeneration))) {
                TOKENS.rememberEnd(id, token);
                return;
            }
            if (!TOKENS.acceptStart(id, token)) return;
            if (current != null) close(current, true);
            Charge charge = new Charge(caster, target, token, caster == localPlayer, inputNonce);
            CONTEXTS.put(id, charge);
            if (caster == localPlayer) AcademyClient.acceptedSingleStart("thunder_clap", inputNonce, token);
            // Aim once immediately, then every active tick; the legacy mark's uninitialized origin
            // at spawn is replaced by explicit current block-only target coordinates.
            charge.updateAim();
            charge.previousMark = charge.mark;
        });
    }

    /** Client-thread session cleanup also restores the old player object after death/respawn/world replacement. */
    public static void clear() {
        clearContents();
        lifecycleEpoch++;
    }

    private static void clearContents() {
        for (Charge charge : CONTEXTS.values()) close(charge, false);
        CONTEXTS.clear(); TAILS.clear(); TOKENS.clear(); INPUT.clear(); CLOCK.clear();
        restoreSpeed();
    }

    private static void synchronizeSession(Minecraft minecraft) {
        if (activeLevel == minecraft.level && activePlayer == minecraft.player
                && activeConnection == minecraft.getConnection()) return;
        // Object-identity guards already invalidate callbacks from the old session. Do not change
        // the explicit-clear epoch here: two initial packets queued for this new session are valid.
        clearContents();
        activeLevel = minecraft.level;
        activePlayer = minecraft.player;
        activeConnection = minecraft.getConnection();
    }

    private static void restoreSpeed() {
        if (slowedPlayer != null) setWalkSpeed(slowedPlayer, ClassicThunderClapTimeline.NORMAL_WALK_SPEED);
        slowedPlayer = null;
    }

    private static void setWalkSpeed(LocalPlayer player, float speed) {
        player.getAbilities().setWalkingSpeed(speed);
        // Modern Player.getSpeed() reads this attribute instead of Abilities.walkingSpeed.
        // Change only the local base; keep sprint/potion modifiers and never send an ability packet.
        var movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) movement.setBaseValue(speed);
    }

    private static void close(Charge charge, boolean linger) {
        if (!charge.hold.active()) return;
        charge.hold.end();
        if (charge.local && charge.caster instanceof LocalPlayer player) {
            setWalkSpeed(player, ClassicThunderClapTimeline.NORMAL_WALK_SPEED);
            if (slowedPlayer == player) slowedPlayer = null;
        }
        if (linger && charge.hold.surroundAlive()) {
            if (TAILS.size() >= MAX_TAILS) TAILS.removeFirst();
            TAILS.add(charge);
        } else charge.subArcs.clear();
    }

    private static void updateClock(Minecraft minecraft) {
        CLOCK.update(Util.getMillis(), minecraft.level != null && !minecraft.isPaused());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        synchronizeSession(minecraft);
        updateClock(minecraft);
        if (minecraft.level == null) return;
        // Screen cancellation cannot depend on paused ticking or receipt of a later server packet.
        if (minecraft.screen != null) abortLocal();
        Iterator<Charge> iterator = CONTEXTS.values().iterator();
        while (iterator.hasNext()) {
            Charge charge = iterator.next();
            if (!charge.valid(minecraft)) {
                iterator.remove();
                TOKENS.rememberEnd(charge.caster.getId(), charge.token);
                if (charge.local) INPUT.abort();
                close(charge, false);
            } else if (!minecraft.isPaused()) charge.tick();
        }
        if (!minecraft.isPaused()) TAILS.removeIf(charge -> !charge.valid(minecraft) || !charge.tickTail());
        else TAILS.removeIf(charge -> !charge.valid(minecraft));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // AFTER_LEVEL is after Fabulous compositing. The through-wall ripple must not be rendered
        // into the particles target and then occluded/composited by later world stages.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft minecraft = Minecraft.getInstance();
        synchronizeSession(minecraft);
        updateClock(minecraft);
        if (minecraft.level == null || CONTEXTS.isEmpty() && TAILS.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        PoseStack poses = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var modelView = RenderSystem.getModelViewStack();
        var previousShader = RenderSystem.getShader();
        int previousTexture = RenderSystem.getShaderTexture(0);
        float[] color = RenderSystem.getShaderColor().clone();
        modelView.pushMatrix();
        try {
            // AFTER_LEVEL's event PoseStack is identity and the normal global model-view is restored.
            modelView.set(event.getModelViewMatrix());
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            for (Charge charge : CONTEXTS.values()) if (charge.valid(minecraft)) renderSurround(charge, poses, camera, partial);
            for (Charge charge : TAILS) if (charge.valid(minecraft)) renderSurround(charge, poses, camera, partial);
            buffers.endBatch(RenderTypes.ARC);
            for (Charge charge : CONTEXTS.values())
                if (charge.valid(minecraft) && charge.hold.markVisible(charge.local) && finite(charge.mark))
                    renderRipple(charge, poses, camera, partial);
            buffers.endBatch(RenderTypes.RIPPLE);
        } finally {
            // Private buffers only; never flush vanilla or another mod's geometry.
            try { buffers.endBatch(); }
            finally {
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(515);
                RenderSystem.enableCull();
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderTexture(0, previousTexture);
                RenderSystem.setShader(() -> previousShader);
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
        }
    }

    private static void renderSurround(Charge charge, PoseStack poses, Vec3 camera, float partial) {
        if (!charge.hold.surroundAlive()) return;
        Vec3 center = interpolatedPosition(charge.caster, partial);
        double yaw = Math.toRadians(-(charge.caster instanceof LivingEntity living
                ? Mth.rotLerp(partial, living.yHeadRotO, living.getYHeadRot())
                : Mth.rotLerp(partial, charge.caster.yRotO, charge.caster.getYRot())));
        poses.pushPose();
        try {
            // Modern caster.position() is explicitly feet-based, replacing 1.7's local posY-1.6 workaround.
            poses.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
            Matrix4f matrix = poses.last().pose();
            VertexConsumer out = buffers.getBuffer(RenderTypes.ARC);
            for (SubArc arc : charge.subArcs) {
                if (arc.dead || !arc.shown) continue;
                Pattern pattern = BOLD_PATTERNS.get(arc.pattern);
                Vec3 direction = arc.direction.yRot((float) yaw), u = arc.u.yRot((float) yaw), v = arc.v.yRot((float) yaw);
                Vec3 position = arc.position.yRot((float) yaw)
                        .subtract(direction.scale(pattern.length * ClassicThunderClapTimeline.SURROUND_SCALE / 2));
                renderPaths(out, matrix, position, direction, u, v, pattern.paths);
            }
        } finally { poses.popPose(); }
    }

    private static void renderRipple(Charge charge, PoseStack poses, Vec3 camera, float partial) {
        Vec3 position = charge.previousMark.lerp(charge.mark, partial);
        long elapsed = CLOCK.elapsed() - charge.createdMillis;
        poses.pushPose();
        try {
            // Subtract camera in double precision before converting local mesh vertices to floats.
            poses.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
            Matrix4f matrix = poses.last().pose();
            VertexConsumer out = buffers.getBuffer(RenderTypes.RIPPLE);
            for (int layer = 0; layer < ClassicThunderClapTimeline.RIPPLE_LAYERS; layer++) {
                long phase = ClassicThunderClapTimeline.ripplePhase(elapsed, layer);
                float half = ClassicThunderClapTimeline.rippleSize(phase) / 2;
                float height = ClassicThunderClapTimeline.rippleHeight(phase);
                int alpha = ClassicThunderClapTimeline.colorByte(ClassicThunderClapTimeline.RIPPLE_ALPHA
                        * ClassicThunderClapTimeline.rippleFade(phase));
                rippleVertex(out, matrix, -half, height, -half, 0, 0, alpha);
                rippleVertex(out, matrix, half, height, -half, 0, 1, alpha);
                rippleVertex(out, matrix, half, height, half, 1, 1, alpha);
                rippleVertex(out, matrix, -half, height, half, 1, 0, alpha);
            }
        } finally { poses.popPose(); }
    }

    private static void rippleVertex(VertexConsumer out, Matrix4f matrix, float x, float y, float z, float u, float v, int alpha) {
        out.addVertex(matrix, x, y, z).setUv(u, v).setColor(
                ClassicThunderClapTimeline.colorByte(ClassicThunderClapTimeline.RIPPLE_RED),
                ClassicThunderClapTimeline.colorByte(ClassicThunderClapTimeline.RIPPLE_GREEN),
                ClassicThunderClapTimeline.colorByte(ClassicThunderClapTimeline.RIPPLE_BLUE), alpha);
    }

    private static final class Charge {
        final Entity caster;
        final ClientLevel level;
        final long token, inputNonce, createdMillis;
        final boolean local;
        final double width, height;
        final Random random = new Random();
        final ClassicThunderClapTimeline.Hold hold = new ClassicThunderClapTimeline.Hold();
        final List<SubArc> subArcs = new ArrayList<>();
        Vec3 mark = Vec3.ZERO, previousMark = Vec3.ZERO;

        Charge(Entity caster, ClientLevel level, long token, boolean local, long inputNonce) {
            this.caster = caster; this.level = level; this.token = token; this.local = local; this.inputNonce = inputNonce;
            createdMillis = CLOCK.elapsed();
            width = caster.getBbWidth() * ClassicThunderClapTimeline.ENTITY_SIZE_MULTIPLIER;
            height = caster.getBbHeight() * ClassicThunderClapTimeline.ENTITY_SIZE_MULTIPLIER;
            generateSurround();
        }
        boolean valid(Minecraft minecraft) {
            return minecraft.level == level && caster.level() == level && caster.isAlive() && !caster.isRemoved()
                    && level.getEntity(caster.getId()) == caster && (!local || caster == minecraft.player);
        }
        void tick() {
            previousMark = mark;
            updateAim();
            hold.tick();
            if (local && caster instanceof LocalPlayer player) {
                slowedPlayer = player;
                setWalkSpeed(player, hold.speed());
            }
            tickSurround();
        }
        boolean tickTail() {
            hold.tick();
            if (!hold.surroundAlive()) { subArcs.clear(); return false; }
            tickSurround();
            return true;
        }
        void updateAim() {
            Vec3 start = caster.getEyePosition(), end = start.add(ClassicRaytrace.direction(caster).normalize().scale(ClassicThunderClapTimeline.RANGE));
            // Source EntitySelectors.nothing() still block-traces through ClassicRaytrace.perform.
            HitResult hit = ClassicRaytrace.perform(caster, start, end, ClipContext.Fluid.NONE, entity -> false);
            mark = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
        }
        void tickSurround() {
            if (!hold.surroundAlive()) { subArcs.clear(); return; }
            if (subArcs.isEmpty()) generateSurround();
            Iterator<SubArc> iterator = subArcs.iterator();
            while (iterator.hasNext()) {
                SubArc arc = iterator.next();
                if (arc.dead) iterator.remove();
                else arc.tick(random);
            }
        }
        void generateSurround() {
            for (int i = 0; i < ClassicThunderClapTimeline.BOLD_COUNT; i++)
                subArcs.add(new SubArc(cubePoint(random, width, height), random));
        }
    }

    private record Pattern(double length, List<List<ClassicArcGeometry.Segment>> paths) {}
    private static final class SubArc {
        final Vec3 position, direction, u, v;
        int pattern, age;
        boolean shown, dead;
        SubArc(Vec3 position, Random random) {
            this.position = position;
            pattern = random.nextInt(BOLD_PATTERNS.size());
            float x = (float) (random.nextDouble() * Math.PI * 2), y = (float) (random.nextDouble() * Math.PI * 2),
                    z = (float) (random.nextDouble() * Math.PI * 2);
            // Source GL applies Rz*Ry*Rx; Vec3 X/Z rotation signs are inverted.
            direction = new Vec3(1, 0, 0).xRot(-x).yRot(y).zRot(-z);
            u = new Vec3(0, 1, 0).xRot(-x).yRot(y).zRot(-z);
            v = new Vec3(0, 0, 1).xRot(-x).yRot(y).zRot(-z);
        }
        void tick(Random random) {
            if (ClassicThunderClapTimeline.replaceSubArc(random.nextDouble())) pattern = random.nextInt(BOLD_PATTERNS.size());
            age = ClassicThunderClapTimeline.subArcAge(age, random.nextDouble());
            if (age == ClassicThunderClapTimeline.SUBARC_LIFE) dead = true;
            shown = ClassicThunderClapTimeline.subArcShown(shown, random.nextDouble());
        }
    }

    private static List<Pattern> boldPatterns() {
        Random random = new Random();
        List<Pattern> patterns = new ArrayList<>();
        for (int i = 0; i < ClassicThunderClapTimeline.BOLD_TEMPLATES; i++) {
            double length = ClassicThunderClapTimeline.BOLD_LENGTH_FROM + random.nextDouble()
                    * (ClassicThunderClapTimeline.BOLD_LENGTH_TO_EXCLUSIVE - ClassicThunderClapTimeline.BOLD_LENGTH_FROM);
            patterns.add(new Pattern(length, ClassicArcGeometry.generate(random, length,
                    ClassicThunderClapTimeline.BOLD_PASSES, ClassicThunderClapTimeline.BOLD_WIDTH,
                    ClassicThunderClapTimeline.BOLD_OFFSET, ClassicThunderClapTimeline.BOLD_BRANCH,
                    ClassicThunderClapTimeline.BOLD_WIDTH_SHRINK)));
        }
        return List.copyOf(patterns);
    }

    /** CubePointFactory chooses all six faces equally, centers X/Z only, and leaves Y in [0,height]. */
    private static Vec3 cubePoint(Random random, double width, double height) {
        int face = random.nextInt(6);
        if (face < 2) return new Vec3((random.nextDouble() - .5) * width, face == 0 ? 0 : height,
                (random.nextDouble() - .5) * width);
        if (face < 4) {
            double y = random.nextDouble() * height, x = (random.nextDouble() - .5) * width;
            return new Vec3(x, y, face == 2 ? -width / 2 : width / 2);
        }
        double y = random.nextDouble() * height, z = (random.nextDouble() - .5) * width;
        return new Vec3(face == 4 ? -width / 2 : width / 2, y, z);
    }

    private static void renderPaths(VertexConsumer out, Matrix4f matrix, Vec3 origin, Vec3 direction,
                                    Vec3 u, Vec3 v, List<List<ClassicArcGeometry.Segment>> paths) {
        double scale = ClassicThunderClapTimeline.SURROUND_SCALE;
        for (List<ClassicArcGeometry.Segment> path : paths) {
            Vec3 previousUp = null;
            for (ClassicArcGeometry.Segment segment : path) {
                Vec3 start = arcPoint(origin, direction, u, v, segment.start().position().scale(scale));
                Vec3 end = arcPoint(origin, direction, u, v, segment.end().position().scale(scale));
                Vec3 up = end.subtract(start).cross(v).normalize();
                if (up.lengthSqr() < 1.0E-12) up = u;
                if (previousUp == null) previousUp = up;
                int alpha = Mth.clamp((int) Math.round(255 * segment.alpha()), 0, 255);
                arcVertex(out, matrix, start.add(previousUp.scale(segment.start().width() * scale)), 0, 0, alpha);
                arcVertex(out, matrix, start.subtract(previousUp.scale(segment.start().width() * scale)), 0, 1, alpha);
                arcVertex(out, matrix, end.subtract(up.scale(segment.end().width() * scale)), 1, 1, alpha);
                arcVertex(out, matrix, end.add(up.scale(segment.end().width() * scale)), 1, 0, alpha);
                previousUp = up;
            }
        }
    }
    private static Vec3 arcPoint(Vec3 origin, Vec3 direction, Vec3 u, Vec3 v, Vec3 point) {
        return origin.add(direction.scale(point.x)).add(u.scale(point.y)).add(v.scale(point.z));
    }
    private static void arcVertex(VertexConsumer out, Matrix4f matrix, Vec3 point, float u, float v, int alpha) {
        out.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z).setUv(u, v).setColor(255, 255, 255, alpha);
    }
    private static Vec3 interpolatedPosition(Entity entity, float partial) {
        return new Vec3(Mth.lerp(partial, entity.xo, entity.getX()), Mth.lerp(partial, entity.yo, entity.getY()),
                Mth.lerp(partial, entity.zo, entity.getZ()));
    }
    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    /** Same unlit textured-buffer pattern as ClassicRenderTypes, with explicit source depth states. */
    private static final class RenderTypes extends RenderType {
        private RenderTypes() {
            super("unused", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096,
                    false, false, () -> {}, () -> {});
        }
        // 1.21.1 NO_DEPTH_TEST(function519) is a no-op. The source ripple explicitly disables depth.
        private static final DepthTestStateShard RIPPLE_DEPTH = new DepthTestStateShard("academy_thunder_clap_no_depth", 519) {
            @Override public void setupRenderState() { RenderSystem.disableDepthTest(); }
            @Override public void clearRenderState() { RenderSystem.enableDepthTest(); RenderSystem.depthFunc(515); }
        };
        private static final DepthTestStateShard ARC_DEPTH = new DepthTestStateShard("academy_thunder_clap_depth", 515) {
            @Override public void setupRenderState() { RenderSystem.enableDepthTest(); RenderSystem.depthFunc(515); }
            @Override public void clearRenderState() { RenderSystem.enableDepthTest(); RenderSystem.depthFunc(515); }
        };
        private static final TransparencyStateShard LEGACY_ALPHA = new TransparencyStateShard("academy_thunder_clap_alpha", () -> {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }, () -> { RenderSystem.disableBlend(); RenderSystem.defaultBlendFunc(); });
        static final RenderType ARC = create("academy_thunder_clap_bold", ARC_TEXTURE, ARC_DEPTH);
        static final RenderType RIPPLE = create("academy_thunder_clap_ripple", RIPPLE_TEXTURE, RIPPLE_DEPTH);
        private static RenderType create(String name, ResourceLocation texture, DepthTestStateShard depth) {
            return RenderType.create(name, DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096,
                    false, false, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> depth == RIPPLE_DEPTH
                                    ? ClassicSkillAlphaShader.get() : GameRenderer.getPositionTexColorShader()))
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(LEGACY_ALPHA).setDepthTestState(depth).setCullState(NO_CULL)
                            .setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                            .setWriteMaskState(COLOR_WRITE).setOutputState(MAIN_TARGET).createCompositeState(false));
        }
    }
}
