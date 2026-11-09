/* AcademyCraft 1.0.7 ThunderBolt/EntityArc visual adapter. GPLv3; see NOTICE. */
package cn.academy.port.client;

import cn.academy.port.skill.ClassicRaytrace;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Real client-only strong/endpoint arcs, not gameplay entities or projectiles. The modern CPU
 * geometry and render buffers replace the source GL display lists; pixel parity is unverified.
 * Forward only thunder_bolt packets here from the client packet switch.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClassicThunderBoltEffects {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "academy", "textures/effects/arc/line_segment.png");
    private static final SoundEvent SOUND = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath("academy", "em.arc_strong"));
    private static final List<List<List<ClassicArcGeometry.Segment>>> STRONG_PATTERNS = patterns(true);
    private static final List<List<List<ClassicArcGeometry.Segment>>> AOE_PATTERNS = patterns(false);
    private static final List<Arc> ARCS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static final int MAX_ARCS = 1024, MAX_PACKET_AOES = 256;
    private static ClientLevel activeLevel;
    private static Entity activePlayer;
    private static Object activeConnection;
    private static MultiBufferSource.BufferSource buffers;

    private ClassicThunderBoltEffects() {}

    /** Defensive copy and session capture prevent queued effects crossing a world/connection. */
    public static void receive(CompoundTag data) {
        if (data == null || !"thunder_bolt".equals(data.getString("kind"))
                || !data.contains("entity", Tag.TAG_INT)
                || !hasVector(data, "x", "y", "z") || !hasVector(data, "dx", "dy", "dz")
                || !hasVector(data, "point_x", "point_y", "point_z")) return;
        CompoundTag tag = data.copy();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Entity localPlayer = minecraft.player;
        Object connection = minecraft.getConnection();
        minecraft.execute(() -> {
            if (level == null || minecraft.level != level || minecraft.player != localPlayer
                    || minecraft.getConnection() != connection) return;
            synchronizeSession(minecraft);
            Entity caster = level.getEntity(tag.getInt("entity"));
            Vec3 origin = caster == null ? vector(tag, "x", "y", "z") : caster.getEyePosition();
            // The original client spawns main arcs from its current head-facing pose at receipt.
            Vec3 direction = caster == null ? vector(tag, "dx", "dy", "dz") : ClassicRaytrace.direction(caster);
            Vec3 center = vector(tag, "point_x", "point_y", "point_z");
            if (!finite(origin) || !finite(direction) || direction.lengthSqr() < 1.0E-12 || !finite(center)) return;
            for (int i = 0; i < ClassicThunderBoltTimeline.MAIN_ARCS; i++)
                add(new Arc(origin, direction, Double.POSITIVE_INFINITY,
                        ClassicThunderBoltTimeline.MAIN_LIFE_TICKS, true, tag.getInt("entity")));

            var aoes = tag.getList("aoes", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(MAX_PACKET_AOES, aoes.size()); i++) {
                var target = aoes.getCompound(i);
                if (!hasVector(target, "x", "y", "z")) continue;
                Entity entity = target.contains("entity", Tag.TAG_INT) ? level.getEntity(target.getInt("entity")) : null;
                Vec3 end = entity == null ? vector(target, "x", "y", "z")
                        : entity.position().add(0, entity.getEyeHeight(), 0);
                Vec3 delta = end.subtract(center);
                double length = delta.length();
                if (!finite(end) || !Double.isFinite(length) || length < 1.0E-6 || length > 64) continue;
                // Neither origin nor endpoint follows entities after spawn, just like EntityArc.
                add(new Arc(center, delta, length,
                        ClassicThunderBoltTimeline.aoeLife(RANDOM.nextInt(10)), false, tag.getInt("entity")));
            }
            if (caster != null) minecraft.getSoundManager().play(new FollowingSound(caster));
            else level.playLocalSound(origin.x, origin.y, origin.z, SOUND, SoundSource.MASTER,
                    ClassicThunderBoltTimeline.SOUND_VOLUME, 1, false);
        });
    }

    private static void add(Arc arc) {
        if (ARCS.size() >= MAX_ARCS) ARCS.removeFirst();
        ARCS.add(arc);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        synchronizeSession(minecraft);
        if (minecraft.level == null || minecraft.isPaused()) return;
        ARCS.removeIf(arc -> !arc.tick());
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        synchronizeSession(minecraft);
        if (minecraft.level == null || ARCS.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        RenderType type = ClassicRenderTypes.world(TEXTURE);
        PoseStack poses = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poses.pushPose();
        try {
            VertexConsumer out = buffers.getBuffer(type);
            for (Arc arc : ARCS) if (arc.show) render(arc, camera, poses.last().pose(), out);
            // Private buffers only: do not flush vanilla or another mod's queued geometry.
            buffers.endBatch(type);
        } finally {
            poses.popPose();
        }
    }

    private static void render(Arc arc, Vec3 camera, Matrix4f matrix, VertexConsumer out) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean firstPerson = minecraft.player != null && arc.caster == minecraft.player.getId()
                && minecraft.options.getCameraType().isFirstPerson();
        var offset = ClassicThunderBoltTimeline.viewOffset(firstPerson);
        // ViewOptimize checks the current view on every draw, including AOE endpoint arcs.
        // Original EntityArc translates render-relative coordinates before local geometry.
        Vec3 origin = arc.origin.subtract(camera).add(arc.direction.scale(offset.x()))
                .add(arc.u.scale(offset.y())).add(arc.v.scale(offset.z()));
        for (List<ClassicArcGeometry.Segment> path : arc.patterns.get(arc.pattern)) {
            Vec3 previousUp = null;
            for (ClassicArcGeometry.Segment segment : path) {
                if (!ClassicThunderBoltTimeline.drawsSegment(segment.start().position().x, arc.clipLength)) break;
                Vec3 start = arc.point(origin, segment.start().position());
                Vec3 end = arc.point(origin, segment.end().position());
                Vec3 up = end.subtract(start).cross(arc.v).normalize();
                if (up.lengthSqr() < 1.0E-12) up = arc.u;
                if (previousUp == null) previousUp = up;
                int alpha = (int) Math.round(segment.alpha() * 255);
                vertex(out, matrix, start.add(previousUp.scale(segment.start().width())), 0, 0, alpha);
                vertex(out, matrix, start.subtract(previousUp.scale(segment.start().width())), 0, 1, alpha);
                vertex(out, matrix, end.subtract(up.scale(segment.end().width())), 1, 1, alpha);
                vertex(out, matrix, end.add(up.scale(segment.end().width())), 1, 0, alpha);
                previousUp = up;
            }
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 point, float u, float v, int alpha) {
        out.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .setUv(u, v).setColor(255, 255, 255, Math.max(0, Math.min(255, alpha)));
    }

    private static List<List<List<ClassicArcGeometry.Segment>>> patterns(boolean strong) {
        Random random = new Random();
        var patterns = new ArrayList<List<List<ClassicArcGeometry.Segment>>>();
        for (int i = 0; i < ClassicThunderBoltTimeline.TEMPLATES; i++)
            patterns.add(ClassicArcGeometry.generate(random, ClassicThunderBoltTimeline.PATTERN_LENGTH,
                    ClassicThunderBoltTimeline.PASSES,
                    strong ? ClassicThunderBoltTimeline.STRONG_WIDTH : ClassicThunderBoltTimeline.AOE_WIDTH,
                    strong ? ClassicThunderBoltTimeline.STRONG_OFFSET : ClassicThunderBoltTimeline.AOE_OFFSET,
                    strong ? ClassicThunderBoltTimeline.STRONG_BRANCH : ClassicThunderBoltTimeline.AOE_BRANCH,
                    ClassicThunderBoltTimeline.WIDTH_SHRINK));
        return List.copyOf(patterns);
    }

    public static void clear() { ARCS.clear(); }

    private static void synchronizeSession(Minecraft minecraft) {
        if (activeLevel != minecraft.level || activePlayer != minecraft.player
                || activeConnection != minecraft.getConnection()) {
            clear();
            activeLevel = minecraft.level;
            activePlayer = minecraft.player;
            activeConnection = minecraft.getConnection();
        }
    }

    private static Vec3 vector(CompoundTag tag, String x, String y, String z) {
        return new Vec3(tag.getDouble(x), tag.getDouble(y), tag.getDouble(z));
    }
    private static boolean hasVector(CompoundTag tag, String x, String y, String z) {
        return tag.contains(x, Tag.TAG_ANY_NUMERIC) && tag.contains(y, Tag.TAG_ANY_NUMERIC)
                && tag.contains(z, Tag.TAG_ANY_NUMERIC);
    }
    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static final class Arc {
        final Vec3 origin, direction, u, v;
        final double clipLength;
        final int life, caster;
        final Random random = new Random();
        final List<List<List<ClassicArcGeometry.Segment>>> patterns;
        int age, pattern; // EntityArc starts all three overlapping arcs on template zero.
        boolean show = true;

        Arc(Vec3 origin, Vec3 direction, double length, int life, boolean strong, int caster) {
            this.direction = direction.normalize();
            Vec3 localNormal = this.direction.cross(new Vec3(0, 1, 0));
            this.v = localNormal.lengthSqr() < 1.0E-12 ? new Vec3(-1, 0, 0) : localNormal.normalize();
            this.u = this.v.cross(this.direction).normalize();
            this.origin = origin;
            this.clipLength = length;
            this.life = life;
            this.caster = caster;
            this.patterns = strong ? STRONG_PATTERNS : AOE_PATTERNS;
        }

        boolean tick() {
            if (!ClassicThunderBoltTimeline.alive(++age, life)) return false;
            if (ClassicThunderBoltTimeline.replacePattern(random.nextDouble())) pattern = random.nextInt(patterns.size());
            show = ClassicThunderBoltTimeline.shown(show, random.nextDouble());
            return true;
        }
        Vec3 point(Vec3 origin, Vec3 local) {
            return origin.add(direction.scale(local.x)).add(u.scale(local.y)).add(v.scale(local.z));
        }
    }

    private static final class FollowingSound extends AbstractTickableSoundInstance {
        private final Entity caster;
        FollowingSound(Entity caster) {
            super(SOUND, SoundSource.MASTER, RandomSource.create());
            this.caster = caster;
            volume = ClassicThunderBoltTimeline.SOUND_VOLUME;
            pitch = 1;
            looping = false;
            tick();
        }
        @Override public void tick() {
            if (caster.isRemoved() || caster.level() != Minecraft.getInstance().level) { stop(); return; }
            x = caster.getX(); y = caster.getY(); z = caster.getZ();
        }
    }
}
