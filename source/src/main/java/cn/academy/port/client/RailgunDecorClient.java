package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.vfx2.RailgunVfx2;
import cn.academy.port.core.RangedSkillRanges;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Optional railgun presentation paired with the existing confirmed charge and beam packets. */
final class RailgunDecorClient {
    private static final boolean QA_BUDGET = Boolean.getBoolean("academy.qa.signatureBudget");
    private static final int MAX_SHOTS = 64, MAX_CHARGES = 16, MAX_VISIBLE = 8;
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static final Map<Integer, Charge> CHARGES = new LinkedHashMap<>();
    private static ClientLevel level;
    private static Object connection;
    private static long session;

    private record Shot(Vec3 origin, Vec3 axis, double length, Point face, int caster, long started) {}
    private record Charge(Entity caster, long started) {}

    private RailgunDecorClient() {}

    static boolean sameDimension(ClientLevel world, CompoundTag tag) {
        return !tag.contains("shot_dimension") || world != null && tag.contains("shot_dimension", Tag.TAG_STRING)
                && world.dimension().location().toString().equals(tag.getString("shot_dimension"));
    }

    static void charge(int casterId) {
        var mc = Minecraft.getInstance();
        synchronize(mc);
        if (level == null || !level.isClientSide() || !mc.isSameThread()) return;
        var caster = level.getEntity(casterId);
        if (caster != null && (CHARGES.size() < MAX_CHARGES || CHARGES.containsKey(casterId)))
            CHARGES.put(casterId, new Charge(caster, level.getGameTime()));
    }

    static void shot(CompoundTag tag, Vec3 origin, Vec3 direction) {
        var mc = Minecraft.getInstance();
        synchronize(mc);
        double length = tag.getDouble("length");
        if (level == null || !mc.isSameThread() || !sameDimension(level, tag) || !finite(origin)
                || !finite(direction) || direction.lengthSqr() < 1e-12 || !Double.isFinite(length)
                || length <= 0 || length > RangedSkillRanges.fixed("railgun")) return;
        Vec3 axis = direction.normalize();
        if (!RailThunderArrayGeometry.validEndpoints(point(origin), point(origin.add(axis.scale(length))))) return;
        Point face = null;
        if (tag.contains("shot_dimension", Tag.TAG_STRING) && tag.contains("block_contact", Tag.TAG_DOUBLE)
                && tag.contains("block_face", Tag.TAG_INT)) {
            double contact = tag.getDouble("block_contact");
            int faceId = tag.getInt("block_face");
            if (Double.isFinite(contact) && contact > 0 && contact <= length && faceId >= 0 && faceId < Direction.values().length) {
                var normal = Direction.from3DDataValue(faceId).getNormal();
                var candidate = new Point(normal.getX(), normal.getY(), normal.getZ());
                if (candidate.dot(point(axis)) < 0) {
                    length = contact;
                    face = candidate;
                }
            }
        }
        int caster = tag.contains("entity", Tag.TAG_INT) ? tag.getInt("entity") : -1;
        CHARGES.remove(caster);
        var shot = new Shot(origin, axis, length, face, caster, level.getGameTime());
        if (SHOTS.contains(shot)) return;
        if (SHOTS.size() >= MAX_SHOTS) SHOTS.removeFirst();
        SHOTS.add(shot);
        if (QA_BUDGET) com.mojang.logging.LogUtils.getLogger().info(
                "QA_A2_RAILGUN caster={} origin={} axis={} beamLength={} decorLength={} blockFace={}",
                caster, origin, axis, tag.getDouble("length"), length, face);
    }

    static void render(RenderLevelStageEvent event, VisualTier tier) {
        var mc = Minecraft.getInstance();
        synchronize(mc);
        if (level == null) return;
        double partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + partial;
        SHOTS.removeIf(shot -> now - shot.started >= RailgunDecor.SHOT_TICKS);
        CHARGES.values().removeIf(charge -> now - charge.started >= RailgunDecor.CHARGE_TICKS
                || level.getEntity(charge.caster.getId()) != charge.caster);
        var camera = event.getCamera().getPosition();
        var parts = new FxLib.Builder();
        SHOTS.stream().filter(shot -> distance(shot, camera) <= 64 * 64)
                .filter(shot -> event.getFrustum().isVisible(new AABB(shot.origin, shot.origin.add(shot.axis.scale(shot.length))).inflate(1)))
                .sorted(Comparator.comparingDouble(shot -> distance(shot, camera))).limit(MAX_VISIBLE)
                .forEach(shot -> parts.add(RailgunDecor.shot(point(shot.origin), point(shot.axis), shot.length, shot.face,
                        now - shot.started, shot.started ^ shot.caster, tier)));
        for (var charge : CHARGES.values()) {
            Vec3 eye = charge.caster.getEyePosition((float) partial);
            if (eye.distanceToSqr(camera) > 32 * 32) continue;
            parts.add(RailgunDecor.charge(point(eye), point(charge.caster.getViewVector((float) partial)),
                    now - charge.started, tier));
        }
        VisualKit.render(event, parts.build(), tier, RailgunDecor.MAX_QUADS);
    }

    private static double distance(Shot shot, Vec3 camera) {
        return RailgunVfx2.distanceSquared(shot.origin, shot.axis, shot.length, camera);
    }

    private static void synchronize(Minecraft mc) {
        long epoch = AcademyClient.inputSessionEpoch();
        if (level == mc.level && connection == mc.getConnection() && session == epoch) return;
        level = mc.level;
        connection = mc.getConnection();
        session = epoch;
        SHOTS.clear();
        CHARGES.clear();
    }

    private static Point point(Vec3 point) { return new Point(point.x, point.y, point.z); }
    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
