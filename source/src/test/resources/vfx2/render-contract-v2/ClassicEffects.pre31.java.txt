/*
 * AcademyCraft NeoForge visual adapter.
 * Beam shape, colors, textures and timing adapted from AcademyCraft 1.0.7:
 * Copyright (c) Lambda Innovation, 2013-2016.
 * https://github.com/LambdaInnovation/AcademyCraft
 * See docs/UPSTREAM-README-1.0.7.md for the original license and notices.
 */
package cn.academy.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Client-only railgun/charge renderer. Call from a client packet handler, never
 * reference this class in dedicated-server/common initialization code.
 * Public entry points marshal onto the client thread and discard stale-world calls.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClassicEffects {
    private static final ResourceLocation WHITE = texture("port/white");
    private static final ResourceLocation BLEND_IN = texture("effects/railgun/blend_in");
    private static final ResourceLocation TILE = texture("effects/railgun/tile");
    private static final ResourceLocation BLEND_OUT = texture("effects/railgun/blend_out");
    private static final ResourceLocation ARC_SEGMENT = texture("effects/arc/line_segment");
    private static final ResourceLocation[] CHARGE = new ResourceLocation[40];
    private static final List<Beam> BEAMS = new ArrayList<>();
    private static final List<WeakArc> ARCS = new ArrayList<>();
    private static final Map<Integer, Long> CHARGES = new HashMap<>();
    private static ClientLevel activeLevel;
    private static long clockMillis;
    private static long lastWallMillis = Util.getMillis();
    private static MultiBufferSource.BufferSource worldBuffers;

    static {
        for (int i = 0; i < CHARGE.length; i++) CHARGE[i] = texture("effects/arc_burst/" + i);
    }

    private ClassicEffects() {}

    /** Visual-only: no damage, block breaking, or sound playback is performed here. */
    public static void addRailgun(Vec3 origin, Vec3 direction, double length) {
        if (origin == null || direction == null || !finite(origin) || !finite(direction)
                || !Double.isFinite(length) || length <= 0 || direction.lengthSqr() < 1.0E-12) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel targetLevel = minecraft.level;
        minecraft.execute(() -> {
            if (targetLevel == null || minecraft.level != targetLevel) return;
            synchronizeWorld(minecraft);
            updateClock(minecraft);
            if (BEAMS.size() >= 64) BEAMS.removeFirst();
            BEAMS.add(new Beam(origin, direction.normalize(), Math.min(length, 256), clockMillis));
        });
    }

    /** Original Weak Arc parameters; ten client ticks of flickering lightning. */
    public static void addArc(Vec3 origin, Vec3 direction, double length) {
        if (origin == null || direction == null || !finite(origin) || !finite(direction)
                || !Double.isFinite(length) || length <= 0 || direction.lengthSqr() < 1.0E-12) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel targetLevel = minecraft.level;
        minecraft.execute(() -> {
            if (targetLevel == null || minecraft.level != targetLevel) return;
            synchronizeWorld(minecraft);
            if (ARCS.size() >= 64) ARCS.removeFirst();
            ARCS.add(new WeakArc(origin, direction.normalize(), Math.min(length, 20)));
        });
    }

    /** Restart the original non-looping 40 x 40ms animation for this entity. */
    public static void addCharge(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel targetLevel = minecraft.level;
        minecraft.execute(() -> {
            if (targetLevel == null || minecraft.level != targetLevel) return;
            synchronizeWorld(minecraft);
            updateClock(minecraft);
            if (CHARGES.size() < 128 || CHARGES.containsKey(entityId)) CHARGES.put(entityId, clockMillis);
        });
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        synchronizeWorld(minecraft);
        updateClock(minecraft);
        BEAMS.removeIf(beam -> clockMillis - beam.created >= ClassicEffectTimeline.RAILGUN_LIFETIME_MS);
        if (!minecraft.isPaused()) {
            for (Beam beam : BEAMS) beam.tickSparks();
            ARCS.removeIf(arc -> !arc.tick());
        }
        CHARGES.entrySet().removeIf(entry -> ClassicEffectTimeline.chargeFrame(clockMillis - entry.getValue()) < 0
                || minecraft.level == null || minecraft.level.getEntity(entry.getKey()) == null);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        synchronizeWorld(minecraft);
        updateClock(minecraft);
        if (minecraft.level == null || (BEAMS.isEmpty() && ARCS.isEmpty() && CHARGES.isEmpty())) return;
        PoseStack poses = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        if (worldBuffers == null) worldBuffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        MultiBufferSource.BufferSource buffers = worldBuffers;
        Set<RenderType> used = new HashSet<>();
        poses.pushPose();
        try {
            // AFTER_PARTICLES supplies the entity pose stack. Subtract camera
            // in double precision before any float vertex/matrix operation.
            // Global float coordinates erase classic thin arcs far from spawn.
            Matrix4f matrix = poses.last().pose();
            for (Beam beam : BEAMS) renderBeam(beam, camera, matrix, buffers, used);
            for (WeakArc arc : ARCS) {
                if (arc.show) renderArc(arc, camera, matrix, worldBuffer(buffers, used, ARC_SEGMENT));
            }
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            for (Map.Entry<Integer, Long> charge : CHARGES.entrySet()) {
                Entity entity = minecraft.level.getEntity(charge.getKey());
                if (entity == null || (entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson())) continue;
                int frame = ClassicEffectTimeline.chargeFrame(clockMillis - charge.getValue());
                if (frame < 0) continue;
                // World-space hand vicinity adapter; exact classic render-hook
                // arm attachment awaits player-model/screenshot comparison.
                Vec3 center = entity.getEyePosition(partialTick)
                        .add(entity.getViewVector(partialTick).scale(0.7)).add(0, -0.2, 0);
                poses.pushPose();
                Vec3 relative = center.subtract(camera);
                poses.translate(relative.x, relative.y, relative.z);
                poses.mulPose(event.getCamera().rotation());
                VertexConsumer consumer = worldBuffer(buffers, used, CHARGE[frame]);
                billboard(consumer, poses.last().pose(), 0.4, 255);
                poses.popPose();
            }
            // Flush only our own render types, never another mod's pending batch.
            for (RenderType type : used) buffers.endBatch(type);
        } finally {
            poses.popPose();
        }
    }

    private static void renderArc(WeakArc arc, Vec3 camera, Matrix4f matrix, VertexConsumer out) {
        Vec3 u = perpendicular(arc.direction);
        Vec3 v = arc.direction.cross(u).normalize();
        renderArcPaths(arc.origin.subtract(camera), arc.direction, u, v, 1, arc.length,
                WeakArc.PATTERNS.get(arc.pattern), matrix, out, false);
    }

    private static void renderArcPaths(Vec3 origin, Vec3 direction, Vec3 u, Vec3 v, double scale,
                                       double length, List<List<ClassicArcGeometry.Segment>> paths,
                                       Matrix4f matrix, VertexConsumer out, boolean fixedDisplayList) {
        // Original draw() uses baked GL vertices; draw(length) regenerates ribbon
        // normals on each visible frame. Local geometry transforms stay double.
        var quads = fixedDisplayList ? ClassicArcGeometry.baked(paths) : ClassicArcGeometry.ribbons(paths, length);
        for (ClassicArcGeometry.Quad quad : quads) {
            int alpha = (int) Math.round(255 * quad.alpha());
            vertex(out, matrix, arcPoint(origin, direction, u, v, quad.p1().scale(scale)), 0, 0, 255, 255, 255, alpha);
            vertex(out, matrix, arcPoint(origin, direction, u, v, quad.p2().scale(scale)), 0, 1, 255, 255, 255, alpha);
            vertex(out, matrix, arcPoint(origin, direction, u, v, quad.p4().scale(scale)), 1, 1, 255, 255, 255, alpha);
            vertex(out, matrix, arcPoint(origin, direction, u, v, quad.p3().scale(scale)), 1, 0, 255, 255, 255, alpha);
        }
    }

    private static Vec3 arcPoint(Vec3 origin, Vec3 direction, Vec3 u, Vec3 v, Vec3 point) {
        return origin.add(direction.scale(point.x)).add(u.scale(point.y)).add(v.scale(point.z));
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        synchronizeWorld(minecraft);
        updateClock(minecraft);
        if (event.getHand() != InteractionHand.MAIN_HAND || minecraft.player == null) return;
        Long created = CHARGES.get(minecraft.player.getId());
        if (created == null) return;
        int frame = ClassicEffectTimeline.chargeFrame(clockMillis - created);
        if (frame < 0) return;
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        try {
            int sign = minecraft.player.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
            poses.translate(sign * 0.26, -0.12, -0.24);
            poses.scale(0.4F, 0.4F, 1.0F);
            RenderType type = ClassicRenderTypes.hand(CHARGE[frame]);
            billboard(event.getMultiBufferSource().getBuffer(type), poses.last().pose(), 1, 255);
            if (event.getMultiBufferSource() instanceof MultiBufferSource.BufferSource buffers) buffers.endBatch(type);
        } finally {
            poses.popPose();
        }
        // Do not cancel the event: vanilla hands and the held item still render.
    }

    private static void renderBeam(Beam beam, Vec3 camera, Matrix4f matrix,
                                   MultiBufferSource buffers, Set<RenderType> used) {
        long age = clockMillis - beam.created;
        if (age >= ClassicEffectTimeline.RAILGUN_LIFETIME_MS) return;
        beam.wiggle(clockMillis);
        double alpha = ClassicEffectTimeline.beamAlpha(age);
        double widthScale = ClassicEffectTimeline.beamWidthScale(age) + beam.widthWiggle;
        double length = beam.length * ClassicEffectTimeline.beamLengthScale(age);
        if (length < 0.001 || widthScale <= 0 || alpha <= 0) return;
        if (!beam.sparks.isEmpty()) {
            VertexConsumer sparkBuffer = worldBuffer(buffers, used, ARC_SEGMENT);
            for (BeamSpark spark : beam.sparks) {
                if (!spark.draw) continue;
                RailgunPattern pattern = BeamSpark.PATTERNS.get(spark.pattern);
                Vec3 centered = spark.position.subtract(camera).subtract(spark.direction.scale(pattern.length * 0.15));
                renderArcPaths(centered, spark.direction, spark.u, spark.v, 0.3,
                        Double.POSITIVE_INFINITY, pattern.paths, matrix, sparkBuffer, true);
            }
        }
        Vec3 origin = beam.origin.subtract(camera);
        Vec3 up = origin.cross(beam.direction);
        if (up.lengthSqr() < 1.0E-8) up = perpendicular(beam.direction);
        up = up.normalize();
        Vec3 start = origin.add(beam.direction.scale(-0.3));
        Vec3 end = origin.add(beam.direction.scale(length + 0.3));
        // Classic uses a fixed 1.1-block cap length even when the width shrinks.
        Vec3 middleStart = start.add(beam.direction.scale(1.1));
        Vec3 middleEnd = end.add(beam.direction.scale(-1.1));
        int glowAlpha = (int) Math.round(255 * alpha * alpha * (0.9 + beam.glowWiggle));
        board(worldBuffer(buffers, used, BLEND_IN), matrix, start, middleStart, up, 1.1 * widthScale, glowAlpha);
        board(worldBuffer(buffers, used, TILE), matrix, middleStart, middleEnd, up, 1.1 * widthScale, glowAlpha);
        board(worldBuffer(buffers, used, BLEND_OUT), matrix, middleEnd, end, up, 1.1 * widthScale, glowAlpha);
        VertexConsumer core = worldBuffer(buffers, used, WHITE);
        cylinder(core, matrix, beam, origin, length, 0.09 * widthScale, 0.98, 241, 240, 222, (int) Math.round(200 * alpha));
        cylinder(core, matrix, beam, origin, length, 0.13 * widthScale, 1.0, 236, 170, 93, (int) Math.round(60 * alpha));
    }

    /** Reconstruct the original 12-sided cylinder and four sqrt-profile end bands. */
    private static void cylinder(VertexConsumer out, Matrix4f matrix, Beam beam, Vec3 origin, double length,
                                 double radius, double headFix, int r, int g, int b, int a) {
        Vec3 axisU = perpendicular(beam.direction);
        Vec3 axisV = beam.direction.cross(axisU).normalize();
        double offset = radius * (1 - headFix);
        for (int side = 0; side < 12; side++) {
            double angle0 = side * Math.PI * 2 / 12;
            double angle1 = (side + 1) * Math.PI * 2 / 12;
            ringQuad(out, matrix, beam, origin, axisU, axisV, radius, radius, radius, length,
                    angle0, angle1, r, g, b, a);
            for (int band = 0; band < 4; band++) {
                double t0 = band / 4.0, t1 = (band + 1) / 4.0;
                double radius0 = radius * Math.sqrt(t0), radius1 = radius * Math.sqrt(t1);
                ringQuad(out, matrix, beam, origin, axisU, axisV, radius0, radius1,
                        offset + radius * headFix * t0, offset + radius * headFix * t1,
                        angle0, angle1, r, g, b, a);
                ringQuad(out, matrix, beam, origin, axisU, axisV, radius0, radius1,
                        length + radius - offset - radius * headFix * t0,
                        length + radius - offset - radius * headFix * t1,
                        angle0, angle1, r, g, b, a);
            }
        }
    }

    private static void ringQuad(VertexConsumer out, Matrix4f matrix, Beam beam, Vec3 origin, Vec3 u, Vec3 v,
                                 double radius0, double radius1, double x0, double x1,
                                 double angle0, double angle1, int r, int g, int b, int a) {
        vertex(out, matrix, ring(beam, origin, u, v, x0, radius0, angle0), 0, 0, r, g, b, a);
        vertex(out, matrix, ring(beam, origin, u, v, x1, radius1, angle0), 1, 0, r, g, b, a);
        vertex(out, matrix, ring(beam, origin, u, v, x1, radius1, angle1), 1, 1, r, g, b, a);
        vertex(out, matrix, ring(beam, origin, u, v, x0, radius0, angle1), 0, 1, r, g, b, a);
    }

    private static Vec3 ring(Beam beam, Vec3 origin, Vec3 u, Vec3 v, double x, double radius, double angle) {
        return origin.add(beam.direction.scale(x)).add(u.scale(radius * Math.sin(angle))).add(v.scale(radius * Math.cos(angle)));
    }

    private static void board(VertexConsumer out, Matrix4f matrix, Vec3 start, Vec3 end, Vec3 up,
                              double width, int alpha) {
        Vec3 offset = up.scale(width / 2);
        vertex(out, matrix, start.add(offset), 0, 1, 255, 255, 255, alpha);
        vertex(out, matrix, start.subtract(offset), 0, 0, 255, 255, 255, alpha);
        vertex(out, matrix, end.subtract(offset), 1, 0, 255, 255, 255, alpha);
        vertex(out, matrix, end.add(offset), 1, 1, 255, 255, 255, alpha);
    }

    private static void billboard(VertexConsumer out, Matrix4f matrix, double halfWidth, int alpha) {
        vertex(out, matrix, new Vec3(-halfWidth, -halfWidth, 0), 0, 1, 255, 255, 255, alpha);
        vertex(out, matrix, new Vec3(halfWidth, -halfWidth, 0), 1, 1, 255, 255, 255, alpha);
        vertex(out, matrix, new Vec3(halfWidth, halfWidth, 0), 1, 0, 255, 255, 255, alpha);
        vertex(out, matrix, new Vec3(-halfWidth, halfWidth, 0), 0, 0, 255, 255, 255, alpha);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 point, float u, float v,
                               int r, int g, int b, int a) {
        out.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .setUv(u, v).setColor(r, g, b, Math.max(0, Math.min(255, a)));
    }

    private static VertexConsumer worldBuffer(MultiBufferSource buffers, Set<RenderType> used, ResourceLocation texture) {
        RenderType type = ClassicRenderTypes.world(texture);
        used.add(type);
        return buffers.getBuffer(type);
    }

    private static Vec3 perpendicular(Vec3 direction) {
        return direction.cross(Math.abs(direction.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png");
    }

    private static void synchronizeWorld(Minecraft minecraft) {
        if (activeLevel != minecraft.level) {
            activeLevel = minecraft.level;
            BEAMS.clear();
            ARCS.clear();
            CHARGES.clear();
            clockMillis = 0;
            lastWallMillis = Util.getMillis();
        }
    }

    private static void updateClock(Minecraft minecraft) {
        long now = Util.getMillis();
        if (!minecraft.isPaused()) clockMillis += Math.max(0, now - lastWallMillis);
        lastWallMillis = now;
    }

    private static final class Beam {
        final Vec3 origin, direction;
        final double length;
        final long created;
        final Random random = new Random();
        long lastFrame;
        double widthWiggle, glowWiggle;
        final List<BeamSpark> sparks = new ArrayList<>();
        int ticks;

        Beam(Vec3 origin, Vec3 direction, double length, long created) {
            this.origin = origin;
            this.direction = direction;
            this.length = length;
            this.created = created;
            lastFrame = created;
            Vec3 u = perpendicular(direction), v = direction.cross(u).normalize();
            for (double x = 1; x <= length; x += 1 + random.nextDouble()) {
                double theta = random.nextDouble() * Math.PI * 2;
                double radius = 0.1 + random.nextDouble() * 0.15;
                Vec3 position = origin.add(direction.scale(x))
                        .add(u.scale(radius * Math.sin(theta))).add(v.scale(radius * Math.cos(theta)));
                sparks.add(new BeamSpark(position, random));
            }
        }

        void tickSparks() {
            if (++ticks >= 30) sparks.clear();
            else for (BeamSpark spark : sparks) spark.tick(random);
        }

        void wiggle(long now) {
            double dt = Math.max(0, now - lastFrame) / 1000.0;
            widthWiggle = Math.max(0, Math.min(0.3, widthWiggle + dt * (random.nextDouble() * 1.6 - 0.8)));
            glowWiggle = Math.max(0, Math.min(0.1, glowWiggle + dt * (random.nextDouble() * 0.8 - 0.4)));
            lastFrame = now;
        }
    }

    private static final class WeakArc {
        static final List<List<List<ClassicArcGeometry.Segment>>> PATTERNS = makePatterns();
        final Vec3 origin, direction;
        final double length;
        final Random random = new Random();
        int ticks, pattern;
        boolean show = true;

        WeakArc(Vec3 origin, Vec3 direction, double length) {
            this.origin = origin;
            this.direction = direction;
            this.length = length;
        }

        private static List<List<List<ClassicArcGeometry.Segment>>> makePatterns() {
            Random random = new Random();
            List<List<List<ClassicArcGeometry.Segment>>> patterns = new ArrayList<>();
            for (int i = 0; i < 20; i++) patterns.add(ClassicArcGeometry.generate(random, 20, 6, 0.1, 1.1, 0.15, 0.7));
            return patterns;
        }

        boolean tick() {
            if (++ticks >= 10) return false;
            if (random.nextDouble() < 0.7) pattern = random.nextInt(PATTERNS.size());
            if (random.nextDouble() < (show ? 0.1 : 0.4)) show = !show;
            return true;
        }
    }

    private record RailgunPattern(double length, List<List<ClassicArcGeometry.Segment>> paths) {}

    private static final class BeamSpark {
        static final List<RailgunPattern> PATTERNS = makePatterns();
        final Vec3 position, direction, u, v;
        int pattern;
        boolean draw;

        BeamSpark(Vec3 position, Random random) {
            this.position = position;
            pattern = random.nextInt(PATTERNS.size());
            float x = (float) (random.nextDouble() * Math.PI * 2);
            float y = (float) (random.nextDouble() * Math.PI * 2);
            float z = (float) (random.nextDouble() * Math.PI * 2);
            direction = new Vec3(1, 0, 0).xRot(x).yRot(y).zRot(z);
            u = new Vec3(0, 1, 0).xRot(x).yRot(y).zRot(z);
            v = new Vec3(0, 0, 1).xRot(x).yRot(y).zRot(z);
        }

        private static List<RailgunPattern> makePatterns() {
            Random random = new Random();
            List<RailgunPattern> patterns = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                double length = 2 + random.nextDouble();
                patterns.add(new RailgunPattern(length,
                        ClassicArcGeometry.generate(random, length, 3, 0.3, 0.8, 0.7, 0.9)));
            }
            return patterns;
        }

        void tick(Random random) {
            if (random.nextDouble() < 0.5) pattern = random.nextInt(PATTERNS.size());
            if (random.nextDouble() < (draw ? 0.4 : 0.3)) draw = !draw;
        }
    }
}
