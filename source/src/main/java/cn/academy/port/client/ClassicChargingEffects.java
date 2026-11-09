/*
 * AcademyCraft 1.0.7 CurrentCharging/EntityArc/EntitySurroundArc visual and sound adapter.
 * Copyright (c) Lambda Innovation, 2013-2016. GPLv3; see NOTICE.
 */
package cn.academy.port.client;

import cn.academy.port.skill.ChargingEnergy;
import cn.academy.port.skill.ClassicRaytrace;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Client-only adapter. Forward charging_start/charging_end only from the client packet switch.
 * No energy transfer, inventory mutation, damage, or client-to-server aim packets happen here.
 * Shared ClassicArcGeometry produces source-style ribbons, not pixel-identical legacy GL output.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClassicChargingEffects {
    private static final ResourceLocation ARC_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "academy", "textures/effects/arc/line_segment.png");
    private static final SoundEvent LOOP_SOUND = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath("academy", "em.charge_loop"));
    private static final Map<Integer, Charging> CONTEXTS = new HashMap<>();
    private static final ClassicChargingTimeline.Tokens TOKENS = new ClassicChargingTimeline.Tokens();
    private static final List<Pattern> BEAM_PATTERNS = beamPatterns();
    private static final List<Pattern> THIN_PATTERNS = surroundPatterns(true);
    private static final List<Pattern> NORMAL_PATTERNS = surroundPatterns(false);
    private static ClientLevel activeLevel;
    private static Entity activePlayer;
    private static Object activeConnection;
    private static MultiBufferSource.BufferSource buffers;

    private ClassicChargingEffects() {}

    /** Copies data before crossing threads and drops callbacks belonging to a replaced session. */
    public static void receive(CompoundTag data) {
        if (data == null) return;
        CompoundTag tag = data.copy();
        if (!tag.contains("entity", Tag.TAG_INT) || !tag.contains("token", Tag.TAG_LONG)
                || !tag.contains("input", Tag.TAG_LONG) || !tag.hasUUID("entity_uuid")
                || !tag.contains("owner_epoch", Tag.TAG_LONG) || tag.getLong("input") < 0
                || tag.getLong("owner_epoch") < 0
                || tag.getLong("input") > 0 && tag.getLong("owner_epoch") <= 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel target = minecraft.level;
        Entity localPlayer = minecraft.player;
        Object connection = minecraft.getConnection();
        long epoch = AcademyClient.inputSessionEpoch();
        minecraft.execute(() -> {
            if (target == null || minecraft.level != target || minecraft.player != localPlayer
                    || minecraft.getConnection() != connection || !AcademyClient.sameInputSessionEpoch(epoch)) return;
            int id = tag.getInt("entity");
            long token = tag.getLong("token");
            long input = tag.getLong("input");
            if (token <= 0) return;
            Entity caster = target.getEntity(id);
            if (caster == null || !caster.getUUID().equals(tag.getUUID("entity_uuid"))) return;
            if (caster == localPlayer && input > 0
                    && !AcademyClient.singleOwnerEpochMatches(tag.getLong("owner_epoch"))) return;
            synchronizeSession(minecraft);
            Charging current = CONTEXTS.get(id);
            if ("charging_end".equals(tag.getString("kind"))) {
                if (current != null && current.token == token
                        && (current.input != input || current.ownerEpoch != tag.getLong("owner_epoch"))) return;
                TOKENS.rememberEnd(id, token);
                if (caster == localPlayer && (input == 0 || AcademyClient.singleTerminalAllowed("charging", input, token)))
                    AcademyClient.acceptedSingleEnd("charging", input, token);
                if (current != null && current.caster == caster && current.input == input
                        && current.ownerEpoch == tag.getLong("owner_epoch")
                        && ClassicChargingTimeline.matchingEnd(current.token, token)) {
                    CONTEXTS.remove(id);
                    current.close(minecraft);
                }
                return;
            }
            if (!"charging_start".equals(tag.getString("kind"))
                    || !tag.contains("item_mode", Tag.TAG_BYTE)) return;
            if (!caster.isAlive() || caster.isRemoved()
                    || current != null && current.token >= token
                    || current == null && CONTEXTS.size() >= 128
                    || caster == localPlayer && input > 0 && !AcademyClient.singleStartAllowed("charging", input, token)
                    || caster == localPlayer && input == 0 && !AcademyClient.legacySingleStartAllowed("charging")
                    || !TOKENS.acceptStart(id, token)) return;
            if (current != null) current.close(minecraft);
            Charging charging = new Charging(caster, target, input, token, tag.getLong("owner_epoch"), tag.getBoolean("item_mode"));
            CONTEXTS.put(id, charging);
            if (caster == localPlayer) AcademyClient.acceptedSingleStart("charging", input, token);
            charging.updateAim();
            minecraft.getSoundManager().play(charging.loop);
        });
    }

    /** Client-thread session hook; the subscriber also detects level, player, and connection changes. */
    public static void clear() { clear(Minecraft.getInstance()); }

    /** Local END/rejection cleanup only; no wire action and no remote-caster removal. */
    public static void endLocal(long input) {
        if (input <= 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        Entity localPlayer = minecraft.player;
        if (localPlayer == null) return;
        Charging current = CONTEXTS.get(localPlayer.getId());
        if (current != null && current.caster == localPlayer && current.input == input)
            endLocal(input, current.ownerEpoch);
    }

    /** Deferred END uses its immutable captured epoch even if a newer node reuses the input. */
    public static void endLocal(long input, long ownerEpoch) {
        if (input <= 0 || ownerEpoch <= 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        Entity localPlayer = minecraft.player;
        if (localPlayer == null) return;
        Charging current = CONTEXTS.get(localPlayer.getId());
        if (current != null && current.caster == localPlayer && current.input == input && current.ownerEpoch == ownerEpoch
                && CONTEXTS.remove(localPlayer.getId(), current)) current.close(minecraft);
    }

    private static void clear(Minecraft minecraft) {
        for (Charging charging : CONTEXTS.values()) charging.close(minecraft);
        CONTEXTS.clear();
        TOKENS.clear();
    }

    private static void synchronizeSession(Minecraft minecraft) {
        if (activeLevel == minecraft.level && activePlayer == minecraft.player
                && activeConnection == minecraft.getConnection()) return;
        clear(minecraft);
        activeLevel = minecraft.level;
        activePlayer = minecraft.player;
        activeConnection = minecraft.getConnection();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        synchronizeSession(minecraft);
        if (minecraft.level == null) return;
        Iterator<Charging> iterator = CONTEXTS.values().iterator();
        while (iterator.hasNext()) {
            Charging charging = iterator.next();
            if (!charging.valid(minecraft)) {
                iterator.remove();
                charging.close(minecraft);
            } else if (!minecraft.isPaused()) charging.tick();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        synchronizeSession(minecraft);
        if (minecraft.level == null || CONTEXTS.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        RenderType type = ClassicRenderTypes.world(ARC_TEXTURE);
        Set<RenderType> used = new LinkedHashSet<>();
        PoseStack poses = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        poses.pushPose();
        try {
            Matrix4f matrix = poses.last().pose();
            for (Charging charging : CONTEXTS.values()) {
                if (!charging.valid(minecraft)) continue;
                if (!charging.itemMode && charging.beamShown && charging.beamValid) {
                    Vec3 origin = charging.previousOrigin.lerp(charging.origin, partial);
                    Vec3 delta = charging.previousEndpoint.lerp(charging.endpoint, partial).subtract(origin);
                    double length = delta.length();
                    if (length > 1.0E-6 && Double.isFinite(length)) {
                        Vec3 direction = delta.scale(1 / length);
                        // Equivalent local basis to EntityArc's yaw/pitch transform and normal=(0,0,1).
                        Vec3 v = direction.cross(new Vec3(0, 1, 0));
                        v = v.lengthSqr() < 1.0E-12 ? new Vec3(-1, 0, 0) : v.normalize();
                        Vec3 u = v.cross(direction).normalize();
                        used.add(type);
                        renderPaths(buffers.getBuffer(type), matrix, origin, camera, direction, u, v, 1, length,
                                BEAM_PATTERNS.get(charging.beamPattern).paths);
                    }
                }
                if (!charging.surroundShown) continue;
                Vec3 center = charging.itemMode ? interpolatedPosition(charging.caster, partial)
                        : charging.surroundPosition;
                double yaw = Math.toRadians(-(charging.caster instanceof LivingEntity living
                        ? Mth.rotLerp(partial, living.yHeadRotO, living.getYHeadRot())
                        : Mth.rotLerp(partial, charging.caster.yRotO, charging.caster.getYRot())));
                for (SubArc arc : charging.subArcs) {
                    if (arc.dead || !arc.shown) continue;
                    Pattern pattern = charging.surroundPatterns.get(arc.pattern);
                    Vec3 direction = arc.direction.yRot((float) (charging.itemMode ? yaw : 0));
                    Vec3 u = arc.u.yRot((float) (charging.itemMode ? yaw : 0));
                    Vec3 v = arc.v.yRot((float) (charging.itemMode ? yaw : 0));
                    Vec3 position = center.add(arc.position.yRot((float) (charging.itemMode ? yaw : 0)))
                            .subtract(direction.scale(pattern.length * ClassicChargingTimeline.SURROUND_SCALE / 2));
                    used.add(type);
                    renderPaths(buffers.getBuffer(type), matrix, position, camera, direction, u, v,
                            ClassicChargingTimeline.SURROUND_SCALE, Double.POSITIVE_INFINITY, pattern.paths);
                }
            }
            // Flush only our own batches, preserving other mods' pending buffers.
            for (RenderType renderType : used) buffers.endBatch(renderType);
        } finally {
            poses.popPose();
        }
    }

    private static final class Charging {
        final Entity caster;
        final ClientLevel level;
        final long input;
        final long token;
        final long ownerEpoch;
        final boolean itemMode;
        final Random random = new Random();
        final List<Pattern> surroundPatterns;
        final List<SubArc> subArcs = new ArrayList<>();
        final double width, height;
        final ChargingLoop loop;
        Vec3 origin, previousOrigin, endpoint, previousEndpoint, surroundPosition;
        boolean beamShown = true, beamValid, surroundShown, closed;
        int beamPattern;

        Charging(Entity caster, ClientLevel level, long input, long token, long ownerEpoch, boolean itemMode) {
            this.caster = caster;
            this.level = level;
            this.input = input;
            this.token = token;
            this.ownerEpoch = ownerEpoch;
            this.itemMode = itemMode;
            surroundPatterns = itemMode ? THIN_PATTERNS : NORMAL_PATTERNS;
            width = itemMode ? caster.getBbWidth() * ClassicChargingTimeline.ITEM_SIZE_MULTIPLIER : 1;
            height = itemMode ? caster.getBbHeight() * ClassicChargingTimeline.ITEM_SIZE_MULTIPLIER : 1;
            // 1.7 local-player eye/feet offsets are replaced by modern explicit coordinates.
            origin = previousOrigin = caster.getEyePosition();
            endpoint = previousEndpoint = origin.add(ClassicRaytrace.direction(caster).scale(ClassicChargingTimeline.RANGE));
            surroundPosition = caster.position();
            loop = new ChargingLoop(this);
            generateSurround();
        }

        boolean valid(Minecraft minecraft) {
            return !closed && minecraft.level == level && caster.isAlive() && !caster.isRemoved()
                    && level.getEntity(caster.getId()) == caster;
        }

        void tick() {
            previousOrigin = origin;
            previousEndpoint = endpoint;
            updateAim();
            if (!itemMode) {
                if (random.nextDouble() < ClassicChargingTimeline.BEAM_TEX_WIGGLE)
                    beamPattern = random.nextInt(BEAM_PATTERNS.size());
                beamShown = ClassicChargingTimeline.beamShown(beamShown, random.nextDouble());
            }
            // Match SubArcHandler: remove arcs dead before this tick, regenerate only when empty.
            if (subArcs.isEmpty()) generateSurround();
            Iterator<SubArc> iterator = subArcs.iterator();
            while (iterator.hasNext()) {
                SubArc arc = iterator.next();
                if (arc.dead) iterator.remove();
                else arc.tick(random);
            }
            // No arbitrary hold timeout: lifetime is the authoritative charging context/release.
        }

        void updateAim() {
            origin = caster.getEyePosition();
            HitResult hit = ClassicRaytrace.living(caster, ClassicChargingTimeline.RANGE, ClipContext.Fluid.NONE);
            boolean supported = hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                    && ChargingEnergy.blockSupported(level, block.getBlockPos());
            surroundShown = ClassicChargingTimeline.surroundShown(supported);
            if (supported && hit instanceof BlockHitResult block) {
                // NORMAL is centered in X/Z but starts at the block's lower Y.
                surroundPosition = new Vec3(block.getBlockPos().getX() + .5, block.getBlockPos().getY(),
                        block.getBlockPos().getZ() + .5);
            }
            endpoint = hit.getType() == HitResult.Type.MISS
                    ? origin.add(ClassicRaytrace.direction(caster).scale(ClassicChargingTimeline.RANGE))
                    : hit instanceof EntityHitResult entityHit
                    ? hit.getLocation().add(0, entityHit.getEntity().getEyeHeight(), 0) : hit.getLocation();
            beamValid = finite(origin) && finite(endpoint);
            if (!beamValid) surroundShown = false;
            // Item surround deliberately still requires supported aim; its render position follows
            // the caster, matching EntityPos.tick overwriting the preceding block updatePos.
        }

        void generateSurround() {
            int count = itemMode ? ClassicChargingTimeline.THIN_COUNT : ClassicChargingTimeline.NORMAL_COUNT;
            for (int i = 0; i < count; i++)
                subArcs.add(new SubArc(cubePoint(random, width, height), random, surroundPatterns.size()));
        }

        void close(Minecraft minecraft) {
            if (closed) return;
            closed = true;
            subArcs.clear();
            loop.finish();
            minecraft.getSoundManager().stop(loop);
        }
    }

    /** Source FollowEntitySound: looping, volume .3, positional and caster-following. */
    private static final class ChargingLoop extends AbstractTickableSoundInstance {
        private final Charging charging;

        ChargingLoop(Charging charging) {
            super(LOOP_SOUND, SoundSource.MASTER, RandomSource.create());
            this.charging = charging;
            volume = ClassicChargingTimeline.LOOP_VOLUME;
            looping = true;
            delay = 0;
            updatePosition();
        }

        @Override public void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            if (!charging.valid(minecraft) || CONTEXTS.get(charging.caster.getId()) != charging) {
                stop();
                return;
            }
            updatePosition();
        }

        private void updatePosition() {
            x = charging.caster.getX(); y = charging.caster.getY(); z = charging.caster.getZ();
        }

        void finish() { stop(); }
    }

    private record Pattern(double length, List<List<ClassicArcGeometry.Segment>> paths) {}

    private static final class SubArc {
        final Vec3 position, direction, u, v;
        final int templates;
        int pattern, age;
        boolean shown, dead;

        SubArc(Vec3 position, Random random, int templates) {
            this.position = position;
            this.templates = templates;
            pattern = random.nextInt(templates);
            float x = (float) (random.nextDouble() * Math.PI * 2);
            float y = (float) (random.nextDouble() * Math.PI * 2);
            float z = (float) (random.nextDouble() * Math.PI * 2);
            // Source GL applies Rz * Ry * Rx; Vec3's X/Z rotation signs are inverted.
            direction = new Vec3(1, 0, 0).xRot(-x).yRot(y).zRot(-z);
            u = new Vec3(0, 1, 0).xRot(-x).yRot(y).zRot(-z);
            v = new Vec3(0, 0, 1).xRot(-x).yRot(y).zRot(-z);
        }

        void tick(Random random) {
            if (random.nextDouble() < .5 * ClassicChargingTimeline.SUBARC_FRAME_RATE)
                pattern = random.nextInt(templates);
            age = ClassicChargingTimeline.subArcAge(age, random.nextDouble());
            if (age == ClassicChargingTimeline.SUBARC_LIFE) dead = true;
            shown = ClassicChargingTimeline.subArcShown(shown, random.nextDouble());
        }
    }

    private static List<Pattern> beamPatterns() {
        Random random = new Random();
        List<Pattern> result = new ArrayList<>();
        for (int i = 0; i < ClassicChargingTimeline.BEAM_TEMPLATES; i++)
            result.add(new Pattern(ClassicChargingTimeline.BEAM_LENGTH,
                    ClassicArcGeometry.generate(random, ClassicChargingTimeline.BEAM_LENGTH,
                            ClassicChargingTimeline.BEAM_PASSES, ClassicChargingTimeline.BEAM_WIDTH,
                            ClassicChargingTimeline.BEAM_MAX_OFFSET, ClassicChargingTimeline.BEAM_BRANCH,
                            ClassicChargingTimeline.BEAM_WIDTH_SHRINK)));
        return result;
    }

    private static List<Pattern> surroundPatterns(boolean thin) {
        Random random = new Random();
        List<Pattern> result = new ArrayList<>();
        for (int i = 0; i < ClassicChargingTimeline.SURROUND_TEMPLATES; i++) {
            double length = thin ? 1.5 + random.nextDouble() * .5 : 3 + random.nextDouble();
            result.add(new Pattern(length, ClassicArcGeometry.generate(random, length,
                    ClassicChargingTimeline.SURROUND_PASSES, thin ? .2 : .3,
                    ClassicChargingTimeline.SURROUND_OFFSET, ClassicChargingTimeline.SURROUND_BRANCH,
                    ClassicChargingTimeline.SURROUND_WIDTH_SHRINK)));
        }
        return result;
    }

    /** CubePointFactory chooses each face equally, centers only X/Z, and leaves Y in [0,height]. */
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

    private static void renderPaths(VertexConsumer out, Matrix4f matrix, Vec3 origin, Vec3 camera, Vec3 direction,
                                    Vec3 u, Vec3 v, double scale, double length,
                                    List<List<ClassicArcGeometry.Segment>> paths) {
        // EntityArc/EntitySurroundArc translate render-relative doubles before local meshes.
        origin = origin.subtract(camera);
        for (List<ClassicArcGeometry.Segment> path : paths) {
            Vec3 previousUp = null;
            for (ClassicArcGeometry.Segment segment : path) {
                // Source draw(length) retains a whole segment if its start is before the endpoint.
                if (segment.start().position().x > length) break;
                Vec3 start = arcPoint(origin, direction, u, v, segment.start().position().scale(scale));
                Vec3 end = arcPoint(origin, direction, u, v, segment.end().position().scale(scale));
                Vec3 up = end.subtract(start).cross(v).normalize();
                if (up.lengthSqr() < 1.0E-12) up = u;
                if (previousUp == null) previousUp = up;
                int alpha = (int) Math.round(255 * segment.alpha());
                vertex(out, matrix, start.add(previousUp.scale(segment.start().width() * scale)), 0, 0, alpha);
                vertex(out, matrix, start.subtract(previousUp.scale(segment.start().width() * scale)), 0, 1, alpha);
                vertex(out, matrix, end.subtract(up.scale(segment.end().width() * scale)), 1, 1, alpha);
                vertex(out, matrix, end.add(up.scale(segment.end().width() * scale)), 1, 0, alpha);
                previousUp = up;
            }
        }
    }

    private static Vec3 arcPoint(Vec3 origin, Vec3 direction, Vec3 u, Vec3 v, Vec3 point) {
        return origin.add(direction.scale(point.x)).add(u.scale(point.y)).add(v.scale(point.z));
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 point, float u, float v, int alpha) {
        out.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .setUv(u, v).setColor(255, 255, 255, Mth.clamp(alpha, 0, 255));
    }

    private static Vec3 interpolatedPosition(Entity entity, float partial) {
        return new Vec3(Mth.lerp(partial, entity.xo, entity.getX()), Mth.lerp(partial, entity.yo, entity.getY()),
                Mth.lerp(partial, entity.zo, entity.getZ()));
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
